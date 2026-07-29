package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Base64
import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
import com.erfangholami.androidsolidservices.api.auth.preferredTokenEndpointAuthMethod
import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

private const val REFRESH_COALESCE_MS = 5_000L

private const val REFRESH_LEAD_MS = 60_000L

private const val ERROR_NO_REFRESH_TOKEN = "no_refresh_token"

private const val ERROR_SESSION_EXPIRED = "session_expired"

internal class TokenRefreshCoordinator(
    private val authService: AuthorizationService,
    private val profileManager: ProfileManager,
    private val now: () -> Long,
) {
    private val dpopTokenRequester = DPoPTokenRequester()

    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(webId: String) = refreshMutexes.getOrPut(webId) { Mutex() }

    private val recentRefresh = ConcurrentHashMap<String, Pair<Long, Profile>>()

    fun forget(webId: String) {
        recentRefresh.remove(webId)
    }

    fun forgetAll() {
        recentRefresh.clear()
    }

    suspend fun requestToken(
        profile: Profile,
        isRefresh: Boolean,
    ): Pair<TokenResponse?, AuthorizationException?> {
        if (profile.authState.lastAuthorizationResponse == null) {
            return Pair(null, profile.authState.authorizationException)
        }
        if (isRefresh && profile.authState.refreshToken == null) {
            return Pair(null, tokenError(ERROR_NO_REFRESH_TOKEN, "No refresh token held for this session."))
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
            ?: return Pair(null, tokenError(ERROR_NO_REFRESH_TOKEN, "No refresh token held for this session."))
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
                    token == null -> {
                        Log.w(
                            AUTH_LOG_TAG,
                            "AuthTrace: 2xx token response could not be parsed for ${profile.userInfo?.webId} — " +
                                "if the provider rotated the refresh token, the new token is being DISCARDED here",
                        )
                        Pair(null, tokenError("invalid_token_response", null))
                    }
                    refreshedIdToken != null &&
                        !isRefreshedIdTokenValid(refreshedIdToken, profile, discoveryDoc) -> {
                        Log.w(
                            AUTH_LOG_TAG,
                            "AuthTrace: 2xx refresh REJECTED by ID-token validation for ${profile.userInfo?.webId} — " +
                                "rotated refresh token rtNew=${tokenFp(token.refreshToken)} is being DISCARDED " +
                                "while rtSent=${tokenFp(authState.refreshToken)} was already spent server-side",
                        )
                        Pair(null, tokenError("invalid_id_token", "Refreshed ID token failed validation"))
                    }
                    else -> Pair(token, null)
                }
            }
            is DPoPTokenResult.Failure -> {
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
            if (!signatureOk || !issuerOk || !identityOk) {
                Log.w(
                    AUTH_LOG_TAG,
                    "AuthTrace: refreshed ID token checks for ${profile.userInfo?.webId}: " +
                        "signature/JWKS=$signatureOk issuer=$issuerOk identity=$identityOk",
                )
            }
            signatureOk && issuerOk && identityOk
        } catch (e: Exception) {
            Log.w(AUTH_LOG_TAG, "Refreshed ID token validation failed for ${profile.userInfo?.webId}", e)
            false
        }
    }

    suspend fun checkTokenAndRefresh(
        webId: String,
        profile: Profile,
        forceRefresh: Boolean = false,
    ): Profile {
        if (!forceRefresh && !needsTokenRefresh(profile)) return profile
        return mutexFor(webId).withLock {
            recentRefresh[webId]?.let { (at, refreshed) ->
                if (now() - at < REFRESH_COALESCE_MS) {
                    Log.i(
                        AUTH_LOG_TAG,
                        "AuthTrace: refresh coalesced for $webId — reusing result from ${now() - at}ms ago " +
                            "(rt=${tokenFp(refreshed.authState.refreshToken)})",
                    )
                    return@withLock refreshed
                }
            }

            val currentProfile = profileManager.getProfileOrNull(webId) ?: return@withLock profile
            if (!forceRefresh && !needsTokenRefresh(currentProfile)) return@withLock currentProfile
            if (currentProfile.authState.refreshToken == null) {
                return@withLock expireOnlyIfAccessTokenSpent(webId, currentProfile)
            }

            Log.i(
                AUTH_LOG_TAG,
                "AuthTrace: refresh start for $webId force=$forceRefresh " +
                    "rtSent=${tokenFp(currentProfile.authState.refreshToken)} " +
                    "accessExpiresInMs=${currentProfile.authState.lastTokenResponse?.accessTokenExpirationTime?.minus(now())} " +
                    "isAuthorized=${currentProfile.authState.isAuthorized}",
            )
            val refreshSpan = Telemetry.startSpan("solid_auth_refresh")
            withContext(NonCancellable) {
                try {
                    val (tokenResponse, exception) = requestToken(currentProfile, isRefresh = true)
                    when {
                        tokenResponse != null -> {
                            val updatedAuthState = deepCopyAuthState(currentProfile.authState)
                            updatedAuthState.update(tokenResponse, null)
                            val updated = currentProfile.copy(authState = updatedAuthState)
                            recentRefresh[webId] = now() to updated
                            Log.i(
                                AUTH_LOG_TAG,
                                "AuthTrace: refresh ok for $webId rt " +
                                    "${tokenFp(currentProfile.authState.refreshToken)} -> ${tokenFp(updatedAuthState.refreshToken)}" +
                                    (if (tokenResponse.refreshToken == null) " (response carried no new refresh token)" else " (rotated)"),
                            )
                            profileManager.writeProfile(webId, updated)
                            refreshSpan.putAttribute(TelemetryAttribute.OUTCOME, TelemetryAttribute.OUTCOME_SUCCESS)
                            updated
                        }

                        isTerminalRefreshError(exception) -> {
                            Log.w(
                                AUTH_LOG_TAG,
                                "Token refresh failed terminally for $webId: error=${exception?.error} " +
                                    "(AuthTrace: rtSent=${tokenFp(currentProfile.authState.refreshToken)})",
                                exception,
                            )
                            refreshSpan.putAttribute(TelemetryAttribute.OUTCOME, "terminal")
                            exception?.let {
                                Telemetry.recordException(
                                    it,
                                    TelemetryAttribute.OPERATION to "solid.auth.refresh",
                                    "auth_error" to (it.error ?: "unknown"),
                                    "issuer" to issuerHost(currentProfile),
                                )
                            }
                            val updatedAuthState = deepCopyAuthState(currentProfile.authState)
                            updatedAuthState.update(null as TokenResponse?, exception)
                            val updated = currentProfile.copy(authState = updatedAuthState)
                            profileManager.writeProfile(webId, updated)
                            updated
                        }

                        else -> {
                            Log.w(
                                AUTH_LOG_TAG,
                                "Token refresh failed transiently for $webId: " +
                                    "error=${exception?.error}, desc=${exception?.errorDescription} " +
                                    "(AuthTrace: rtSent=${tokenFp(currentProfile.authState.refreshToken)} — " +
                                    "this token will be RE-SENT on the next attempt)",
                                exception,
                            )
                            refreshSpan.putAttribute(TelemetryAttribute.OUTCOME, "transient")
                            Telemetry.log("solid.auth.refresh transient failure: error=${exception?.error}")
                            currentProfile
                        }
                    }
                } finally {
                    refreshSpan.stop()
                }
            }
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

    private suspend fun expireOnlyIfAccessTokenSpent(
        webId: String,
        profile: Profile,
    ): Profile {
        if (!isAccessTokenHardExpired(profile)) return profile
        if (!profile.authState.isAuthorized) return profile
        Log.w(
            AUTH_LOG_TAG,
            "Session for $webId has an expired access token and no refresh token to renew it; " +
                "marking the session expired.",
        )
        val updatedAuthState = deepCopyAuthState(profile.authState)
        updatedAuthState.update(
            null as TokenResponse?,
            tokenError(ERROR_SESSION_EXPIRED, "The access token expired and the provider issued no refresh token."),
        )
        val updated = profile.copy(authState = updatedAuthState)
        profileManager.writeProfile(webId, updated)
        return updated
    }

    private fun isTerminalRefreshError(exception: AuthorizationException?): Boolean {
        val error = exception?.error ?: return false
        return error == "invalid_grant" || error == "invalid_client"
    }

    private fun needsTokenRefresh(profile: Profile): Boolean {
        val expirationTime =
            profile.authState.lastTokenResponse?.accessTokenExpirationTime ?: return true
        return (now() + REFRESH_LEAD_MS) > expirationTime
    }

    fun isAccessTokenHardExpired(profile: Profile): Boolean {
        val expirationTime =
            profile.authState.lastTokenResponse?.accessTokenExpirationTime ?: return true
        return now() >= expirationTime
    }
}

private fun AuthState.createTokenRequest(isRefresh: Boolean): TokenRequest {
    return if (isRefresh) {
        this.createTokenRefreshRequest()
    } else {
        this.lastAuthorizationResponse!!.createTokenExchangeRequest()
    }
}
