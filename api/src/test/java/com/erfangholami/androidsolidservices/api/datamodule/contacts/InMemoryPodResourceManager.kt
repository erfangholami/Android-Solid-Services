package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
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
        webId: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidResult<T> =
        store[resource.toString()]
            ?.let { SolidResult.Success(it as T) }
            ?: SolidResult.Failure(SolidError.fromHttp(404, "not found: $resource"))

    override suspend fun <T : Resource> create(
        webId: String,
        resource: T,
    ): SolidResult<T> {
        put(resource)
        return SolidResult.Success(resource)
    }

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> {
        put(newResource)
        return SolidResult.Success(newResource)
    }

    override suspend fun delete(
        webId: String,
        resourceUri: URI,
        ifMatch: String?,
    ): SolidResult<Boolean> {
        val uri = resourceUri.toString()
        if (uri in failDeletesFor) {
            return SolidResult.Failure(SolidError.fromHttp(409, "delete failed (test): $uri"))
        }
        deletedUris.add(uri)
        if (uri.endsWith("/")) {
            store.keys.filter { it.startsWith(uri) }.forEach { store.remove(it) }
        } else {
            store.remove(uri)
        }
        return SolidResult.Success(true)
    }

    override suspend fun <T : Resource> delete(
        webId: String,
        resource: T,
    ): SolidResult<T> {
        delete(webId, resource.getIdentifier())
        return SolidResult.Success(resource)
    }

    override suspend fun putRaw(
        webId: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> {
        rawPuts[uri.toString()] = body
        return SolidResult.Success(Unit)
    }

    override suspend fun head(webId: String, uri: URI): SolidResult<SolidMetadata> =
        if (store.containsKey(uri.toString())) {
            SolidResult.Success(SolidMetadata.EMPTY)
        } else {
            SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
        }

    override suspend fun headPublic(uri: URI): SolidResult<SolidMetadata> =
        SolidResult.Success(SolidMetadata.EMPTY)

    override suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidResult<T> = read("", uri, clazz)

    override suspend fun patch(
        webId: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun patchRaw(
        webId: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun post(
        webId: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<URI?> = notImplemented()

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: URI,
        resource: T,
    ): SolidResult<URI?> = notImplemented()

    private fun <T> notImplemented(): SolidResult<T> =
        SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised by this test")))
}
