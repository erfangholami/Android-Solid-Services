package com.erfangholami.androidsolidservices.api.resource.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.StreamingResource
import com.erfangholami.androidsolidservices.api.resource.implementation.SolidHttpClient
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URI

internal class SolidResourceManagerImplementation : SolidResourceManager {

    companion object {
        private const val RESOURCE_LOG_TAG = "SolidResourceManager"

        private const val MAX_CONCURRENT_DELETES = 6
        private const val MAX_DELETE_ATTEMPTS = 4
        private const val DELETE_RETRY_BASE_DELAY_MS = 500L
        private val TRANSIENT_DELETE_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)

        @Volatile
        private var instance: SolidResourceManager? = null

        internal fun getInstance(authenticator: Authenticator): SolidResourceManager {
            return instance ?: synchronized(this) {
                instance ?: SolidResourceManagerImplementation(authenticator).also { instance = it }
            }
        }

        internal fun setHttpTrace(enabled: Boolean) {
            SolidHttpClient.debugTrace = enabled
        }
    }

    private val solidHttpClient: SolidHttpClient

    private constructor(authenticator: Authenticator) {
        solidHttpClient = SolidHttpClient(authenticator)
    }

    override suspend fun head(
        webId: String,
        uri: String,
    ): SolidResult<SolidMetadata> = withContext(Dispatchers.IO) {
        solidHttpClient.head(webId, encodeUriString(uri))
    }

    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> = withContext(Dispatchers.IO) {
        val result = solidHttpClient.get(webId, encodeUriString(resource), clazz)
        if (result is SolidResult.Success && result.value is SolidContainer) {
            val container = result.value as SolidContainer
            val enriched = coroutineScope {
                container.getContained().map { ref ->
                    async {
                        when (val headResult =
                            solidHttpClient.head(webId, URI.create(ref.identifier))) {
                            is SolidResult.Success -> ref.copy(headMetadata = headResult.value)
                            is SolidResult.Failure -> {
                                Log.w(
                                    RESOURCE_LOG_TAG,
                                    "read: HEAD failed for ${ref.identifier} " +
                                            "(${headResult.error.code}: ${headResult.error.message}); " +
                                            "returning ref without metadata.",
                                    headResult.error.cause,
                                )
                                ref
                            }
                        }
                    }
                }.awaitAll()
            }
            container.enrichContained(enriched)
        }
        result
    }

    override suspend fun <T : Resource> create(
        webId: String,
        resource: T,
    ): SolidResult<T> = withContext(Dispatchers.IO) {
        try {
            val response = solidHttpClient.put(webId, resource, ifNoneMatchStar = true)
            if (response is SolidResult.Failure &&
                response.error.code == SolidErrorCode.PRECONDITION_FAILED
            ) {
                SolidResult.Failure(SolidError.fromHttp(409, "Resource already exists"))
            } else {
                response
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = withContext(Dispatchers.IO) {
        try {
            solidHttpClient.put(
                webId,
                newResource,
                ifMatch = ifMatch,
                ifUnmodifiedSince = ifUnmodifiedSince,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = withContext(Dispatchers.IO) {
        solidHttpClient.patch(webId, encodeUriString(uri), patch, ifMatch)
    }

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = withContext(Dispatchers.IO) {
        solidHttpClient.patchRaw(webId, encodeUriString(uri), n3Body, ifMatch)
    }

    override suspend fun <T : Resource> delete(
        webId: String,
        resource: T,
    ): SolidResult<T> = withContext(Dispatchers.IO) {
        try {
            val uri = encodeUriString(resource.getIdentifier())
            val deleteResult = if (resource is SolidContainer || uri.toString().endsWith("/")) {
                deleteRecursive(webId, uri, Semaphore(MAX_CONCURRENT_DELETES))
            } else {
                solidHttpClient.delete(webId, uri)
            }
            when (deleteResult) {
                is SolidResult.Success -> SolidResult.Success(resource)
                is SolidResult.Failure -> deleteResult
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun delete(
        webId: String,
        resourceUri: String,
        ifMatch: String?,
    ): SolidResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val uri = encodeUriString(resourceUri)
            if (uri.toString().endsWith("/")) {
                deleteRecursive(webId, uri, Semaphore(MAX_CONCURRENT_DELETES))
            } else {
                solidHttpClient.delete(webId, uri, ifMatch)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<String?> = withContext(Dispatchers.IO) {
        solidHttpClient.post(webId, encodeUriString(uri), contentType, body, additionalHeaders).map { it?.toString() }
    }

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?> = withContext(Dispatchers.IO) {
        try {
            solidHttpClient.postResource(webId, encodeUriString(containerUri), resource).map { it?.toString() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SolidResult.Failure(SolidError.fromThrowable(e))
        }
    }

    override suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> = withContext(Dispatchers.IO) {
        solidHttpClient.putRaw(webId, encodeUriString(uri), contentType, body, ifMatch, linkHeader)
    }

    override suspend fun readStream(webId: String, uri: String): SolidResult<StreamingResource> =
        solidHttpClient.getStream(webId, encodeUriString(uri))

    override suspend fun writeStream(
        webId: String,
        uri: String,
        contentType: String,
        contentLength: Long?,
        ifMatch: String?,
        onProgress: ((bytesWritten: Long, total: Long?) -> Unit)?,
        openSource: () -> InputStream,
    ): SolidResult<Unit> =
        solidHttpClient.putStream(webId, encodeUriString(uri), contentType, contentLength, ifMatch, onProgress, openSource)

    override suspend fun <T : Resource> readPublic(
        uri: String,
        clazz: Class<T>,
    ): SolidResult<T> = withContext(Dispatchers.IO) {
        solidHttpClient.getPublic(encodeUriString(uri), clazz)
    }

    override suspend fun headPublic(uri: String): SolidResult<SolidMetadata> =
        withContext(Dispatchers.IO) { solidHttpClient.headPublic(encodeUriString(uri)) }

    private suspend fun deleteRecursive(
        webId: String,
        containerUri: URI,
        gate: Semaphore,
    ): SolidResult<Boolean> {
        val containerResult = solidHttpClient.get(webId, containerUri, SolidContainer::class.java)
        val container = when (containerResult) {
            is SolidResult.Success -> containerResult.value
            is SolidResult.Failure ->
                return if (containerResult.error.code == SolidErrorCode.NOT_FOUND) {
                    SolidResult.Success(true)
                } else {
                    containerResult
                }
        }

        val failures = coroutineScope {
            container.getContained().map { ref ->
                async {
                    val childUri = URI.create(ref.identifier)
                    val isChildContainer = ref.isContainerByUri() ||
                            ref.types.contains(LDP.BASIC_CONTAINER) ||
                            ref.types.contains(LDP.CONTAINER) ||
                            ref.types.contains(LDP.DIRECT_CONTAINER) ||
                            ref.types.contains(LDP.INDIRECT_CONTAINER)
                    val childResult = if (isChildContainer) {
                        deleteRecursive(webId, childUri, gate)
                    } else {
                        deleteWithRetry(webId, childUri, gate)
                    }
                    if (childResult is SolidResult.Success) null else childUri.toString()
                }
            }.awaitAll().filterNotNull()
        }

        if (failures.isNotEmpty()) {
            return SolidResult.Failure(
                SolidError.fromHttp(
                    409,
                    "Could not delete ${failures.size} contained resource(s) under $containerUri; " +
                            "container left intact",
                )
            )
        }

        return deleteWithRetry(webId, containerUri, gate)
    }

    private suspend fun deleteWithRetry(
        webId: String,
        uri: URI,
        gate: Semaphore,
    ): SolidResult<Boolean> {
        var attempt = 0
        while (true) {
            val result = gate.withPermit { solidHttpClient.delete(webId, uri) }
            when {
                result is SolidResult.Success -> return result

                result is SolidResult.Failure && result.error.code == SolidErrorCode.NOT_FOUND ->
                    return SolidResult.Success(true)

                attempt < MAX_DELETE_ATTEMPTS - 1 && result.isTransientFailure() -> {
                    delay(DELETE_RETRY_BASE_DELAY_MS shl attempt)
                    attempt++
                }

                else -> return result
            }
        }
    }

    private fun SolidResult<Boolean>.isTransientFailure(): Boolean = when (this) {
        is SolidResult.Success -> false
        is SolidResult.Failure -> {
            val status = error.httpStatus
            status == null || status in TRANSIENT_DELETE_STATUS_CODES
        }
    }
}
