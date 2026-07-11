package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import java.net.URI

internal class InMemoryPodResourceManager : SolidResourceManager {

    val store: MutableMap<String, Resource> = mutableMapOf()
    val deletedUris: MutableList<String> = mutableListOf()
    val rawPuts: MutableMap<String, ByteArray> = mutableMapOf()
    val failDeletesFor: MutableSet<String> = mutableSetOf()

    fun put(resource: Resource) {
        store[resource.getIdentifier().toString()] = resource
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> =
        store[resource.toString()]
            ?.let { SolidNetworkResponse.Success(it as T) }
            ?: SolidNetworkResponse.Error(404, "not found: $resource")

    override suspend fun <T : Resource> create(
        webid: String,
        resource: T,
    ): SolidNetworkResponse<T> {
        put(resource)
        return SolidNetworkResponse.Success(resource)
    }

    override suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String?,
    ): SolidNetworkResponse<T> {
        put(newResource)
        return SolidNetworkResponse.Success(newResource)
    }

    override suspend fun delete(
        webid: String,
        resourceUri: URI,
    ): SolidNetworkResponse<Boolean> {
        val uri = resourceUri.toString()
        if (uri in failDeletesFor) {
            return SolidNetworkResponse.Error(409, "delete failed (test): $uri")
        }
        deletedUris.add(uri)
        if (uri.endsWith("/")) {
            store.keys.filter { it.startsWith(uri) }.forEach { store.remove(it) }
        } else {
            store.remove(uri)
        }
        return SolidNetworkResponse.Success(true)
    }

    override suspend fun <T : Resource> delete(
        webid: String,
        resource: T,
    ): SolidNetworkResponse<T> {
        delete(webid, resource.getIdentifier())
        return SolidNetworkResponse.Success(resource)
    }

    override suspend fun putRaw(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidNetworkResponse<Unit> {
        rawPuts[uri.toString()] = body
        return SolidNetworkResponse.Success(Unit)
    }

    override suspend fun head(webid: String, uri: URI): SolidNetworkResponse<SolidMetadata> =
        if (store.containsKey(uri.toString())) {
            SolidNetworkResponse.Success(SolidMetadata.EMPTY)
        } else {
            SolidNetworkResponse.Error(404, "not found: $uri")
        }

    override suspend fun headPublic(uri: URI): SolidNetworkResponse<SolidMetadata> =
        SolidNetworkResponse.Success(SolidMetadata.EMPTY)

    override suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = read("", uri, clazz)

    override suspend fun patch(
        webid: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> = notImplemented()

    override suspend fun patchRaw(
        webid: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> = notImplemented()

    override suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidNetworkResponse<URI?> = notImplemented()

    override suspend fun <T : Resource> createInContainer(
        webid: String,
        containerUri: URI,
        resource: T,
    ): SolidNetworkResponse<URI?> = notImplemented()

    private fun <T> notImplemented(): SolidNetworkResponse<T> =
        SolidNetworkResponse.Exception(NotImplementedError("not exercised by this test"))
}
