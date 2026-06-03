package com.erfangholami.androidsolidservices.api.resource.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URI

internal class SolidResourceManagerImplementation : SolidResourceManager {

    companion object {
        private const val RESOURCE_LOG_TAG = "SolidResourceManager"

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
        solidHttpClient = SolidHttpClient(authenticator)
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
                deleteRecursive(webid, uri)
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
                deleteRecursive(webid, resourceUri)
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

    private suspend fun deleteRecursive(
        webid: String,
        containerUri: URI
    ): SolidNetworkResponse<Boolean> {
        val containerResult = solidHttpClient.get(webid, containerUri, SolidContainer::class.java)
        if (containerResult !is SolidNetworkResponse.Success) {
            return when (containerResult) {
                is SolidNetworkResponse.Error -> SolidNetworkResponse.Error(
                    containerResult.errorCode,
                    containerResult.errorMessage
                )

                is SolidNetworkResponse.Exception -> SolidNetworkResponse.Exception(containerResult.exception)
            }
        }
        coroutineScope {
            containerResult.data.getContained().map { ref ->
                async {
                    val isChildContainer = ref.isContainerByUri() ||
                            ref.types.contains(LDP.BASIC_CONTAINER) ||
                            ref.types.contains(LDP.CONTAINER) ||
                            ref.types.contains(LDP.DIRECT_CONTAINER) ||
                            ref.types.contains(LDP.INDIRECT_CONTAINER)
                    if (isChildContainer) {
                        deleteRecursive(webid, URI.create(ref.identifier)).getOrThrow()
                    } else {
                        solidHttpClient.delete(webid, URI.create(ref.identifier)).getOrThrow()
                    }
                }
            }.awaitAll()
        }

        return solidHttpClient.delete(webid, containerUri)
    }
}
