package com.erfangholami.androidsolidservices.api.auth.implementation

import android.content.Context
import android.content.Intent
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
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume


private const val AUTH_LOG_TAG = "Authenticator"

internal class AuthenticatorImplementation internal constructor(
    context: Context,
    private val now: () -> Long = { System.currentTimeMillis() },
) : Authenticator {

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
                    nonceSink = { nonce -> updateInProgressDPoPNonce(nonce) },
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
        inProgressAuth.set(Profile(authState = authState))

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

    override suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?,
    ): String? {
        profileManager.awaitInit()

        val current = inProgressAuth.get() ?: return null
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
            nonceSink = { nonce -> updateInProgressDPoPNonce(nonce) },
        )

        val issuers = webIdProfile.getOidcIssuers()
        if (issuers.isNotEmpty()) {
            val tokenIss = IdTokenClaims.issuer(idToken)?.trimEnd('/')
            val issuerUris = issuers.map { it.toString().trimEnd('/') }
            if (tokenIss != null && !issuerUris.contains(tokenIss)) {
                inProgressAuth.clear()
                return ""
            }
        }

        val finalProfile = inProgressAuth.get()!!.copy(
            userInfo = userInfo,
            webId = webIdProfile,
        )

        val realWebId = userInfo.webId
        profileManager.writeProfile(realWebId, finalProfile)
        profileManager.setActiveWebId(realWebId)
        inProgressAuth.clear()
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
        checkTokenAndRefresh(webId, profile, forceRefresh)
        val updated = profileManager.getProfileOrNull(webId) ?: return null
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
                .getInstance(profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!)
                .generateProof(httpMethod, uri, tokenResponse.accessToken)
        }
        return headers
    }

    override fun updateDPoPNonce(webId: String, nonce: String) {
        val profile = profileManager.getProfileOrNull(webId) ?: return
        val discoveryDoc = profile.authState.authorizationServiceConfiguration?.discoveryDoc
        if (discoveryDoc != null && discoveryDoc.supportsDPop()) {
            DPoPGenerator.getInstance(discoveryDoc).updateNonce(nonce)
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
            nonceSink = { nonce -> updateDPoPNonce(webId, nonce) },
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
        profileManager.removeProfile(webId)
    }

    override suspend fun removeAllProfiles() {
        profileManager.awaitInit()
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

        val tokenRequest = profile.authState.createTokenRequest(isRefresh)
        val discoveryDoc = profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!
        val authMethod = discoveryDoc.preferredTokenEndpointAuthMethod()
        val clientSecret = profile.authState.lastRegistrationResponse?.clientSecret
        val clientAuthentication = when {
            discoveryDoc.supportsDPop() && authMethod == TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC && clientSecret != null ->
                DPopClientSecretBasic(clientSecret, tokenRequest.configuration)
            discoveryDoc.supportsDPop() ->
                DPopNoClientAuth(tokenRequest.configuration)
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

    private suspend fun checkTokenAndRefresh(
        webId: String,
        profile: Profile,
        forceRefresh: Boolean = false,
    ) {
        if (!forceRefresh && !needsTokenRefresh(profile)) return
        mutexFor(webId).withLock {
            val currentProfile = profileManager.getProfileOrNull(webId) ?: return@withLock
            if (!forceRefresh && !needsTokenRefresh(currentProfile)) return@withLock

            val (tokenResponse, exception) = requestToken(currentProfile, isRefresh = true)
            if (tokenResponse == null) {
                Log.w(
                    AUTH_LOG_TAG,
                    "Token refresh failed for $webId: " +
                        "error=${exception?.error}, desc=${exception?.errorDescription}",
                    exception,
                )
            }
            val updatedAuthState = deepCopyAuthState(currentProfile.authState)
            updatedAuthState.update(tokenResponse, exception)
            profileManager.writeProfile(webId, currentProfile.copy(authState = updatedAuthState))
        }
    }

    private fun needsTokenRefresh(profile: Profile): Boolean {
        val expirationTime =
            profile.authState.lastTokenResponse?.accessTokenExpirationTime ?: return true
        return (now() + 280_000L) > expirationTime
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
                .getInstance(profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!)
                .generateProof(httpMethod, uri, tokenResponse.accessToken)
        }
        return headers
    }

    private fun getInProgressTokenResponse(): TokenResponse? {
        return inProgressAuth.get()?.authState?.lastTokenResponse
    }

    private fun updateInProgressDPoPNonce(nonce: String) {
        val profile = inProgressAuth.get() ?: return
        val discoveryDoc = profile.authState.authorizationServiceConfiguration?.discoveryDoc
        if (discoveryDoc != null && discoveryDoc.supportsDPop()) {
            DPoPGenerator.getInstance(discoveryDoc).updateNonce(nonce)
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
