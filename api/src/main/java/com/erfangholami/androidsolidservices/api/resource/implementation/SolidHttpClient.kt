package com.erfangholami.androidsolidservices.api.resource.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.implementation.AuthSession
import com.erfangholami.androidsolidservices.api.http.SolidRawResponse
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidHttpClient.Companion.DEBUG_TRACE
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.util.encodeUri
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

internal class SolidHttpClient(
    private val auth: AuthSession? = null,
    httpClient: OkHttpClient = defaultHttpClient(),
) {

    /**
     * Redirects are handled manually in [executeAuthenticated] so each hop can re-sign
     * its DPoP proof for the new URL (a transparently-followed redirect would reuse the
     * proof bound to the original `htu` and get a `401`), and so credentials are only
     * re-sent to a same-origin target. The transport is therefore forced to surface
     * `3xx` responses rather than chase them.
     */
    private val httpClient: OkHttpClient =
        httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()

    private val cache = SolidResponseCache()

    suspend fun send(
        method: String,
        uri: URI,
        contentType: String? = null,
        accept: String? = null,
        linkHeader: String? = null,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
    ): SolidRawResponse = withContext(Dispatchers.IO) {
        if (DEBUG_TRACE) Log.d(TAG, "→ $method $uri")
        val mediaType = contentType?.toMediaTypeOrNull()
        val requestBody: RequestBody? = when {
            body != null -> body.toRequestBody(mediaType)
            method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val request = Request.Builder()
            .url(encodeUri(uri).toString())
            .apply {
                if (accept != null) header(HTTPHeaderName.ACCEPT, accept)
                if (contentType != null) header(HTTPHeaderName.CONTENT_TYPE, contentType)
                if (linkHeader != null) header(HTTPHeaderName.LINK, linkHeader)
                headers.forEach { (k, v) -> addHeader(k, v) }
                method(method, requestBody)
            }
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyBytes = response.body?.bytes() ?: ByteArray(0)
        val effectiveUri = try {
            response.request.url.toUri()
        } catch (_: Exception) {
            uri
        }
        val statusCode = response.code
        val responseHeaders = response.headers
        response.close()
        if (DEBUG_TRACE) {
            val excerpt = if (statusCode in 200..299) ""
            else " — ${bodyBytes.decodeToString(throwOnInvalidSequence = false).take(BODY_EXCERPT)}"
            Log.d(TAG, "← $statusCode $method $uri$excerpt")
        }
        SolidRawResponse(statusCode, responseHeaders, bodyBytes, effectiveUri)
    }

    suspend fun <T : Resource> get(
        webId: String,
        uri: URI,
        clazz: Class<T>
    ): SolidResult<T> {
        return try {
            val accept =
                if (RDFResource::class.java.isAssignableFrom(clazz)) HTTPAcceptType.JSON_LD else HTTPAcceptType.ANY
            val response = readCached(webId, "GET", uri, accept, ttlFor(uri, clazz)) { cond ->
                executeAuthenticated("GET", webId, uri, accept = accept, additionalHeaders = cond)
            }
            if (response.isSuccessful()) {
                SolidResult.Success(SolidResourceParser.parse(response, clazz))
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun <T : Resource> put(
        webId: String,
        resource: T,
        ifMatch: String? = null,
        ifUnmodifiedSince: String? = null,
        ifNoneMatchStar: Boolean = false,
    ): SolidResult<T> {
        return try {
            val linkType = when {
                SolidContainer::class.java.isAssignableFrom(resource.javaClass) -> "<${LDP.BASIC_CONTAINER}>; rel=\"type\""
                RDFResource::class.java.isAssignableFrom(resource.javaClass) -> "<${LDP.RDF_SOURCE}>; rel=\"type\""
                else -> "<${LDP.NON_RDF_SOURCE}>; rel=\"type\""
            }
            val bodyBytes = resource.getEntity().readBytes()
            val response = executeAuthenticated(
                method = "PUT",
                webId = webId,
                uri = resource.getIdentifier(),
                contentType = resource.getContentType(),
                accept = resource.getContentType(),
                linkHeader = linkType,
                body = bodyBytes,
                ifMatch = ifMatch,
                ifUnmodifiedSince = ifUnmodifiedSince,
                ifNoneMatchStar = ifNoneMatchStar,
            )
            if (response.isSuccessful()) {
                invalidate(resource.getIdentifier())
                SolidResult.Success(resource)
            } else {
                if (response.statusCode == 412) invalidate(resource.getIdentifier())
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun patch(
        webId: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String? = null
    ): SolidResult<Unit> {
        return try {
            var response = executeAuthenticated(
                method = "PATCH",
                webId = webId,
                uri = uri,
                contentType = HTTPAcceptType.SPARQL_UPDATE,
                body = patch.toSparqlUpdate().toByteArray(Charsets.UTF_8),
                ifMatch = ifMatch,
            )
            if (response.statusCode == HTTP_UNSUPPORTED_MEDIA_TYPE) {
                // The Solid Protocol mandates text/n3 PATCH support, but some servers accept
                // only one of the two formats. We prefer the more widely accepted sparql-update
                // and fall back to the spec-required text/n3 when the server 415s it.
                response = executeAuthenticated(
                    method = "PATCH",
                    webId = webId,
                    uri = uri,
                    contentType = HTTPAcceptType.N3,
                    body = patch.toN3String().toByteArray(Charsets.UTF_8),
                    ifMatch = ifMatch,
                )
            }
            if (response.isSuccessful()) {
                invalidate(uri)
                SolidResult.Success(Unit)
            } else {
                if (response.statusCode == 412) invalidate(uri)
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun patchRaw(
        webId: String,
        uri: URI,
        n3Body: String,
        ifMatch: String? = null
    ): SolidResult<Unit> {
        return try {
            val response = executeAuthenticated(
                method = "PATCH",
                webId = webId,
                uri = uri,
                contentType = HTTPAcceptType.N3,
                body = n3Body.toByteArray(Charsets.UTF_8),
                ifMatch = ifMatch,
            )
            if (response.isSuccessful()) {
                invalidate(uri)
                SolidResult.Success(Unit)
            } else {
                if (response.statusCode == 412) invalidate(uri)
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun head(webId: String, uri: URI): SolidResult<SolidMetadata> {
        return try {
            val response = readCached(
                webId,
                "HEAD",
                uri,
                accept = null,
                ttlMillis = ttlFor(uri, null)
            ) { cond ->
                executeAuthenticated("HEAD", webId, uri, additionalHeaders = cond)
            }
            if (response.isSuccessful()) {
                SolidResult.Success(SolidMetadata.from(SolidHeaders(response.headers.toMultimap())))
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun <T : Resource> getPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidResult<T> {
        return try {
            val accept =
                if (RDFResource::class.java.isAssignableFrom(clazz)) HTTPAcceptType.JSON_LD else HTTPAcceptType.ANY
            val response = readCached(
                SolidResponseCache.PUBLIC_PRINCIPAL, "GET", uri, accept, ttlFor(uri, clazz)
            ) { cond ->
                send(method = "GET", uri = uri, accept = accept, headers = cond)
            }
            if (response.isSuccessful()) {
                SolidResult.Success(SolidResourceParser.parse(response, clazz))
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun headPublic(uri: URI): SolidResult<SolidMetadata> {
        return try {
            val response = readCached(
                SolidResponseCache.PUBLIC_PRINCIPAL,
                "HEAD",
                uri,
                accept = null,
                ttlMillis = ttlFor(uri, null)
            ) { cond ->
                send(method = "HEAD", uri = uri, headers = cond)
            }
            if (response.isSuccessful()) {
                SolidResult.Success(SolidMetadata.from(SolidHeaders(response.headers.toMultimap())))
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun putRaw(
        webId: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String? = null,
        linkHeader: String? = null,
    ): SolidResult<Unit> {
        return try {
            val response = executeAuthenticated(
                method = "PUT",
                webId = webId,
                uri = uri,
                contentType = contentType,
                accept = contentType,
                linkHeader = linkHeader,
                body = body,
                ifMatch = ifMatch,
            )
            if (response.isSuccessful()) {
                invalidate(uri)
                SolidResult.Success(Unit)
            } else {
                if (response.statusCode == 412) invalidate(uri)
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun post(
        webId: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): SolidResult<URI?> {
        return try {
            val response = executeAuthenticated(
                method = "POST",
                webId = webId,
                uri = uri,
                contentType = contentType,
                body = body,
                additionalHeaders = additionalHeaders,
            )
            if (response.isSuccessful()) {
                invalidate(uri)
                val location = response.headers[HTTPHeaderName.LOCATION]
                    ?.let { runCatching { URI.create(it) }.getOrNull() }
                SolidResult.Success(location)
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun <T : Resource> postResource(
        webId: String,
        containerUri: URI,
        resource: T,
    ): SolidResult<URI?> {
        return try {
            val linkType = when {
                SolidContainer::class.java.isAssignableFrom(resource.javaClass) -> "<${LDP.BASIC_CONTAINER}>; rel=\"type\""
                RDFResource::class.java.isAssignableFrom(resource.javaClass) -> "<${LDP.RDF_SOURCE}>; rel=\"type\""
                else -> "<${LDP.NON_RDF_SOURCE}>; rel=\"type\""
            }
            val headers = buildMap {
                put(HTTPHeaderName.LINK, linkType)
                slugFrom(resource.getIdentifier())?.let { put("Slug", it) }
            }
            val response = executeAuthenticated(
                method = "POST",
                webId = webId,
                uri = containerUri,
                contentType = resource.getContentType(),
                body = resource.getEntity().readBytes(),
                additionalHeaders = headers,
            )
            if (response.isSuccessful()) {
                invalidate(containerUri)
                val location = response.headers[HTTPHeaderName.LOCATION]
                    ?.let { runCatching { URI.create(it) }.getOrNull() }
                SolidResult.Success(location)
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    private fun slugFrom(identifier: URI): String? {
        val path = identifier.rawPath?.trimEnd('/') ?: return null
        val segment = path.substringAfterLast('/').ifBlank { return null }
        return runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
    }

    suspend fun delete(
        webId: String,
        uri: URI,
        ifMatch: String? = null
    ): SolidResult<Boolean> {
        return try {
            val response = executeAuthenticated("DELETE", webId, uri, ifMatch = ifMatch)
            if (response.isSuccessful()) {
                invalidate(uri)
                SolidResult.Success(true)
            } else {
                if (response.statusCode == 412) invalidate(uri)
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun copy(
        webId: String,
        sourceUri: URI,
        destinationUri: URI,
    ): SolidResult<Boolean> {
        return try {
            val response = executeAuthenticated(
                method = "COPY",
                webId = webId,
                uri = sourceUri,
                additionalHeaders = mapOf("Destination" to destinationUri.toString()),
            )
            if (response.isSuccessful()) {
                invalidate(destinationUri)
                SolidResult.Success(true)
            } else {
                SolidResult.Failure(SolidError.fromHttp(response.statusCode, response.errorDetail()))
            }
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    private suspend fun readCached(
        principal: String,
        method: String,
        uri: URI,
        accept: String?,
        ttlMillis: Long,
        fetch: suspend (conditionalHeaders: Map<String, String>) -> SolidRawResponse,
    ): SolidRawResponse {
        if (!cacheEnabled) return fetch(emptyMap())
        val key = SolidResponseCache.Key(principal, method, uri.toString(), accept ?: "")
        return cache.cachedRead(key, ttlMillis, uri, fetch)
    }

    private fun ttlFor(uri: URI, clazz: Class<*>?): Long {
        if (clazz != null && WebId::class.java.isAssignableFrom(clazz)) return TTL_STABLE_MS
        val path = uri.path.orEmpty()
        return if (path.endsWith(".acl") || path.endsWith(".acr")) TTL_STABLE_MS else TTL_DATA_MS
    }

    private fun invalidate(uri: URI) {
        if (cacheEnabled) cache.invalidateWithParent(uri.toString())
    }

    /** Maps a caught throwable to a [SolidResult.Failure], rethrowing coroutine cancellation. */
    private fun <T> solidFailure(e: Throwable): SolidResult<T> {
        if (e is kotlinx.coroutines.CancellationException) throw e
        return SolidResult.Failure(SolidError.fromThrowable(e))
    }

    private suspend fun executeAuthenticated(
        method: String,
        webId: String,
        uri: URI,
        contentType: String? = null,
        accept: String? = null,
        linkHeader: String? = null,
        body: ByteArray? = null,
        ifMatch: String? = null,
        ifUnmodifiedSince: String? = null,
        ifNoneMatchStar: Boolean = false,
        additionalHeaders: Map<String, String> = emptyMap(),
    ): SolidRawResponse {
        var currentMethod = method
        var currentUri = uri
        var currentBody = body
        var hops = 0
        while (true) {
            // Credentials (and their DPoP proof, bound to the target's htu) are re-attached
            // only for a same-origin hop, so a redirect can't forward the token to a foreign host.
            val attachAuth = sameOrigin(uri, currentUri)
            val response = sendWithAuthRetry(
                currentMethod, webId, currentUri, contentType, accept, linkHeader,
                currentBody, ifMatch, ifUnmodifiedSince, ifNoneMatchStar, additionalHeaders,
                attachAuth,
            )

            val target = redirectTarget(response, currentUri)
            if (target == null || hops++ >= MAX_REDIRECTS) return response

            if (response.statusCode == HTTP_SEE_OTHER && currentMethod != "GET" && currentMethod != "HEAD") {
                currentMethod = "GET"
                currentBody = null
            }
            currentUri = target
        }
    }

    /**
     * Sends one request to a fixed [uri], applying the DPoP-nonce retry and the single
     * expired-token force-refresh. [attachAuth] is `false` for a cross-origin redirect
     * hop — no credentials are sent and the response is returned as-is (no auth retry).
     */
    private suspend fun sendWithAuthRetry(
        method: String,
        webId: String,
        uri: URI,
        contentType: String?,
        accept: String?,
        linkHeader: String?,
        body: ByteArray?,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
        ifNoneMatchStar: Boolean,
        additionalHeaders: Map<String, String>,
        attachAuth: Boolean,
    ): SolidRawResponse {
        var lastResponse: SolidRawResponse? = null
        var didForceRefresh = false

        repeat(MAX_AUTH_ATTEMPTS) {
            val attemptHeaders = buildMap {
                if (attachAuth) putAll(buildAuthHeaders(webId, method, uri.toString()))
                putAll(additionalHeaders)
                if (ifMatch != null) put(HTTPHeaderName.IF_MATCH, if (ifMatch == "*") "*" else "\"$ifMatch\"")
                if (ifMatch == null && ifUnmodifiedSince != null) {
                    put(HTTPHeaderName.IF_UNMODIFIED_SINCE, ifUnmodifiedSince)
                }
                if (ifNoneMatchStar) put(HTTPHeaderName.IF_NONE_MATCH, "*")
            }

            val response = send(method, uri, contentType, accept, linkHeader, body, attemptHeaders)
            if (attachAuth) {
                response.headers[HTTPHeaderName.DPOP_NONCE]?.let { nonce ->
                    requireAuth().updateDPoPNonce(webId, uri.toString(), nonce)
                }
            }
            lastResponse = response

            if (response.statusCode != 401 || !attachAuth) return response

            val wwwAuth = response.headers[HTTPHeaderName.WWW_AUTHENTICATE] ?: ""
            val isPureNonceChallenge = wwwAuth.contains("use_dpop_nonce", ignoreCase = true) &&
                    !wwwAuth.contains("invalid_token", ignoreCase = true) &&
                    !wwwAuth.contains("expired_token", ignoreCase = true)

            if (isPureNonceChallenge) {
                return@repeat
            }
            if (didForceRefresh) {
                return response
            }
            requireAuth().getLastTokenResponse(webId, forceRefresh = true)
            didForceRefresh = true
        }
        return lastResponse!!
    }

    /**
     * The redirect target for a `3xx` [response] with a `Location`, resolved against
     * [from] (so a relative `Location` works), or `null` when this is not a redirect
     * the client follows.
     */
    private fun redirectTarget(response: SolidRawResponse, from: URI): URI? {
        if (response.statusCode !in REDIRECT_CODES) return null
        val location = response.headers[HTTPHeaderName.LOCATION]?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { from.resolve(location) }.getOrNull()
    }

    private fun sameOrigin(a: URI, b: URI): Boolean =
        a.scheme.equals(b.scheme, ignoreCase = true) &&
                a.authority.equals(b.authority, ignoreCase = true)

    internal companion object {
        const val MAX_AUTH_ATTEMPTS = 3

        /** HTTP 415 — the server rejected the request body's media type (drives the PATCH format fallback). */
        const val HTTP_UNSUPPORTED_MEDIA_TYPE = 415

        /** HTTP 303 — a redirect that turns the follow-up request into a `GET`. */
        const val HTTP_SEE_OTHER = 303

        /** Redirect status codes this client follows manually (re-signing DPoP per hop). */
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        /** Cap on redirect hops before giving up and returning the last `3xx` response. */
        const val MAX_REDIRECTS = 5

        /** Freshness window for ordinary data resources — served from memory without a network call. */
        const val TTL_DATA_MS = 5_000L

        /** Longer freshness window for rarely-changing documents (WebID profiles, ACL/ACR resources). */
        const val TTL_STABLE_MS = 60_000L

        /**
         * Master switch for the in-memory [SolidResponseCache]. On by default; flip off
         * (in code or via reflection in a test) to force every read back to the network.
         */
        @JvmStatic
        var cacheEnabled: Boolean = true

        /** Tag used by the optional HTTP-trace log emitted when [DEBUG_TRACE] is on. */
        const val TAG = "SolidHttp"

        /** Max characters of the response body to include in error log lines. */
        const val BODY_EXCERPT = 512

        /** When `true`, logs every HTTP request/response pair. Off by default. */
        @JvmStatic
        var DEBUG_TRACE: Boolean = false
    }

    private suspend fun buildAuthHeaders(
        webId: String,
        method: String,
        uri: String
    ): Map<String, String> {
        val authenticator = requireAuth()
        authenticator.getLastTokenResponse(webId)
            ?: throw IllegalArgumentException("Not authenticated. Complete login before accessing Solid resources.")
        return authenticator.getAuthHeaders(webId, method, uri)
    }

    private fun requireAuth(): AuthSession =
        auth ?: throw IllegalStateException(
            "An authenticated session is required for CRUD operations. " +
                    "Construct SolidHttpClient with an AuthSession instance."
        )
}
