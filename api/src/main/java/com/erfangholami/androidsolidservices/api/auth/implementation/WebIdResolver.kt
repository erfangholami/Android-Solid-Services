package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.resource.implementation.SolidHttpClient
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidResourceParser
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import net.openid.appauth.TokenResponse
import java.net.URI

/**
 * Resolves a WebID document from a Solid pod.
 *
 * Uses a plain [SolidHttpClient] (no embedded auth) because this resolver is called
 * during the login flow, before the main [Authenticator] state is ready. Auth headers
 * are provided via callbacks from the caller's in-progress auth state.
 *
 * Spec: https://solid.github.io/webId-profile/
 *       https://solidproject.org/TR/oidc — WebID claim extraction
 */
internal class WebIdResolver {

    private val solidHttpClient = SolidHttpClient()

    suspend fun resolve(
        webIdUri: String,
        tokenProvider: suspend () -> TokenResponse?,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
    ): WebId {
        val uri = URI.create(webIdUri)
        val hasToken = tokenProvider() != null
        val headers = if (hasToken) authHeadersProvider("GET", webIdUri) else emptyMap()

        var response = solidHttpClient.send(
            method = "GET",
            uri = uri,
            accept = HTTPAcceptType.JSON_LD,
            headers = headers,
        )

        val nonce = response.headers[HTTPHeaderName.DPOP_NONCE]
        if (nonce != null && hasToken) {
            nonceSink(webIdUri, nonce)
            if (response.statusCode == 401) {
                val retryHeaders = authHeadersProvider("GET", webIdUri)
                response = solidHttpClient.send(
                    method = "GET",
                    uri = uri,
                    accept = HTTPAcceptType.JSON_LD,
                    headers = retryHeaders,
                )
            }
        }

        if (!response.isSuccessful()) {
            throw Exception("Could not resolve WebID '$webIdUri'. HTTP ${response.statusCode}")
        }

        return SolidResourceParser.parse(response, WebId::class.java)
    }
}
