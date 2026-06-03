package com.erfangholami.androidsolidservices.api.auth

import com.erfangholami.androidsolidservices.api.auth.implementation.DPopSupportedAlgo
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.DPOP_SIGNING_ALG_VALUES_SUPPORTED
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
import com.erfangholami.androidsolidservices.api.auth.implementation.OidcConstants.TOKEN_ENDPOINT_AUTH_METHOD_NONE
import net.openid.appauth.AuthorizationServiceDiscovery
import org.json.JSONArray
import org.json.JSONException

internal fun AuthorizationServiceDiscovery.supportsDPop(): Boolean {
    return this.docJson.has(DPOP_SIGNING_ALG_VALUES_SUPPORTED)
}

internal fun AuthorizationServiceDiscovery.supportedDPopAlgorithms(): List<String> {
    return if (this.supportsDPop()) {
        this.getStringList(DPOP_SIGNING_ALG_VALUES_SUPPORTED)!!
    } else {
        listOf()
    }
}

internal fun AuthorizationServiceDiscovery.preferredIdTokenAlgorithm(): String {
    val supported = this.getStringList(ID_TOKEN_SIGNING_ALG_VALUES_SUPPORTED) ?: return DPopSupportedAlgo.RS256.name
    return listOf(DPopSupportedAlgo.RS256.name, DPopSupportedAlgo.ES256.name, DPopSupportedAlgo.RS384.name,
        DPopSupportedAlgo.ES384.name, DPopSupportedAlgo.RS512.name, DPopSupportedAlgo.ES512.name,
        DPopSupportedAlgo.PS256.name, DPopSupportedAlgo.PS384.name, DPopSupportedAlgo.PS512.name
    )
        .firstOrNull { supported.contains(it) } ?: supported.firstOrNull() ?: DPopSupportedAlgo.RS256.name
}

internal fun AuthorizationServiceDiscovery.preferredTokenEndpointAuthMethod(): String {
    val supported = this.getStringList(TOKEN_ENDPOINT_AUTH_METHODS_SUPPORTED) ?: return TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
    return listOf(TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC, TOKEN_ENDPOINT_AUTH_METHOD_NONE)
        .firstOrNull { supported.contains(it) } ?: supported.firstOrNull() ?: TOKEN_ENDPOINT_AUTH_METHOD_CLIENT_SECRET_BASIC
}

private fun AuthorizationServiceDiscovery.getStringList(field: String): List<String>? {
    try {
        if (!this.docJson.has(field)) {
            return null
        }
        val value = this.docJson.get(field)
        check(value is JSONArray) {
            ("$field does not contain the expected JSON array")
        }
        val values = ArrayList<String>()
        for (i in 0..<value.length()) {
            values.add(value.getString(i))
        }
        return values
    } catch (e: JSONException) {
        throw IllegalStateException("unexpected JSONException", e)
    }
}
