package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidResourceParser
import com.erfangholami.androidsolidservices.api.transport.SolidHttpClient
import com.erfangholami.androidsolidservices.api.transport.SolidRawResponse
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import net.openid.appauth.TokenResponse
import java.net.URI

internal class WebIdResolver {

    private val solidHttpClient = SolidHttpClient()

    /**
     * The WebID document alone ([primary]) — the only document whose `solid:oidcIssuer` claims
     * may be trusted for identity checks — and the same profile with the extended-profile
     * documents it links folded in ([merged]), which is what an app should display and store.
     */
    internal data class Resolved(val primary: WebId, val merged: WebId)

    suspend fun resolve(
        webIdUri: String,
        tokenProvider: suspend () -> TokenResponse?,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
    ): WebId {
        val startUri = URI.create(webIdUri)
        val hasToken = tokenProvider() != null
        val response = fetchFollowingRedirects(startUri, authHeadersProvider, nonceSink) { hop ->
            hasToken && solidHttpClient.sameOrigin(startUri, hop)
        }
        if (!response.isSuccessful()) {
            throw Exception("Could not resolve WebID '$webIdUri'. HTTP ${response.statusCode}")
        }
        return SolidResourceParser.parse(response, WebId::class.java)
    }

    suspend fun resolveWithExtendedProfiles(
        webIdUri: String,
        tokenProvider: suspend () -> TokenResponse?,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
    ): Resolved {
        val primary = resolve(webIdUri, tokenProvider, authHeadersProvider, nonceSink)
        val merged = runCatching {
            mergeExtendedProfiles(primary, webIdUri, tokenProvider, authHeadersProvider, nonceSink)
        }.onFailure { t ->
            Log.w(
                LOG_TAG,
                "Could not read the extended profile documents of $webIdUri; using the WebID " +
                        "document alone.",
                t,
            )
        }.getOrDefault(primary)
        return Resolved(primary, merged)
    }

    private suspend fun mergeExtendedProfiles(
        primary: WebId,
        webIdUri: String,
        tokenProvider: suspend () -> TokenResponse?,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
    ): WebId {
        val webIdDoc = webIdUri.substringBefore('#')
        val docs = (primary.getPrimaryTopicDocuments() + primary.getRelatedResources())
            .map { it.substringBefore('#') }
            .distinct()
            .filter { it != webIdDoc }
            .take(MAX_EXTENDED_PROFILE_DOCS)
        if (docs.isEmpty()) return primary

        val startUri = URI.create(webIdUri)
        val hasToken = tokenProvider() != null
        val ownOrigins = listOf(startUri) +
                primary.getStorages().mapNotNull { runCatching { URI.create(it) }.getOrNull() }
        val extraQuads: List<RdfQuad> = docs
            .mapNotNull { doc ->
                readExtendedProfile(doc, hasToken, ownOrigins, authHeadersProvider, nonceSink)
            }
            .flatMap { it.getAllQuads() }
        if (extraQuads.isEmpty()) return primary
        return WebId(
            primary.getIdentifier(),
            primary.getContentType(),
            (primary.getAllQuads() + extraQuads).distinct(),
            primary.getHeaders(),
        )
    }

    private suspend fun readExtendedProfile(
        doc: String,
        hasToken: Boolean,
        ownOrigins: List<URI>,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
    ): WebId? {
        val docUri = runCatching { URI.create(doc) }.getOrNull() ?: return null
        val response = runCatching {
            fetchFollowingRedirects(docUri, authHeadersProvider, nonceSink) { hop ->
                hasToken && ownOrigins.any { solidHttpClient.sameOrigin(it, hop) }
            }
        }.getOrNull() ?: return null
        if (!response.isSuccessful()) return null
        return runCatching { SolidResourceParser.parse(response, WebId::class.java) }.getOrNull()
    }

    private suspend fun fetchFollowingRedirects(
        startUri: URI,
        authHeadersProvider: suspend (httpMethod: String, uri: String) -> Map<String, String>,
        nonceSink: (forUri: String, nonce: String) -> Unit,
        attachAuthFor: (URI) -> Boolean,
    ): SolidRawResponse {
        var currentUri = startUri
        var hops = 0
        while (true) {
            val response = fetch(currentUri, attachAuthFor(currentUri), authHeadersProvider, nonceSink)
            val target = solidHttpClient.redirectTarget(response, currentUri)
            if (target != null && hops++ < SolidHttpClient.MAX_REDIRECTS) {
                currentUri = target
                continue
            }
            return response
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

    private companion object {
        const val LOG_TAG = "WebIdResolver"
        const val MAX_EXTENDED_PROFILE_DOCS = 3
    }
}
