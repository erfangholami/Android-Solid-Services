package com.erfangholami.androidsolidservices.api.testing

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import java.util.UUID

internal class InMemoryPodResourceManager : SolidResourceManager {

    val store: MutableMap<String, Resource> = mutableMapOf()
    val deletedUris: MutableList<String> = mutableListOf()
    val rawPuts: MutableMap<String, ByteArray> = mutableMapOf()
    val patches: MutableList<Pair<String, String>> = mutableListOf()
    val failDeletesFor: MutableSet<String> = mutableSetOf()
    var conflictOnExistingCreate: Boolean = false

    fun put(resource: Resource) {
        store[resource.getIdentifier()] = resource
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> =
        store[resource]
            ?.let { SolidResult.Success(it as T) }
            ?: SolidResult.Failure(SolidError.fromHttp(404, "not found: $resource"))

    override suspend fun <T : Resource> create(
        webId: String,
        resource: T,
    ): SolidResult<T> {
        if (conflictOnExistingCreate && store.containsKey(resource.getIdentifier())) {
            return SolidResult.Failure(
                SolidError.fromHttp(409, "already exists: ${resource.getIdentifier()}"),
            )
        }
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
        resourceUri: String,
        ifMatch: String?,
    ): SolidResult<Boolean> {
        if (resourceUri in failDeletesFor) {
            return SolidResult.Failure(SolidError.fromHttp(409, "delete failed (test): $resourceUri"))
        }
        deletedUris.add(resourceUri)
        if (resourceUri.endsWith("/")) {
            store.keys.filter { it.startsWith(resourceUri) }.forEach { store.remove(it) }
            rawPuts.keys.filter { it.startsWith(resourceUri) }.toList().forEach { rawPuts.remove(it) }
        } else {
            store.remove(resourceUri)
            rawPuts.remove(resourceUri)
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
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> {
        rawPuts[uri] = body
        return SolidResult.Success(Unit)
    }

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> =
        if (store.containsKey(uri) || rawPuts.containsKey(uri)) {
            SolidResult.Success(SolidMetadata.EMPTY)
        } else {
            SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
        }

    override suspend fun headPublic(uri: String): SolidResult<SolidMetadata> =
        SolidResult.Success(SolidMetadata.EMPTY)

    override suspend fun <T : Resource> readPublic(
        uri: String,
        clazz: Class<T>,
    ): SolidResult<T> = read("", uri, clazz)

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = patchRaw(webId, uri, patch.toString(), ifMatch)

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> {
        patches.add(uri to n3Body)
        return SolidResult.Success(Unit)
    }

    override suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<String?> {
        val slug = additionalHeaders["Slug"] ?: UUID.randomUUID().toString()
        val location = uri.trimEnd('/') + "/" + slug
        rawPuts[location] = body
        return SolidResult.Success(location)
    }

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?> {
        val location = resource.getIdentifier().takeIf { it.startsWith(containerUri) }
            ?: (containerUri.trimEnd('/') + "/" + UUID.randomUUID())
        put(resource)
        return SolidResult.Success(location)
    }
}

/**
 * Seeds a pod with the identity and both type indexes every data-module test needs before it can
 * assert anything, so a test starts at its first real assertion.
 */
internal fun inMemoryPod(
    webId: String,
    privateTypeIndexUri: String,
    publicTypeIndexUri: String,
    conflictOnExistingCreate: Boolean = false,
    seedPrivateIndex: PrivateTypeIndex.() -> Unit = {},
    seedPublicIndex: PublicTypeIndex.() -> Unit = {},
): InMemoryPodResourceManager = InMemoryPodResourceManager().apply {
    this.conflictOnExistingCreate = conflictOnExistingCreate
    put(
        WebId(
            webId,
            listOf(
                RdfQuad(webId, Solid.PRIVATE_TYPE_INDEX, privateTypeIndexUri),
                RdfQuad(webId, Solid.PUBLIC_TYPE_INDEX, publicTypeIndexUri),
            ),
        ),
    )
    put(
        PrivateTypeIndex(privateTypeIndexUri, "application/ld+json", null, null)
            .apply(seedPrivateIndex),
    )
    put(
        PublicTypeIndex(publicTypeIndexUri, "application/ld+json", null, null)
            .apply(seedPublicIndex),
    )
}
