package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Base64
import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
import com.erfangholami.androidsolidservices.api.auth.preferredTokenEndpointAuthMethod
import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetrySpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.internal.UriUtil
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

private const val AUTH_LOG_TAG = "Authenticator"

private const val HTTP_TOO_MANY_REQUESTS = 429

internal fun interface RefreshTokenEndpoint {
    suspend fun refresh(profile: Profile): Pair<TokenResponse?, AuthorizationException?>
}

internal class TokenRefreshCoordinator(
    private val authService: AuthorizationService,
    private val profileManager: ProfileStore,
    private val now: () -> Long,
    endpoint: RefreshTokenEndpoint? = null,
    private val sessionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val dpopTokenRequester = DPoPTokenRequester()

    private val policy = RefreshPolicy(now)

    private val refreshEndpoint = endpoint ?: RefreshTokenEndpoint { requestToken(it, isRefresh = true) }

    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()

    private val inFlight = ConcurrentHashMap<String, Deferred<Profile>>()

    private fun mutexFor(webId: String) = refreshMutexes.getOrPut(webId) { Mutex() }

    fun forget(webId: String) {
        policy.forget(webId)
        inFlight.remove(webId)?.cancel()
    }

    fun forgetAll() {
        policy.forgetAll()
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }

    fun isAccessTokenHardExpired(profile: Profile): Boolean = policy.isAccessTokenHardExpired(profile)

    suspend fun <T> withSessionLock(
        webId: String,
        block: suspend () -> T,
    ): T = mutexFor(webId).withLock { block() }

    /**
     * Returns [webId]'s profile with a usable token when one can be had, refreshing at most once
     * across all concurrent callers.
     *
     * A rotation runs on the coordinator's own scope, never the caller's: a UI flow tearing down
     * mid-refresh cancels its *await*, not the rotation, so a rotated refresh token can no longer
     * be lost to a caller's lifecycle — the failure that used to let a spent token be re-sent and
     * cost the whole grant family.
     */
    suspend fun checkTokenAndRefresh(
        webId: String,
        profile: Profile,
        forceRefresh: Boolean = false,
    ): Profile {
        if (policy.shouldSkip(webId, profile, forceRefresh)) return profile
        policy.coalescedResult(webId)?.let { return it }
        val flight = inFlight.computeIfAbsent(webId) { key ->
            val deferred = sessionScope.async {
                mutexFor(key).withLock { refreshLocked(key, profile, forceRefresh) }
            }
            deferred.invokeOnCompletion { inFlight.remove(key, deferred) }
            deferred
        }
        return flight.await()
    }

    internal fun inFlightOrNull(webId: String): Deferred<Profile>? = inFlight[webId]

    private suspend fun refreshLocked(
        webId: String,
        fallback: Profile,
        forceRefresh: Boolean,
    ): Profile {
        policy.coalescedResult(webId)?.let { return it }
        val current = profileManager.getProfileOrNull(webId) ?: return fallback
        if (policy.shouldSkip(webId, current, forceRefresh)) return current
        if (current.authState.refreshToken == null) {
            return expireOnlyIfAccessTokenSpent(webId, current)
        }
        if (forceRefresh) policy.noteForced(webId)

        val span = Telemetry.startSpan("solid_auth_refresh")
        span.putAttribute("issuer", issuerHost(current))
        span.putAttribute("forced", forceRefresh.toString())
        return try {
            val (tokenResponse, exception) = refreshEndpoint.refresh(current)
            when {
                tokenResponse != null -> applyRotatedToken(webId, current, tokenResponse, span)
                policy.isTerminalRefreshError(exception) -> markSessionDead(webId, current, exception, span)
                else -> keepForRetry(current, exception, span)
            }
        } finally {
            span.stop()
        }
    }

    private suspend fun applyRotatedToken(
        webId: String,
        current: Profile,
        tokenResponse: TokenResponse,
        span: TelemetrySpan,
    ): Profile {
        val updatedAuthState = deepCopyAuthState(current.authState)
        updatedAuthState.update(tokenResponse, null)
        val updated = current.copy(authState = updatedAuthState)
        policy.noteSuccess(webId, updated)
        profileManager.writeProfile(webId, updated)
        span.putAttribute(TelemetryAttribute.OUTCOME, TelemetryAttribute.OUTCOME_SUCCESS)
        span.putAttribute(
            "rt_rotation",
            when (tokenResponse.refreshToken) {
                null -> "unrotated"
                current.authState.refreshToken -> "reissued_same"
                else -> "rotated"
            },
        )
        return updated
    }

    private suspend fun markSessionDead(
        webId: String,
        current: Profile,
        exception: AuthorizationException?,
        span: TelemetrySpan,
    ): Profile {
        Log.w(AUTH_LOG_TAG, "Token refresh failed terminally for $webId: error=${exception?.error}", exception)
        span.putAttribute(TelemetryAttribute.OUTCOME, "terminal")
        exception?.let {
            Telemetry.recordException(
                it,
                TelemetryAttribute.OPERATION to "solid.auth.refresh",
                "auth_error" to (it.error ?: "unknown"),
                "issuer" to issuerHost(current),
            )
        }
        val updatedAuthState = deepCopyAuthState(current.authState)
        updatedAuthState.update(null as TokenResponse?, exception)
        val updated = current.copy(authState = updatedAuthState)
        profileManager.writeProfile(webId, updated)
        return updated
    }

    private fun keepForRetry(
        current: Profile,
        exception: AuthorizationException?,
        span: TelemetrySpan,
    ): Profile {
        Log.w(
            AUTH_LOG_TAG,
            "Token refresh failed transiently: error=${exception?.error}, desc=${exception?.errorDescription}",
            exception,
        )
        span.putAttribute(TelemetryAttribute.OUTCOME, "transient")
        Telemetry.log("solid.auth.refresh transient failure: error=${exception?.error}")
        return current
    }

    private suspend fun expireOnlyIfAccessTokenSpent(
        webId: String,
        profile: Profile,
    ): Profile {
        if (!policy.isAccessTokenHardExpired(profile)) return profile
        if (!profile.authState.isAuthorized) return profile
        Log.w(
            AUTH_LOG_TAG,
            "Session for $webId has an expired access token and no refresh token to renew it; " +
                "marking the session expired.",
        )
        val expiry = tokenError(
            SessionErrors.SESSION_EXPIRED,
            "The access token expired and the provider issued no refresh token.",
        )
        Telemetry.recordException(
            expiry,
            TelemetryAttribute.OPERATION to "solid.auth.expiry",
            "auth_error" to SessionErrors.SESSION_EXPIRED,
            "reason" to "no_refresh_token",
            "issuer" to issuerHost(profile),
        )
        val updatedAuthState = deepCopyAuthState(profile.authState)
        updatedAuthState.update(null as TokenResponse?, expiry)
        val updated = profile.copy(authState = updatedAuthState)
        profileManager.writeProfile(webId, updated)
        return updated
    }

    suspend fun requestToken(
        profile: Profile,
        isRefresh: Boolean,
    ): Pair<TokenResponse?, AuthorizationException?> {
        if (profile.authState.lastAuthorizationResponse == null) {
            return Pair(null, profile.authState.authorizationException)
        }
        if (isRefresh && profile.authState.refreshToken == null) {
            return Pair(
                null,
                tokenError(SessionErrors.NO_REFRESH_TOKEN, "No refresh token held for this session."),
            )
        }

        val discoveryDoc = profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!

        if (isRefresh && discoveryDoc.supportsDPop()) {
            return dpopRefresh(profile, discoveryDoc)
        }

        val tokenRequest = profile.authState.createTokenRequest(isRefresh)
        val authMethod = discoveryDoc.preferredTokenEndpointAuthMethod()
        val clientSecret = profile.authState.lastRegistrationResponse?.clientSecret
        val clientAuthentication = when {
            discoveryDoc.supportsDPop() && authMethod == TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC && clientSecret != null ->
                DPopClientSecretBasic(clientSecret, tokenRequest.configuration, profile.dpopKeyId)
            discoveryDoc.supportsDPop() ->
                DPopNoClientAuth(tokenRequest.configuration, profile.dpopKeyId)
            authMethod == TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC && clientSecret != null ->
                ClientSecretBasic(clientSecret)
            else ->
                NoClientAuth
        }

        return suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(
                tokenRequest,
                clientAuthentication,
            ) { tokenResponse, exception ->
                cont.resume(Pair(tokenResponse, exception))
            }
        }
    }

    private suspend fun dpopRefresh(
        profile: Profile,
        discoveryDoc: AuthorizationServiceDiscovery,
    ): Pair<TokenResponse?, AuthorizationException?> {
        val authState = profile.authState
        val config = authState.authorizationServiceConfiguration!!
        val refreshToken = authState.refreshToken
            ?: return Pair(
                null,
                tokenError(SessionErrors.NO_REFRESH_TOKEN, "No refresh token held for this session."),
            )
        val clientId = authState.lastRegistrationResponse?.clientId
            ?: authState.lastAuthorizationResponse?.request?.clientId
            ?: return Pair(null, AuthorizationException.TokenRequestErrors.INVALID_CLIENT)
        val tokenEndpoint = URI.create(config.tokenEndpoint.toString())
        val refreshRequest = authState.createTokenRefreshRequest()

        val params = LinkedHashMap<String, String>().apply {
            put("grant_type", REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN)
            put("refresh_token", refreshToken)
            put(OidcConstants.CLIENT_AUTHENTICATION_CLIENT_ID, clientId)
            refreshRequest.scope?.let { put("scope", it) }
            refreshRequest.additionalParameters.forEach { (key, value) -> putIfAbsent(key, value) }
        }

        val authMethod = discoveryDoc.preferredTokenEndpointAuthMethod()
        val clientSecret = authState.lastRegistrationResponse?.clientSecret
        val basicAuth = if (authMethod == TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC && clientSecret != null) {
            val credentials = "${UriUtil.formUrlEncodeValue(clientId)}:${UriUtil.formUrlEncodeValue(clientSecret)}"
            "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        } else {
            null
        }

        return when (val result = dpopTokenRequester.request(tokenEndpoint, params, basicAuth, discoveryDoc, profile.dpopKeyId)) {
            is DPoPTokenResult.Success -> {
                val token = runCatching {
                    TokenResponse.Builder(refreshRequest).fromResponseJson(result.json).build()
                }.getOrNull()
                val refreshedIdToken = token?.idToken
                when {
                    token == null ->
                        Pair(null, tokenError("invalid_token_response", null))
                    refreshedIdToken != null &&
                        !isRefreshedIdTokenValid(refreshedIdToken, profile, discoveryDoc) ->
                        Pair(null, tokenError("invalid_id_token", "Refreshed ID token failed validation"))
                    else -> Pair(token, null)
                }
            }
            is DPoPTokenResult.Failure -> {
                if (result.statusCode == HTTP_TOO_MANY_REQUESTS) {
                    profile.userInfo?.webId?.let { policy.recordRateLimit(it, result.retryAfterSeconds) }
                    return Pair(
                        null,
                        tokenError(SessionErrors.RATE_LIMITED, "The token endpoint returned 429 Too Many Requests."),
                    )
                }
                val base = result.error?.let { AuthorizationException.TokenRequestErrors.byString(it) }
                    ?: AuthorizationException.TokenRequestErrors.OTHER
                Pair(
                    null,
                    AuthorizationException.fromOAuthTemplate(base, result.error, result.errorDescription, null),
                )
            }
        }
    }

    private fun tokenError(
        error: String,
        description: String?,
    ): AuthorizationException = AuthorizationException.fromOAuthTemplate(
        AuthorizationException.TokenRequestErrors.OTHER,
        error,
        description,
        null,
    )

    private suspend fun isRefreshedIdTokenValid(
        idToken: String,
        profile: Profile,
        discoveryDoc: AuthorizationServiceDiscovery,
    ): Boolean {
        return try {
            val signatureOk = IdTokenVerifier.verify(idToken, URI.create(discoveryDoc.jwksUri.toString()))
            val issuerOk = IdTokenClaims.issuer(idToken)?.trimEnd('/') == discoveryDoc.issuer.trimEnd('/')
            val identityOk = profile.userInfo?.webId?.let { IdTokenClaims.webId(idToken) == it } ?: true
            signatureOk && issuerOk && identityOk
        } catch (e: Exception) {
            Log.w(AUTH_LOG_TAG, "Refreshed ID token validation failed for ${profile.userInfo?.webId}", e)
            false
        }
    }

    private fun issuerHost(profile: Profile): String = runCatching {
        URI(
            profile.authState.authorizationServiceConfiguration
                ?.discoveryDoc
                ?.issuer
                .toString(),
        ).host
    }.getOrNull() ?: "unknown"
}

private fun AuthState.createTokenRequest(isRefresh: Boolean): TokenRequest {
    return if (isRefresh) {
        this.createTokenRefreshRequest()
    } else {
        this.lastAuthorizationResponse!!.createTokenExchangeRequest()
    }
}
