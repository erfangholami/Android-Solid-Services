package com.erfangholami.androidsolidservices.api.transport

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.SolidSession
import com.erfangholami.androidsolidservices.api.resource.StreamingResource
import com.erfangholami.androidsolidservices.api.resource.implementation.N3PatchConverter
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidResourceParser
import com.erfangholami.androidsolidservices.api.transport.SolidHttpClient.Companion.debugTrace
import com.erfangholami.androidsolidservices.api.transport.SolidRawResponse
import com.erfangholami.androidsolidservices.api.transport.telemetryOrigin
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import com.erfangholami.androidsolidservices.shared.util.encodeUri
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.net.URI

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

internal class SolidHttpClient(
    private val auth: SolidSession? = null,
    httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val httpClient: OkHttpClient =
        httpClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

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
        if (debugTrace) Log.d(TAG, "→ $method $uri")
        val mediaType = contentType?.toMediaTypeOrNull()
        val requestBody: RequestBody? = when {
            body != null -> body.toRequestBody(mediaType)
            method in setOf("POST", "PUT", "PATCH") -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val request = Request
            .Builder()
            .url(encodeUri(uri).toString())
            .apply {
                if (accept != null) header(HTTPHeaderName.ACCEPT, accept)
                if (contentType != null) header(HTTPHeaderName.CONTENT_TYPE, contentType)
                if (linkHeader != null) header(HTTPHeaderName.LINK, linkHeader)
                headers.forEach { (k, v) -> addHeader(k, v) }
                method(method, requestBody)
            }.build()

        val span = Telemetry.startNetworkSpan(uri.telemetryOrigin(), method)
        requestBody?.contentLength()?.takeIf { it >= 0 }?.let(span::setRequestPayloadSize)
        try {
            val response = httpClient.newCall(request).execute()
            val statusCode = response.code
            val bodyBytes = if (method == "HEAD" || statusCode in BODILESS_STATUSES) {
                ByteArray(0)
            } else {
                response.body?.bytes() ?: ByteArray(0)
            }
            val effectiveUri = try {
                response.request.url.toUri()
            } catch (_: Exception) {
                uri
            }
            val responseHeaders = response.headers
            response.close()
            span.setResponseCode(statusCode)
            span.setResponsePayloadSize(bodyBytes.size.toLong())
            span.setResponseContentType(responseHeaders[HTTPHeaderName.CONTENT_TYPE])
            if (debugTrace) {
                val excerpt = if (statusCode in 200..299) {
                    ""
                } else {
                    " — ${bodyBytes.decodeToString(throwOnInvalidSequence = false).take(BODY_EXCERPT)}"
                }
                Log.d(TAG, "← $statusCode $method $uri$excerpt")
            }
            SolidRawResponse(statusCode, responseHeaders, bodyBytes, effectiveUri)
        } finally {
            span.stop()
        }
    }

    private suspend fun sendPublicFollowingRedirects(
        method: String,
        uri: URI,
        accept: String?,
        headers: Map<String, String>,
    ): SolidRawResponse {
        var currentMethod = method
        var currentUri = uri
        var hops = 0
        while (true) {
            val response = send(method = currentMethod, uri = currentUri, accept = accept, headers = headers)
            val target = redirectTarget(response, currentUri)
            if (target == null || hops++ >= MAX_REDIRECTS) return response
            if (response.statusCode == HTTP_SEE_OTHER && currentMethod != "GET" && currentMethod != "HEAD") {
                currentMethod = "GET"
            }
            currentUri = target
        }
    }

    suspend fun <T : Resource> get(
        webId: String,
        uri: URI,
        clazz: Class<T>,
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
                uri = encodeUriString(resource.getIdentifier()),
                contentType = resource.getContentType(),
                accept = resource.getContentType(),
                linkHeader = linkType,
                body = bodyBytes,
                ifMatch = ifMatch,
                ifUnmodifiedSince = ifUnmodifiedSince,
                ifNoneMatchStar = ifNoneMatchStar,
            )
            if (response.isSuccessful()) {
                invalidate(encodeUriString(resource.getIdentifier()))
                SolidResult.Success(resource)
            } else {
                if (response.statusCode == 412) invalidate(encodeUriString(resource.getIdentifier()))
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
        ifMatch: String? = null,
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
        ifMatch: String? = null,
    ): SolidResult<Unit> {
        return try {
            var response = executeAuthenticated(
                method = "PATCH",
                webId = webId,
                uri = uri,
                contentType = HTTPAcceptType.N3,
                body = n3Body.toByteArray(Charsets.UTF_8),
                ifMatch = ifMatch,
            )
            if (response.statusCode == HTTP_UNSUPPORTED_MEDIA_TYPE) {
                val sparql = N3PatchConverter.toSparqlUpdate(n3Body)
                if (sparql != null) {
                    response = executeAuthenticated(
                        method = "PATCH",
                        webId = webId,
                        uri = uri,
                        contentType = HTTPAcceptType.SPARQL_UPDATE,
                        body = sparql.toByteArray(Charsets.UTF_8),
                        ifMatch = ifMatch,
                    )
                }
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

    suspend fun head(
        webId: String,
        uri: URI,
    ): SolidResult<SolidMetadata> {
        return try {
            val response = readCached(
                webId,
                "HEAD",
                uri,
                accept = null,
                ttlMillis = ttlFor(uri, null),
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
                SolidResponseCache.PUBLIC_PRINCIPAL,
                "GET",
                uri,
                accept,
                ttlFor(uri, clazz),
            ) { cond ->
                sendPublicFollowingRedirects(method = "GET", uri = uri, accept = accept, headers = cond)
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
                ttlMillis = ttlFor(uri, null),
            ) { cond ->
                sendPublicFollowingRedirects(method = "HEAD", uri = uri, accept = null, headers = cond)
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
                slugFrom(encodeUriString(resource.getIdentifier()))?.let { put("Slug", it) }
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
        ifMatch: String? = null,
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

    private fun ttlFor(
        uri: URI,
        clazz: Class<*>?,
    ): Long {
        if (clazz != null && WebId::class.java.isAssignableFrom(clazz)) return TTL_STABLE_MS
        val path = uri.path.orEmpty()
        return if (path.endsWith(".acl") || path.endsWith(".acr")) TTL_STABLE_MS else TTL_DATA_MS
    }

    private fun invalidate(uri: URI) {
        if (cacheEnabled) cache.invalidateWithParent(uri.toString())
    }

    suspend fun getStream(
        webId: String,
        uri: URI,
    ): SolidResult<StreamingResource> = withContext(Dispatchers.IO) {
        try {
            var currentUri = uri
            var didForceRefresh = false
            var lastCode = 0
            repeat(MAX_AUTH_ATTEMPTS + MAX_REDIRECTS) {
                val headers = buildAuthHeaders(webId, "GET", currentUri.toString())
                val request = Request
                    .Builder()
                    .url(encodeUri(currentUri).toString())
                    .apply {
                        headers.forEach { (k, v) -> addHeader(k, v) }
                        get()
                    }.build()
                val response = httpClient.newCall(request).execute()
                response.header(HTTPHeaderName.DPOP_NONCE)?.let { nonce ->
                    requireAuth().updateDPoPNonce(webId, currentUri.toString(), nonce)
                }
                lastCode = response.code
                when {
                    response.isSuccessful ->
                        return@withContext SolidResult.Success(streamingResourceFrom(currentUri, response))

                    response.code in REDIRECT_CODES -> {
                        val location = response.header(HTTPHeaderName.LOCATION)
                        response.close()
                        currentUri = location
                            ?.takeIf { it.isNotBlank() }
                            ?.let { runCatching { currentUri.resolve(it) }.getOrNull() }
                            ?: return@withContext SolidResult.Failure(
                                SolidError.fromHttp(response.code, "streaming GET: unusable redirect"),
                            )
                    }

                    response.code == 401 -> {
                        val wwwAuth = response.header(HTTPHeaderName.WWW_AUTHENTICATE) ?: ""
                        response.close()
                        when (afterUnauthorized(webId, wwwAuth, didForceRefresh)) {
                            UnauthorizedNext.GIVE_UP -> return@withContext SolidResult.Failure(SolidError.fromHttp(401))
                            UnauthorizedNext.RETRY_AFTER_REFRESH -> didForceRefresh = true
                            UnauthorizedNext.RETRY_WITH_NONCE -> Unit
                        }
                    }

                    else -> {
                        val detail = response.body?.string()?.take(BODY_EXCERPT)
                        response.close()
                        return@withContext SolidResult.Failure(SolidError.fromHttp(response.code, detail))
                    }
                }
            }
            SolidResult.Failure(SolidError.fromHttp(lastCode.takeIf { it != 0 } ?: 500, "streaming GET: retries exhausted"))
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    suspend fun putStream(
        webId: String,
        uri: URI,
        contentType: String,
        contentLength: Long?,
        ifMatch: String?,
        onProgress: ((Long, Long?) -> Unit)?,
        openSource: () -> InputStream,
    ): SolidResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestBody = StreamingRequestBody(contentType, contentLength ?: -1L, onProgress, openSource)
            var didForceRefresh = false
            var lastCode = 0
            repeat(MAX_AUTH_ATTEMPTS) {
                val headers = buildMap {
                    putAll(buildAuthHeaders(webId, "PUT", uri.toString()))
                    if (ifMatch != null) put(HTTPHeaderName.IF_MATCH, if (ifMatch == "*") "*" else "\"$ifMatch\"")
                }
                val request = Request
                    .Builder()
                    .url(encodeUri(uri).toString())
                    .apply {
                        headers.forEach { (k, v) -> addHeader(k, v) }
                        put(requestBody)
                    }.build()
                val response = httpClient.newCall(request).execute()
                val code = response.code
                val wwwAuth = response.header(HTTPHeaderName.WWW_AUTHENTICATE) ?: ""
                val nonce = response.header(HTTPHeaderName.DPOP_NONCE)
                val detail = if (code in 200..299) null else response.body?.string()?.take(BODY_EXCERPT)
                response.close()
                nonce?.let { requireAuth().updateDPoPNonce(webId, uri.toString(), it) }
                lastCode = code
                when {
                    code in 200..299 -> {
                        invalidate(uri)
                        return@withContext SolidResult.Success(Unit)
                    }

                    code == 412 -> {
                        invalidate(uri)
                        return@withContext SolidResult.Failure(SolidError.fromHttp(412, detail))
                    }

                    code == 401 -> when (afterUnauthorized(webId, wwwAuth, didForceRefresh)) {
                        UnauthorizedNext.GIVE_UP -> return@withContext SolidResult.Failure(SolidError.fromHttp(401, detail))
                        UnauthorizedNext.RETRY_AFTER_REFRESH -> didForceRefresh = true
                        UnauthorizedNext.RETRY_WITH_NONCE -> Unit
                    }

                    else -> return@withContext SolidResult.Failure(SolidError.fromHttp(code, detail))
                }
            }
            SolidResult.Failure(SolidError.fromHttp(lastCode.takeIf { it != 0 } ?: 500, "streaming PUT: retries exhausted"))
        } catch (e: Exception) {
            solidFailure(e)
        }
    }

    private enum class UnauthorizedNext { RETRY_WITH_NONCE, RETRY_AFTER_REFRESH, GIVE_UP }

    private suspend fun afterUnauthorized(
        webId: String,
        wwwAuth: String,
        didForceRefresh: Boolean,
    ): UnauthorizedNext {
        val isNonceChallenge = wwwAuth.contains("use_dpop_nonce", true) &&
            !wwwAuth.contains("invalid_token", true) &&
            !wwwAuth.contains("expired_token", true)
        return when {
            isNonceChallenge -> UnauthorizedNext.RETRY_WITH_NONCE
            didForceRefresh -> UnauthorizedNext.GIVE_UP
            requireAuth().hasValidToken(webId, forceRefresh = true) -> UnauthorizedNext.RETRY_AFTER_REFRESH
            else -> UnauthorizedNext.GIVE_UP
        }
    }

    private fun streamingResourceFrom(
        uri: URI,
        response: Response,
    ): StreamingResource {
        val body = response.body
            ?: run {
                response.close()
                return StreamingResource(uri.toString(), "application/octet-stream", 0L, ByteArray(0).inputStream()) {}
            }
        val contentType = body.contentType()?.toString()
            ?: response.header(HTTPHeaderName.CONTENT_TYPE)
            ?: "application/octet-stream"
        return StreamingResource(uri.toString(), contentType, body.contentLength(), body.byteStream()) { response.close() }
    }

    private fun <T> solidFailure(e: Throwable): SolidResult<T> {
        if (e is kotlinx.coroutines.CancellationException) throw e
        val error = SolidError.fromThrowable(e)
        when {
            e is IOException -> Telemetry.log("solid.http failed: ${e.javaClass.simpleName}: ${e.message}")
            error is SolidError.NotAuthenticated -> Telemetry.log("solid.http refused: no authorized session")
            else -> Telemetry.recordException(
                e,
                TelemetryAttribute.OPERATION to "solid.http",
                TelemetryAttribute.ERROR_TYPE to e.javaClass.simpleName,
            )
        }
        return SolidResult.Failure(error)
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
            val attachAuth = sameOrigin(uri, currentUri)
            val response = sendWithAuthRetry(
                currentMethod,
                webId,
                currentUri,
                contentType,
                accept,
                linkHeader,
                currentBody,
                ifMatch,
                ifUnmodifiedSince,
                ifNoneMatchStar,
                additionalHeaders,
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
            val challenge = AuthChallenge.parse(wwwAuth)

            if (challenge == AuthChallenge.NonceStale) {
                return@repeat
            }
            if (didForceRefresh) {
                return response
            }
            if (!challenge.warrantsTokenRefresh(isOwnOrigin(webId, uri))) {
                Telemetry.log(
                    "solid.auth 401 kept as authorization outcome " +
                        "(origin=${uri.scheme}://${uri.authority}, wwwAuth=${wwwAuth.take(60)})",
                )
                return response
            }
            Telemetry.log(
                "solid.auth 401 -> forced refresh (origin=${uri.scheme}://${uri.authority}, " +
                    "wwwAuth=${wwwAuth.take(60)})",
            )
            didForceRefresh = true
            if (!requireAuth().hasValidToken(webId, forceRefresh = true)) {
                Telemetry.log("solid.auth forced refresh failed; keeping the 401")
                return response
            }
        }
        return lastResponse!!
    }

    internal fun redirectTarget(
        response: SolidRawResponse,
        from: URI,
    ): URI? {
        if (response.statusCode !in REDIRECT_CODES) return null
        val location = response.headers[HTTPHeaderName.LOCATION]?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { from.resolve(location) }.getOrNull()
    }

    internal fun sameOrigin(
        a: URI,
        b: URI,
    ): Boolean = a.scheme.equals(b.scheme, ignoreCase = true) &&
        a.authority.equals(b.authority, ignoreCase = true)

    internal companion object {
        const val MAX_AUTH_ATTEMPTS = 3

        val BODILESS_STATUSES = setOf(204, 304)

        const val HTTP_UNSUPPORTED_MEDIA_TYPE = 415

        const val HTTP_SEE_OTHER = 303

        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        const val MAX_REDIRECTS = 5

        const val TTL_DATA_MS = 5_000L

        const val TTL_STABLE_MS = 60_000L

        @JvmStatic
        var cacheEnabled: Boolean = true

        const val TAG = "SolidHttp"

        const val BODY_EXCERPT = 512

        @JvmStatic
        var debugTrace: Boolean = false
    }

    private suspend fun buildAuthHeaders(
        webId: String,
        method: String,
        uri: String,
    ): Map<String, String> {
        val session = requireAuth()
        if (!session.hasValidToken(webId)) throw SolidError.NotAuthenticated().asException()
        return session.authHeaders(webId, method, uri)
    }

    private fun requireAuth(): SolidSession = auth ?: throw IllegalStateException(
        "An authenticated session is required for CRUD operations. " +
            "Construct SolidHttpClient with a SolidSession instance.",
    )
}

private class StreamingRequestBody(
    private val contentType: String,
    private val length: Long,
    private val onProgress: ((Long, Long?) -> Unit)?,
    private val openSource: () -> InputStream,
) : RequestBody() {
    override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = length

    override fun isOneShot(): Boolean = false

    override fun writeTo(sink: BufferedSink) {
        val total = length.takeIf { it >= 0 }
        openSource().use { input ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var written = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                written += read
                onProgress?.invoke(written, total)
            }
        }
    }

    private companion object {
        const val STREAM_BUFFER_SIZE = 8 * 1024
    }
}
