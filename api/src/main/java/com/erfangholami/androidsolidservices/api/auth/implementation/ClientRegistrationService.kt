package com.erfangholami.androidsolidservices.api.auth.implementation

import androidx.core.net.toUri
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_CLIENT_NAME
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_AUTHORIZATION_CODE
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_GRANT_TYPE_REFRESH_TOKEN
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_ID_TOKEN_SIGNED_RESPONSE_ALG
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.REGISTRATION_REQUEST_SUBJECT_TYPE_PUBLIC
import com.erfangholami.androidsolidservices.api.auth.preferredIdTokenAlgorithm
import com.erfangholami.androidsolidservices.api.auth.preferredTokenEndpointAuthMethod
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.RegistrationResponse
import kotlin.coroutines.resume

internal class ClientRegistrationService(
    private val authService: AuthorizationService,
    private val profileManager: ProfileManager,
) {

    suspend fun fetchAuthorizationConfig(
        oidcIssuer: String,
    ): Pair<AuthorizationServiceConfiguration?, AuthorizationException?> {
        return suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(oidcIssuer.toUri()) { config, exception ->
                cont.resume(Pair(config, exception))
            }
        }
    }

    fun findExistingRegistration(issuer: String?): RegistrationResponse? {
        if (issuer == null) return null
        return profileManager.getAllLoggedInProfiles()
            .firstOrNull {
                it.authState.authorizationServiceConfiguration?.discoveryDoc?.issuer == issuer &&
                    it.authState.lastRegistrationResponse != null
            }
            ?.authState?.lastRegistrationResponse
    }

    suspend fun registerToOpenId(
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
}
