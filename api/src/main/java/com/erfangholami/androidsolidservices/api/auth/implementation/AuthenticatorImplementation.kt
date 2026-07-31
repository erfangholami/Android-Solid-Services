package com.erfangholami.androidsolidservices.api.auth.implementation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_PROMPT_CONSENT
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_PROMPT_LOGIN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_SCOPE_OFFLINE_ACCESS
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_SCOPE_OPENID
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_SCOPE_WEBID
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import java.net.URI
import java.util.UUID

private const val AUTH_LOG_TAG = "Authenticator"

internal class AuthenticatorImplementation internal constructor(
    context: Context,
    now: () -> Long = { System.currentTimeMillis() },
) : Authenticator, AuthSession {

    companion object {

        @Volatile
        private var instance: Authenticator? = null

        fun getInstance(context: Context): Authenticator {
            return instance ?: synchronized(this) {
                instance ?: AuthenticatorImplementation(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val profileManager = ProfileManager.getInstance(context)
    private val authService = AuthorizationService(context)
    private val inProgressAuth = InProgressAuthStore()
    private val webIdResolver = WebIdResolver()

    private val tokenCoordinator = TokenRefreshCoordinator(authService, profileManager, now)
    private val registrationService = ClientRegistrationService(authService, profileManager)
    private val authHeaders = AuthHeaderFactory()

    override val activeProfileFlow: StateFlow<SolidAccount?> get() = profileManager.activeAccountFlow
    override val loggedInProfilesFlow: StateFlow<List<SolidAccount>> get() = profileManager.loggedInAccountsFlow
    override val expiredProfilesFlow: StateFlow<List<SolidAccount>> get() = profileManager.expiredAccountsFlow
    override val isAuthorizedFlow: StateFlow<Boolean> get() = profileManager.isAuthorizedFlow
    override val activeWebIdFlow: StateFlow<String?> get() = profileManager.activeWebIdFlow

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String,
        clientId: String?,
    ): Pair<Intent?, String?> {
        profileManager.awaitInit()

        val issuerUrl = when {
            oidcIssuer != null -> oidcIssuer
            webId != null -> {
                val webIdProfile = webIdResolver.resolve(
                    webIdUri = webId,
                    tokenProvider = { getInProgressTokenResponse() },
                    authHeadersProvider = { method, uri ->
                        buildInProgressAuthHeaders(
                            method,
                            uri
                        )
                    },
                    nonceSink = { forUri, nonce -> updateInProgressDPoPNonce(forUri, nonce) },
                )
                val issuers = webIdProfile.getOidcIssuers()
                if (issuers.isEmpty()) {
                    return Pair(null, "No OIDC issuers found in WebID profile.")
                }
                issuers[0].toString()
            }
            else -> return Pair(null, "Either webId or oidcIssuer must be provided.")
        }

        // AppAuth rejects non-https from inside its own AsyncTask, where the throw is uncaught and
        // takes the process down, so the check has to happen before it sees the URL.
        val httpsIssuerUrl = asHttpsIssuerUrl(issuerUrl)
            ?: return Pair(null, "'$issuerUrl' is not a valid provider address. It must be an https URL.")

        val (conf, confError) = registrationService.fetchAuthorizationConfig(httpsIssuerUrl)
        if (conf == null) {
            return Pair(
                null,
                "Cannot get access to web-id issuer configurations: ${confError?.message}"
            )
        }

        val regResponse = if (clientId == null) {
            registrationService.findExistingRegistration(conf.discoveryDoc?.issuer)
                ?: registrationService.registerToOpenId(conf, appName, redirectUri)
                ?: return Pair(null, "Cannot register to OpenId.")
        } else {
            null
        }
        val effectiveClientId = clientId ?: regResponse!!.clientId

        val authState = AuthState(conf)
        if (regResponse != null) authState.update(regResponse)
        inProgressAuth.set(Profile(authState = authState, dpopKeyId = UUID.randomUUID().toString().replace("-", "")))

        val existingProfile = if (webId != null) profileManager.getProfileOrNull(webId) else null
        val sameProvider = existingProfile?.authState?.authorizationServiceConfiguration
            ?.discoveryDoc?.issuer == conf.discoveryDoc?.issuer
        val prompt = if (existingProfile?.authState?.isAuthorized == true && sameProvider) AUTHORIZATION_REQUEST_PROMPT_LOGIN else AUTHORIZATION_REQUEST_PROMPT_CONSENT

        val authRequest = AuthorizationRequest.Builder(
            conf,
            effectiveClientId,
            ResponseTypeValues.CODE,
            redirectUri.toUri(),
        )
            .setScopes(AUTHORIZATION_REQUEST_SCOPE_WEBID, AUTHORIZATION_REQUEST_SCOPE_OPENID, AUTHORIZATION_REQUEST_SCOPE_OFFLINE_ACCESS)
            .setPrompt(prompt)
            .setResponseMode(AuthorizationRequest.ResponseMode.QUERY)
            .build()

        return Pair(authService.getAuthorizationRequestIntent(authRequest), null)
    }

    override suspend fun submitAuthorizationResponse(responseData: Intent?): String? {
        profileManager.awaitInit()

        val authResponse = responseData?.let { AuthorizationResponse.fromIntent(it) }
        val authException = responseData?.let { AuthorizationException.fromIntent(it) }

        val current = inProgressAuth.get() ?: return null
        if (authResponse == null && authException == null) return null

        val updatedAuthState = deepCopyAuthState(current.authState)
        updatedAuthState.update(authResponse, authException)
        inProgressAuth.set(current.copy(authState = updatedAuthState))

        if (authException != null || authResponse == null) return null

        val (tokenResponse, tokenException) = tokenCoordinator.requestToken(
            inProgressAuth.get()!!,
            isRefresh = false
        )
        if (tokenException != null || tokenResponse == null) return ""

        val updatedAfterToken = deepCopyAuthState(inProgressAuth.get()!!.authState)
        updatedAfterToken.update(tokenResponse, tokenException)
        inProgressAuth.set(inProgressAuth.get()!!.copy(authState = updatedAfterToken))

        val idToken = inProgressAuth.get()!!.authState.idToken ?: return ""
        val jwksUri = inProgressAuth.get()!!.authState.authorizationServiceConfiguration
            ?.discoveryDoc?.jwksUri
        if (jwksUri == null || !IdTokenVerifier.verify(idToken, URI.create(jwksUri.toString()))) {
            inProgressAuth.clear()
            return ""
        }

        val userInfo = IdTokenClaims.userInfo(idToken)

        val webIdProfile = webIdResolver.resolve(
            webIdUri = userInfo.webId,
            tokenProvider = { inProgressAuth.get()!!.authState.lastTokenResponse },
            authHeadersProvider = { method, uri -> buildInProgressAuthHeaders(method, uri) },
            nonceSink = { forUri, nonce -> updateInProgressDPoPNonce(forUri, nonce) },
        )

        val tokenIss = IdTokenClaims.issuer(idToken)?.trimEnd('/')
        val declaredIssuers = webIdProfile.getOidcIssuers().map { it.toString().trimEnd('/') }
        if (tokenIss == null || tokenIss !in declaredIssuers) {
            Log.w(
                AUTH_LOG_TAG,
                "Rejecting login for ${userInfo.webId}: token issuer '$tokenIss' is not listed as a " +
                    "solid:oidcIssuer in the WebID profile (declared: $declaredIssuers).",
            )
            inProgressAuth.clear()
            return ""
        }

        val finalProfile = inProgressAuth.get()!!.copy(
            userInfo = userInfo,
            webId = webIdProfile,
        )

        val realWebId = userInfo.webId
        if (finalProfile.authState.refreshToken == null) {
            Log.w(
                AUTH_LOG_TAG,
                "Login for $realWebId returned no refresh token; the session will last only until " +
                    "the access token expires. Some providers withhold offline_access unless the " +
                    "user opts to stay logged in on the consent screen.",
            )
        }
        val previousKeyId = profileManager.getProfileOrNull(realWebId)?.dpopKeyId
        Log.i(
            AUTH_LOG_TAG,
            "AuthTrace: login persisted for $realWebId " +
                "rt=${tokenFp(finalProfile.authState.refreshToken)} keyId=${finalProfile.dpopKeyId}",
        )
        profileManager.writeProfile(realWebId, finalProfile)
        profileManager.setActiveWebId(realWebId)
        inProgressAuth.clear()
        if (previousKeyId != null && previousKeyId != finalProfile.dpopKeyId) {
            DPoPGenerator.deleteKeys(previousKeyId)
        }
        return realWebId
    }

    override suspend fun getTerminationSessionIntent(
        webId: String,
        logoutRedirectUrl: String,
    ): Pair<Intent?, String?> {
        profileManager.awaitInit()
        val profile = profileManager.getProfileOrNull(webId)
            ?: return Pair(null, "No profile found for $webId")

        if (profile.authState.lastAuthorizationResponse == null ||
            profile.authState.authorizationServiceConfiguration == null
        ) {
            return Pair(null, "There is no configuration")
        }

        val token = getLastTokenResponse(webId)
        val endSessionReq =
            EndSessionRequest.Builder(profile.authState.authorizationServiceConfiguration!!)
                .setIdTokenHint(token!!.idToken)
                .setPostLogoutRedirectUri(logoutRedirectUrl.toUri())
                .build()
        return Pair(authService.getEndSessionRequestIntent(endSessionReq), null)
    }

    override suspend fun getLastTokenResponse(
        webId: String,
        forceRefresh: Boolean,
    ): TokenResponse? {
        profileManager.awaitInit()
        val profile = profileManager.getProfileOrNull(webId) ?: return null
        val updated = tokenCoordinator.checkTokenAndRefresh(webId, profile, forceRefresh)
        if (!updated.authState.isAuthorized) return null
        if (tokenCoordinator.isAccessTokenHardExpired(updated)) return null
        return updated.authState.lastTokenResponse
    }

    override suspend fun getAuthHeaders(
        webId: String,
        httpMethod: String,
        uri: String,
    ): Map<String, String> {
        profileManager.awaitInit()
        val profile = profileManager.getProfile(webId)
        return authHeaders.headersFor(profile, httpMethod, uri)
            ?: throw IllegalStateException("No token available for $webId. Call getLastTokenResponse first.")
    }

    override fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String) {
        val profile = profileManager.getProfileOrNull(webId) ?: return
        authHeaders.updateNonce(profile, resourceUri, nonce)
    }

    override fun isUserAuthorized(): Boolean = profileManager.isUserAuthorized()
    override fun getAllLoggedInProfiles(): List<SolidAccount> =
        profileManager.getAllLoggedInProfiles().map { it.toAccount() }
    override fun getProfile(webId: String): SolidAccount = profileManager.getProfile(webId).toAccount()
    override fun getActiveProfile(): SolidAccount = profileManager.getActiveProfile().toAccount()

    override suspend fun reloadProfile(webId: String): SolidAccount {
        profileManager.awaitInit()
        val profile = profileManager.getProfile(webId)
        getLastTokenResponse(webId)
        val refreshedWebId = webIdResolver.resolve(
            webIdUri = webId,
            tokenProvider = { profileManager.getProfileOrNull(webId)?.authState?.lastTokenResponse },
            authHeadersProvider = { method, uri -> getAuthHeaders(webId, method, uri) },
            nonceSink = { forUri, nonce -> updateDPoPNonce(webId, forUri, nonce) },
        )
        val updated = profileManager.getProfile(webId).copy(webId = refreshedWebId)
        Log.i(
            AUTH_LOG_TAG,
            "AuthTrace: reloadProfile persisting $webId rt=${tokenFp(updated.authState.refreshToken)} — " +
                "if this fingerprint is OLDER than the last 'refresh ok' line, a rotated token was just clobbered",
        )
        profileManager.writeProfile(webId, updated)
        return updated.toAccount()
    }

    override suspend fun getActiveWebId(): String? {
        profileManager.awaitInit()
        return profileManager.getActiveWebId()
    }

    override suspend fun setActiveWebId(webId: String) {
        profileManager.awaitInit()
        profileManager.setActiveWebId(webId)
    }

    override suspend fun removeProfile(webId: String) {
        profileManager.awaitInit()
        val keyId = profileManager.getProfileOrNull(webId)?.dpopKeyId
        tokenCoordinator.forget(webId)
        profileManager.removeProfile(webId)
        keyId?.let { DPoPGenerator.deleteKeys(it) }
    }

    override suspend fun removeAllProfiles() {
        profileManager.awaitInit()
        tokenCoordinator.forgetAll()
        profileManager.removeAllProfiles()
    }

    private fun buildInProgressAuthHeaders(httpMethod: String, uri: String): Map<String, String> {
        val profile = inProgressAuth.get() ?: return emptyMap()
        return authHeaders.headersFor(profile, httpMethod, uri) ?: emptyMap()
    }

    private fun getInProgressTokenResponse(): TokenResponse? {
        return inProgressAuth.get()?.authState?.lastTokenResponse
    }

    private fun updateInProgressDPoPNonce(resourceUri: String, nonce: String) {
        val profile = inProgressAuth.get() ?: return
        authHeaders.updateNonce(profile, resourceUri, nonce)
    }
}

internal fun asHttpsIssuerUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    // Someone typing a bare domain means https; anything else has to say so and be https.
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank()) return null
    // AppAuth matches the scheme case-sensitively, so hand it one that is already lowercase.
    return "https://" + withScheme.substringAfter("://")
}
