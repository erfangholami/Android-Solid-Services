package com.erfangholami.androidsolidservices.api.resource.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.auth.implementation.asSession
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI

internal class SolidResourceManagerImplementation : SolidResourceManager {

    companion object {
        private const val RESOURCE_LOG_TAG = "SolidResourceManager"

        private const val MAX_CONCURRENT_DELETES = 6
        private const val MAX_DELETE_ATTEMPTS = 4
        private const val DELETE_RETRY_BASE_DELAY_MS = 500L
        private val TRANSIENT_DELETE_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)

        @Volatile
        private var INSTANCE: SolidResourceManager? = null

        internal fun getInstance(authenticator: Authenticator): SolidResourceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidResourceManagerImplementation(authenticator).also { INSTANCE = it }
            }
        }

        internal fun setHttpTrace(enabled: Boolean) {
            SolidHttpClient.DEBUG_TRACE = enabled
        }
    }

    private val solidHttpClient: SolidHttpClient

    private constructor(authenticator: Authenticator) {
        solidHttpClient = SolidHttpClient(authenticator.asSession())
    }

    override suspend fun head(
        webid: String,
        uri: URI,
    ): SolidNetworkResponse<SolidMetadata> = withContext(Dispatchers.IO) {
        solidHttpClient.head(webid, uri)
    }

    override suspend fun <T : Resource> read(
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = withContext(Dispatchers.IO) {
        val result = solidHttpClient.get(webid, resource, clazz)
        if (result is SolidNetworkResponse.Success && result.data is SolidContainer) {
            val container = result.data as SolidContainer
            val enriched = coroutineScope {
                container.getContained().map { ref ->
                    async {
                        when (val headResult =
                            solidHttpClient.head(webid, URI.create(ref.identifier))) {
                            is SolidNetworkResponse.Success -> ref.copy(headMetadata = headResult.data)
                            is SolidNetworkResponse.Error -> {
                                Log.w(
                                    RESOURCE_LOG_TAG,
                                    "read: HEAD failed for ${ref.identifier} " +
                                            "(${headResult.errorCode}: ${headResult.errorMessage}); " +
                                            "returning ref without metadata.",
                                )
                                ref
                            }

                            is SolidNetworkResponse.Exception -> {
                                Log.w(
                                    RESOURCE_LOG_TAG,
                                    "read: HEAD threw for ${ref.identifier}; " +
                                            "returning ref without metadata.",
                                    headResult.exception,
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
        webid: String,
        resource: T,
    ): SolidNetworkResponse<T> = withContext(Dispatchers.IO) {
        try {
            val response = solidHttpClient.put(webid, resource, ifNoneMatchStar = true)
            if (response is SolidNetworkResponse.Error && response.errorCode == 412) {
                SolidNetworkResponse.Error(409, "Resource already exists")
            } else {
                response
            }
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String?,
    ): SolidNetworkResponse<T> = withContext(Dispatchers.IO) {
        try {
            solidHttpClient.put(webid, newResource, ifMatch = ifMatch)
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun patch(
        webid: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> = withContext(Dispatchers.IO) {
        solidHttpClient.patch(webid, uri, patch, ifMatch)
    }

    override suspend fun patchRaw(
        webid: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> = withContext(Dispatchers.IO) {
        solidHttpClient.patchRaw(webid, uri, n3Body, ifMatch)
    }

    override suspend fun <T : Resource> delete(
        webid: String,
        resource: T,
    ): SolidNetworkResponse<T> = withContext(Dispatchers.IO) {
        try {
            val uri = resource.getIdentifier()
            val deleteResult = if (resource is SolidContainer || uri.toString().endsWith("/")) {
                deleteRecursive(webid, uri, Semaphore(MAX_CONCURRENT_DELETES))
            } else {
                solidHttpClient.delete(webid, uri)
            }
            when (deleteResult) {
                is SolidNetworkResponse.Success -> SolidNetworkResponse.Success(resource)
                is SolidNetworkResponse.Error -> SolidNetworkResponse.Error(
                    deleteResult.errorCode,
                    deleteResult.errorMessage
                )

                is SolidNetworkResponse.Exception -> SolidNetworkResponse.Exception(deleteResult.exception)
            }
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun delete(
        webid: String,
        resourceUri: URI,
    ): SolidNetworkResponse<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (resourceUri.toString().endsWith("/")) {
                deleteRecursive(webid, resourceUri, Semaphore(MAX_CONCURRENT_DELETES))
            } else {
                solidHttpClient.delete(webid, resourceUri)
            }
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidNetworkResponse<URI?> = withContext(Dispatchers.IO) {
        solidHttpClient.post(webid, uri, contentType, body, additionalHeaders)
    }

    override suspend fun <T : Resource> createInContainer(
        webid: String,
        containerUri: URI,
        resource: T,
    ): SolidNetworkResponse<URI?> = withContext(Dispatchers.IO) {
        try {
            solidHttpClient.postResource(webid, containerUri, resource)
        } catch (e: Exception) {
            SolidNetworkResponse.Exception(e)
        }
    }

    override suspend fun putRaw(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidNetworkResponse<Unit> = withContext(Dispatchers.IO) {
        solidHttpClient.putRaw(webid, uri, contentType, body, ifMatch, linkHeader)
    }

    override suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = withContext(Dispatchers.IO) {
        solidHttpClient.getPublic(uri, clazz)
    }

    override suspend fun headPublic(uri: URI): SolidNetworkResponse<SolidMetadata> =
        withContext(Dispatchers.IO) { solidHttpClient.headPublic(uri) }

    /**
     * Deletes a container and everything under it.
     *
     * Solid has no single-call recursive delete: a `DELETE` on a non-empty container returns `409`
     * (Solid Protocol §5.4), so the tree must be emptied leaf-first, client-side. This empties the
     * container by deleting its contained resources — recursing into child containers — before
     * deleting the container itself.
     *
     * Deletes are bounded to [MAX_CONCURRENT_DELETES] in-flight requests via [gate] (a single
     * shared permit budget for the whole tree) so a large container cannot flood the server with
     * hundreds of simultaneous requests. A failed child does **not** cancel its siblings: every
     * child is attempted, transient failures are retried ([deleteWithRetry]), and if any resource
     * still cannot be deleted the container is left intact and an aggregate [SolidNetworkResponse.Error]
     * is returned — the caller can safely retry (already-gone resources report `404`, treated as
     * success) without leaving the container half-emptied yet deregistered.
     */
    private suspend fun deleteRecursive(
        webid: String,
        containerUri: URI,
        gate: Semaphore,
    ): SolidNetworkResponse<Boolean> {
        val containerResult = solidHttpClient.get(webid, containerUri, SolidContainer::class.java)
        if (containerResult !is SolidNetworkResponse.Success) {
            return when (containerResult) {
                is SolidNetworkResponse.Error ->
                    if (containerResult.errorCode == 404) {
                        SolidNetworkResponse.Success(true)
                    } else {
                        SolidNetworkResponse.Error(containerResult.errorCode, containerResult.errorMessage)
                    }

                is SolidNetworkResponse.Exception -> SolidNetworkResponse.Exception(containerResult.exception)
            }
        }

        val failures = coroutineScope {
            containerResult.data.getContained().map { ref ->
                async {
                    val childUri = URI.create(ref.identifier)
                    val isChildContainer = ref.isContainerByUri() ||
                            ref.types.contains(LDP.BASIC_CONTAINER) ||
                            ref.types.contains(LDP.CONTAINER) ||
                            ref.types.contains(LDP.DIRECT_CONTAINER) ||
                            ref.types.contains(LDP.INDIRECT_CONTAINER)
                    val childResult = if (isChildContainer) {
                        deleteRecursive(webid, childUri, gate)
                    } else {
                        deleteWithRetry(webid, childUri, gate)
                    }
                    if (childResult is SolidNetworkResponse.Success) null else childUri.toString()
                }
            }.awaitAll().filterNotNull()
        }

        if (failures.isNotEmpty()) {
            return SolidNetworkResponse.Error(
                409,
                "Could not delete ${failures.size} contained resource(s) under $containerUri; " +
                        "container left intact",
            )
        }

        return deleteWithRetry(webid, containerUri, gate)
    }

    /**
     * Deletes a single resource, holding a [gate] permit for the request and retrying transient
     * failures (network errors and [TRANSIENT_DELETE_STATUS_CODES] responses) with exponential
     * backoff, up to [MAX_DELETE_ATTEMPTS] attempts. A `404` is treated as success (the resource is
     * already gone), which makes a re-run of a partially-completed delete idempotent.
     */
    private suspend fun deleteWithRetry(
        webid: String,
        uri: URI,
        gate: Semaphore,
    ): SolidNetworkResponse<Boolean> {
        var attempt = 0
        while (true) {
            val result = gate.withPermit { solidHttpClient.delete(webid, uri) }
            when {
                result is SolidNetworkResponse.Success -> return result

                result is SolidNetworkResponse.Error && result.errorCode == 404 ->
                    return SolidNetworkResponse.Success(true)

                attempt < MAX_DELETE_ATTEMPTS - 1 && result.isTransientFailure() -> {
                    delay(DELETE_RETRY_BASE_DELAY_MS shl attempt)
                    attempt++
                }

                else -> return result
            }
        }
    }

    private fun SolidNetworkResponse<Boolean>.isTransientFailure(): Boolean = when (this) {
        is SolidNetworkResponse.Exception -> true
        is SolidNetworkResponse.Error -> errorCode in TRANSIENT_DELETE_STATUS_CODES
        is SolidNetworkResponse.Success -> false
    }
}
