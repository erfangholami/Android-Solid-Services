package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Base64
import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
import com.erfangholami.androidsolidservices.api.auth.preferredTokenEndpointAuthMethod
import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
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

/** Window during which concurrent refreshes for the same WebID reuse the first one's result. */
private const val REFRESH_COALESCE_MS = 5_000L

private const val REFRESH_LEAD_MS = 60_000L

/**
 * Synthetic OAuth error for a refresh attempted on a session that holds no refresh token.
 * Deliberately **not** in [TokenRefreshCoordinator.isTerminalRefreshError]'s set: "cannot
 * refresh" must never be conflated with the server revoking the grant (`invalid_grant`).
 */
private const val ERROR_NO_REFRESH_TOKEN = "no_refresh_token"

/**
 * Synthetic OAuth error recorded when a refresh-token-less session's access token finally
 * expires — the one case where such a session genuinely ends and re-login is required.
 */
private const val ERROR_SESSION_EXPIRED = "session_expired"

/**
 * Acquires and refreshes OAuth tokens for a WebID: the initial code-for-token exchange, DPoP-bound
 * silent refresh (RFC 9449 nonce handling that AppAuth cannot do), refresh-token-reuse coalescing,
 * and the expiry policy. Split out of [AuthenticatorImplementation]; behaviour is unchanged.
 *
 * Owns the coalescing state ([recentRefresh] + the per-WebID [refreshMutexes]) so a burst of
 * concurrent refreshes spends the rotated refresh token exactly once — servers that detect
 * refresh-token reuse (e.g. Inrupt) otherwise revoke the whole token family.
 */
