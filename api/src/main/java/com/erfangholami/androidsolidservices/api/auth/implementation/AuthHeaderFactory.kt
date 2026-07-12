package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.model.profile.Profile

/**
 * Builds the outgoing `Authorization` (+ `DPoP` proof, when the token is DPoP-bound) headers for a
 * single request from a [Profile]'s current token, and records the `DPoP-Nonce` a server returned
 * so the next proof can satisfy it. Split out of [AuthenticatorImplementation]; behaviour unchanged.
 *
 * Stateless with respect to the account set — the caller supplies whichever [Profile] the request
 * authenticates as (the active account, or the in-progress login), so the same logic serves both.
 */
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
