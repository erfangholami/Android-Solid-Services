package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.http.SolidRawResponse
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
        val startUri = URI.create(webIdUri)
        val hasToken = tokenProvider() != null

        var currentUri = startUri
        var hops = 0
        while (true) {
            val attachAuth = hasToken && solidHttpClient.sameOrigin(startUri, currentUri)
            val response = fetch(currentUri, attachAuth, authHeadersProvider, nonceSink)

            val target = solidHttpClient.redirectTarget(response, currentUri)
            if (target != null && hops++ < SolidHttpClient.MAX_REDIRECTS) {
                currentUri = target
                continue
            }

            if (!response.isSuccessful()) {
                throw Exception("Could not resolve WebID '$webIdUri'. HTTP ${response.statusCode}")
            }
            return SolidResourceParser.parse(response, WebId::class.java)
        }
    }

    private suspend fun fetch(
        uri: URI,
        attachAuth: Boolean,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
    ): SolidRawResponse {
        val headers = if (attachAuth) authHeadersProvider("GET", uri.toString()) else emptyMap()
        var response = solidHttpClient.send(
            method = "GET",
            uri = uri,
            accept = HTTPAcceptType.JSON_LD,
            headers = headers,
        )

        val nonce = response.headers[HTTPHeaderName.DPOP_NONCE]
        if (nonce != null && attachAuth) {
            nonceSink(uri.toString(), nonce)
            if (response.statusCode == 401) {
                val retryHeaders = authHeadersProvider("GET", uri.toString())
                response = solidHttpClient.send(
                    method = "GET",
                    uri = uri,
                    accept = HTTPAcceptType.JSON_LD,
                    headers = retryHeaders,
                )
            }
        }
        return response
    }
}
