package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName

internal class AuthHeaderFactory {

    fun headersFor(
        profile: Profile,
        httpMethod: String,
        uri: String,
    ): Map<String, String>? {
        val tokenResponse = profile.authState.lastTokenResponse ?: return null
        val headers = mutableMapOf<String, String>()
        headers[HTTPHeaderName.AUTHORIZATION] =
            "${tokenResponse.tokenType} ${tokenResponse.accessToken}"
        if (tokenResponse.tokenType?.equals(HTTPHeaderName.DPOP, ignoreCase = true) == true) {
            headers[HTTPHeaderName.DPOP] = DPoPGenerator
                .getInstance(profile.authState.authorizationServiceConfiguration!!.discoveryDoc!!, profile.dpopKeyId)
                .generateProof(httpMethod, uri, tokenResponse.accessToken)
        }
        return headers
    }

    fun updateNonce(profile: Profile, resourceUri: String, nonce: String) {
        val discoveryDoc = profile.authState.authorizationServiceConfiguration?.discoveryDoc
        if (discoveryDoc != null && discoveryDoc.supportsDPop()) {
            DPoPGenerator.getInstance(discoveryDoc, profile.dpopKeyId).updateNonce(resourceUri, nonce)
        }
    }
}