internal class TokenRefreshCoordinator(
    private val authService: AuthorizationService,
    private val profileManager: ProfileManager,
    private val now: () -> Long,
) {

    private val dpopTokenRequester = DPoPTokenRequester()

    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()
    private fun mutexFor(webId: String) = refreshMutexes.getOrPut(webId) { Mutex() }

    /**
     * The most recent successful refresh per WebID (timestamp + resulting profile), used to
     * coalesce a burst of concurrent `forceRefresh` calls onto a single token request. Without
     * this, several requests that race on a 401 would each spend the (now rotated) refresh token,
     * and servers that detect refresh-token reuse (e.g. Inrupt) revoke the whole token family.
     */
    private val recentRefresh = ConcurrentHashMap<String, Pair<Long, Profile>>()

    /** Forgets any coalesced refresh for [webId] (a profile was removed). */
    fun forget(webId: String) {
        recentRefresh.remove(webId)
    }

    /** Forgets every coalesced refresh (all profiles were removed). */
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
        // Without a refresh token there is nothing to send to the token endpoint: AppAuth's
        // createTokenRefreshRequest() would throw, and the DPoP path used to synthesize
        // invalid_grant here — which read as "the server revoked the session" and terminally
        // expired an account whose access token was still perfectly valid.
        if (isRefresh && profile.authState.refreshToken == null) {
            return Pair(null, tokenError(ERROR_NO_REFRESH_TOKEN, "No refresh token held for this session."))
        }

        val discoveryDoc = profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!

        // A DPoP refresh must bypass AppAuth: performTokenRequest is single-shot and never exposes
        // the token endpoint's DPoP-Nonce response header, so it cannot satisfy a `use_dpop_nonce`
        // challenge (RFC 9449 §8) — which servers such as Inrupt require on the refresh_token grant.
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
                clientAuthentication
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
                    token == null ->
                        Pair(null, tokenError("invalid_token_response", null))
                    refreshedIdToken != null &&
                        !isRefreshedIdTokenValid(refreshedIdToken, profile, discoveryDoc) ->
                        Pair(null, tokenError("invalid_id_token", "Refreshed ID token failed validation"))
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

    private fun tokenError(error: String, description: String?): AuthorizationException =
        AuthorizationException.fromOAuthTemplate(
            AuthorizationException.TokenRequestErrors.OTHER, error, description, null,
        )

    /**
     * Validates an ID token returned by a refresh: its signature against the issuer's JWKS, that its
     * issuer matches discovery, and — critically — that the identity has not changed (a refresh must
     * never switch the account it belongs to). A returned `false` rejects the refresh.
     */
    private suspend fun isRefreshedIdTokenValid(
        idToken: String,
        profile: Profile,
        discoveryDoc: AuthorizationServiceDiscovery,
    ): Boolean {
        return try {
            IdTokenVerifier.verify(idToken, URI.create(discoveryDoc.jwksUri.toString())) &&
                IdTokenClaims.issuer(idToken)?.trimEnd('/') == discoveryDoc.issuer.trimEnd('/') &&
                (profile.userInfo?.webId?.let { IdTokenClaims.webId(idToken) == it } ?: true)
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
            // Coalesce a burst of concurrent refreshes: if one just succeeded, reuse its result
            // rather than spending the (now rotated) refresh token a second time.
            recentRefresh[webId]?.let { (at, refreshed) ->
                if (now() - at < REFRESH_COALESCE_MS) return@withLock refreshed
            }

            val currentProfile = profileManager.getProfileOrNull(webId) ?: return@withLock profile
            if (!forceRefresh && !needsTokenRefresh(currentProfile)) return@withLock currentProfile
            if (currentProfile.authState.refreshToken == null) {
                return@withLock expireOnlyIfAccessTokenSpent(webId, currentProfile)
            }

            val (tokenResponse, exception) = requestToken(currentProfile, isRefresh = true)
            when {
                tokenResponse != null -> {
                    val updatedAuthState = deepCopyAuthState(currentProfile.authState)
                    updatedAuthState.update(tokenResponse, null)
                    val updated = currentProfile.copy(authState = updatedAuthState)
                    recentRefresh[webId] = now() to updated
                    profileManager.writeProfile(webId, updated)
                    updated
                }

                isTerminalRefreshError(exception) -> {
                    // The refresh token can no longer be used; record the failure so the session
                    // reads as unauthorized and the user is prompted to sign in again.
                    Log.w(
                        AUTH_LOG_TAG,
                        "Token refresh failed terminally for $webId: error=${exception?.error}",
                        exception,
                    )
                    val updatedAuthState = deepCopyAuthState(currentProfile.authState)
                    updatedAuthState.update(null as TokenResponse?, exception)
                    val updated = currentProfile.copy(authState = updatedAuthState)
                    profileManager.writeProfile(webId, updated)
                    updated
                }

                else -> {
                    // Recoverable (DPoP nonce, transport error, 5xx): keep the existing session so a
                    // transient failure cannot force a re-login. The next call retries.
                    Log.w(
                        AUTH_LOG_TAG,
                        "Token refresh failed transiently for $webId: " +
                            "error=${exception?.error}, desc=${exception?.errorDescription}",
                        exception,
                    )
                    currentProfile
                }
            }
        }
    }

    /**
     * Resolves a refresh request for a session that was never issued a refresh token (some
     * providers withhold `offline_access` — e.g. Community Solid Server grants it only when the
     * user ticks "remember me" on its consent screen). Being unable to refresh is not, by
     * itself, the end of the session: the existing access token keeps authenticating requests
     * until it genuinely expires — a `401`-driven forced refresh must not kill a still-valid
     * session. Only once the access token is spent is the terminal expiry recorded, so the
     * account surfaces in `expiredProfilesFlow` for re-login instead of erroring forever.
     */
    private suspend fun expireOnlyIfAccessTokenSpent(webId: String, profile: Profile): Profile {
        if (!isAccessTokenHardExpired(profile)) return profile
        // Already terminally recorded — repeated probes of a dead session must not keep
        // re-logging and re-writing the same expiry.
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

    /**
     * A token-endpoint error meaning the refresh token can no longer be used, so the user must sign
     * in again. Everything else (DPoP nonce challenges, transport failures, 5xx) is transient and
     * must not invalidate a still-usable session.
     */
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
