package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Base64
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.CLIENT_AUTHENTICATION_CLIENT_ID
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.internal.UriUtil

internal class DPopClientSecretBasic(
    private val clientSecret: String,
    private val configuration: AuthorizationServiceConfiguration,
) : ClientAuthentication {

    override fun getRequestHeaders(clientId: String): Map<String?, String?> {
        val encodedClientId = UriUtil.formUrlEncodeValue(clientId)
        val encodedClientSecret = UriUtil.formUrlEncodeValue(clientSecret)
        val credentials = "$encodedClientId:$encodedClientSecret"
        val basicAuth = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        val dpop = DPoPGenerator.getInstance(configuration.discoveryDoc!!)
            .generateProof("POST", configuration.tokenEndpoint.toString())
        return mapOf(
            HTTPHeaderName.AUTHORIZATION to "Basic $basicAuth",
            HTTPHeaderName.DPOP to dpop
        )
    }

    override fun getRequestParameters(clientId: String): Map<String?, String?> {
        return mapOf(CLIENT_AUTHENTICATION_CLIENT_ID to clientId)
    }
}

internal class DPopNoClientAuth(
    private val configuration: AuthorizationServiceConfiguration,
) : ClientAuthentication {

    override fun getRequestHeaders(clientId: String): Map<String?, String?> {
        val dpop = DPoPGenerator.getInstance(configuration.discoveryDoc!!)
            .generateProof("POST", configuration.tokenEndpoint.toString())
        return mapOf(HTTPHeaderName.DPOP to dpop)
    }

    override fun getRequestParameters(clientId: String): Map<String?, String?> {
        return mapOf(CLIENT_AUTHENTICATION_CLIENT_ID to clientId)
    }
}

internal object NoClientAuth : ClientAuthentication {
    override fun getRequestHeaders(clientId: String): Map<String?, String?> = emptyMap()
    override fun getRequestParameters(clientId: String): Map<String?, String?> =
        mapOf(CLIENT_AUTHENTICATION_CLIENT_ID to clientId)
}
