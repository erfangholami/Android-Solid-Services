package com.erfangholami.androidsolidservices.api.auth.implementation

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_PROMPT_CONSENT
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_PROMPT_LOGIN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_SCOPE_OFFLINE_ACCESS
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_SCOPE_OPENID
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.AUTHORIZATION_REQUEST_SCOPE_WEBID
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_CLIENT_NAME
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_AUTHORIZATION_CODE
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_ID_TOKEN_SIGNED_RESPONSE_ALG
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_SUBJECT_TYPE_PUBLIC
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
import com.erfangholami.androidsolidservices.api.auth.preferredIdTokenAlgorithm
import com.erfangholami.androidsolidservices.api.auth.preferredTokenEndpointAuthMethod
import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.internal.UriUtil
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume


private const val AUTH_LOG_TAG = "Authenticator"

/** Window during which concurrent refreshes for the same WebID reuse the first one's result. */
private const val REFRESH_COALESCE_MS = 5_000L

private const val REFRESH_LEAD_MS = 60_000L

internal class AuthenticatorImplementation internal constructor(
    context: Context,
    private val now: () -> Long = { System.currentTimeMillis() },
) : Authenticator, AuthSession {

    companion object {

        @Volatile
        private var INSTANCE: Authenticator? = null

        fun getInstance(context: Context): Authenticator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthenticatorImplementation(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val profileManager = ProfileManager.getInstance(context)
    private val authService = AuthorizationService(context)
    private val inProgressAuth = InProgressAuthStore()
    private val webIdResolver = WebIdResolver()
    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()
    private fun mutexFor(webId: String) = refreshMutexes.getOrPut(webId) { Mutex() }

    private val dpopTokenRequester = DPoPTokenRequester()

    /**
     * The most recent successful refresh per WebID (timestamp + resulting profile), used to
     * coalesce a burst of concurrent `forceRefresh` calls onto a single token request. Without
     * this, several requests that race on a 401 would each spend the (now rotated) refresh token,
     * and servers that detect refresh-token reuse (e.g. Inrupt) revoke the whole token family.
     */
    private val recentRefresh = ConcurrentHashMap<String, Pair<Long, Profile>>()

    override val activeProfileFlow: StateFlow<Profile?> get() = profileManager.activeProfileFlow
    override val loggedInProfilesFlow: StateFlow<List<Profile>> get() = profileManager.loggedInProfilesFlow
    override val isAuthorizedFlow: StateFlow<Boolean> get() = profileManager.isAuthorizedFlow
    override val activeWebIdFlow: StateFlow<String?> get() = profileManager.activeWebIdFlow

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String,
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

        val (conf, confError) = fetchAuthorizationConfig(issuerUrl)
        if (conf == null) {
            return Pair(
                null,
                "Cannot get access to web-id issuer configurations: ${confError?.message}"
            )
        }

        val existingRegistration = findExistingRegistration(conf.discoveryDoc?.issuer)
        val regResponse = existingRegistration
            ?: registerToOpenId(conf, appName, redirectUri)
            ?: return Pair(null, "Cannot register to OpenId.")

        val authState = AuthState(conf)
        authState.update(regResponse)
        // Mint a fresh DPoP key id for this login so the issued tokens bind to a key unique to the
        // resulting account; it is carried through the code exchange and persisted with the profile.
        inProgressAuth.set(Profile(authState = authState, dpopKeyId = UUID.randomUUID().toString().replace("-", "")))

        val existingProfile = if (webId != null) profileManager.getProfileOrNull(webId) else null
        val sameProvider = existingProfile?.authState?.authorizationServiceConfiguration
            ?.discoveryDoc?.issuer == conf.discoveryDoc?.issuer
        val prompt = if (existingProfile?.authState?.isAuthorized == true && sameProvider) AUTHORIZATION_REQUEST_PROMPT_LOGIN else AUTHORIZATION_REQUEST_PROMPT_CONSENT

        val authRequest = AuthorizationRequest.Builder(
            conf,
            regResponse.clientId,
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

        val (tokenResponse, tokenException) = requestToken(
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

        // Solid-OIDC: the issuer that minted this token must be one the WebID document explicitly
        // authorizes via solid:oidcIssuer. If the profile declares none — or none that match — the
        // issuer cannot be trusted to speak for this WebID, so the login is rejected rather than
        // accepting an unverified `webid` claim.
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
        val previousKeyId = profileManager.getProfileOrNull(realWebId)?.dpopKeyId
        profileManager.writeProfile(realWebId, finalProfile)
        profileManager.setActiveWebId(realWebId)
        inProgressAuth.clear()
        // A re-login replaces this account's DPoP key; discard the superseded one.
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
        val updated = checkTokenAndRefresh(webId, profile, forceRefresh)
        if (!updated.authState.isAuthorized) return null
        if (isAccessTokenHardExpired(updated)) return null
        return updated.authState.lastTokenResponse
    }

    override suspend fun getAuthHeaders(
        webId: String,
        httpMethod: String,
        uri: String,
    ): Map<String, String> {
        profileManager.awaitInit()
        val profile = profileManager.getProfile(webId)
        val tokenResponse = profile.authState.lastTokenResponse
            ?: throw IllegalStateException("No token available for $webId. Call getLastTokenResponse first.")
        val headers = mutableMapOf<String, String>()
        headers[HTTPHeaderName.AUTHORIZATION] =
            "${tokenResponse.tokenType} ${tokenResponse.accessToken}"
        if (tokenResponse.tokenType?.equals(HTTPHeaderName.DPOP, true) == true) {
            headers[HTTPHeaderName.DPOP] = DPoPGenerator
                .getInstance(profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!, profile.dpopKeyId)
                .generateProof(httpMethod, uri, tokenResponse.accessToken)
        }
        return headers
    }

    override fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String) {
        val profile = profileManager.getProfileOrNull(webId) ?: return
        val discoveryDoc = profile.authState.authorizationServiceConfiguration?.discoveryDoc
        if (discoveryDoc != null && discoveryDoc.supportsDPop()) {
            DPoPGenerator.getInstance(discoveryDoc, profile.dpopKeyId).updateNonce(resourceUri, nonce)
        }
    }

    override fun isUserAuthorized(): Boolean = profileManager.isUserAuthorized()
    override fun getAllLoggedInProfiles(): List<Profile> = profileManager.getAllLoggedInProfiles()
    override fun getProfile(webId: String): Profile = profileManager.getProfile(webId)
    override fun getActiveProfile(): Profile = profileManager.getActiveProfile()

    override suspend fun reloadProfile(webId: String): Profile {
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
        profileManager.writeProfile(webId, updated)
        return updated
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
        recentRefresh.remove(webId)
        profileManager.removeProfile(webId)
        keyId?.let { DPoPGenerator.deleteKeys(it) }
    }

    override suspend fun removeAllProfiles() {
        profileManager.awaitInit()
        recentRefresh.clear()
        profileManager.removeAllProfiles()
    }

    private suspend fun fetchAuthorizationConfig(
        oidcIssuer: String,
    ): Pair<AuthorizationServiceConfiguration?, AuthorizationException?> {
        return suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(oidcIssuer.toUri()) { config, exception ->
                cont.resume(Pair(config, exception))
            }
        }
    }

    private fun findExistingRegistration(issuer: String?): RegistrationResponse? {
        if (issuer == null) return null
        return profileManager.getAllLoggedInProfiles()
            .firstOrNull {
                it.authState.authorizationServiceConfiguration?.discoveryDoc?.issuer == issuer &&
                    it.authState.lastRegistrationResponse != null
            }
            ?.authState?.lastRegistrationResponse
    }

    private suspend fun registerToOpenId(
        conf: AuthorizationServiceConfiguration,
        appName: String,
        redirectUri: String,
    ): RegistrationResponse? {
        val discoveryDoc = conf.discoveryDoc!!
        val authMethod = discoveryDoc.preferredTokenEndpointAuthMethod()
        val additionalParams = mapOf(
            REGISTRATION_REQUEST_CLIENT_NAME to appName,
            REGISTRATION_REQUEST_ID_TOKEN_SIGNED_RESPONSE_ALG to discoveryDoc.preferredIdTokenAlgorithm(),
        )

        val regReq = RegistrationRequest.Builder(
            conf,
            listOf(redirectUri.toUri()),
        ).setAdditionalParameters(additionalParams)
            .setSubjectType(REGISTRATION_REQUEST_SUBJECT_TYPE_PUBLIC)
            .setTokenEndpointAuthenticationMethod(authMethod)
            .setGrantTypeValues(listOf(REGISTRATION_REQUEST_GRANT_TYPE_AUTHORIZATION_CODE, REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN))
            .build()

        return suspendCancellableCoroutine { cont ->
            authService.performRegistrationRequest(regReq) { response, _ ->
                cont.resume(response)
            }
        }
    }

    private suspend fun requestToken(
        profile: Profile,
        isRefresh: Boolean,
    ): Pair<TokenResponse?, AuthorizationException?> {
        if (profile.authState.lastAuthorizationResponse == null) {
            return Pair(null, profile.authState.authorizationException)
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
            ?: return Pair(null, AuthorizationException.TokenRequestErrors.INVALID_GRANT)
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
                if (token != null) {
                    Pair(token, null)
                } else {
                    Pair(
                        null,
                        AuthorizationException.fromOAuthTemplate(
                            AuthorizationException.TokenRequestErrors.OTHER,
                            "invalid_token_response",
                            null,
                            null,
                        ),
                    )
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

    private suspend fun checkTokenAndRefresh(
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

    private fun isAccessTokenHardExpired(profile: Profile): Boolean {
        val expirationTime =
            profile.authState.lastTokenResponse?.accessTokenExpirationTime ?: return true
        return now() >= expirationTime
    }

    private fun buildInProgressAuthHeaders(httpMethod: String, uri: String): Map<String, String> {
        val profile = inProgressAuth.get() ?: return emptyMap()
        val tokenResponse = profile.authState.lastTokenResponse ?: return emptyMap()
        val headers = mutableMapOf<String, String>()
        headers[HTTPHeaderName.AUTHORIZATION] =
            "${tokenResponse.tokenType} ${tokenResponse.accessToken}"
        if (tokenResponse.tokenType?.equals(HTTPHeaderName.DPOP) == true) {
            headers[HTTPHeaderName.DPOP] = DPoPGenerator
                .getInstance(profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!, profile.dpopKeyId)
                .generateProof(httpMethod, uri, tokenResponse.accessToken)
        }
        return headers
    }

    private fun getInProgressTokenResponse(): TokenResponse? {
        return inProgressAuth.get()?.authState?.lastTokenResponse
    }

    private fun updateInProgressDPoPNonce(resourceUri: String, nonce: String) {
        val profile = inProgressAuth.get() ?: return
        val discoveryDoc = profile.authState.authorizationServiceConfiguration?.discoveryDoc
        if (discoveryDoc != null && discoveryDoc.supportsDPop()) {
            DPoPGenerator.getInstance(discoveryDoc, profile.dpopKeyId).updateNonce(resourceUri, nonce)
        }
    }

    private fun deepCopyAuthState(authState: AuthState): AuthState {
        return AuthState.jsonDeserialize(authState.jsonSerializeString())
    }
}

private fun AuthState.createTokenRequest(isRefresh: Boolean): TokenRequest {
    return if (isRefresh) {
        this.createTokenRefreshRequest()
    } else {
        this.lastAuthorizationResponse!!.createTokenExchangeRequest()
    }
}
