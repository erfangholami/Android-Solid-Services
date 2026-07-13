package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch

internal class FakeSolidResourceManager(
    var onRead: (String) -> SolidResult<out Resource> = { notImplemented() },
    var onReadPublic: (String) -> SolidResult<out Resource> = { notImplemented() },
    var onHead: (String) -> SolidResult<SolidMetadata> = { SolidResult.Success(SolidMetadata.EMPTY) },
    var onHeadPublic: (String) -> SolidResult<SolidMetadata> = { SolidResult.Success(SolidMetadata.EMPTY) },
    var onPost: (String) -> SolidResult<String?> = { SolidResult.Success(null) },
    var onDelete: (String) -> SolidResult<Boolean> = { SolidResult.Success(true) },
    var onCreate: (Resource) -> SolidResult<out Resource> = { SolidResult.Success(it) },
    var onUpdate: (Resource) -> SolidResult<out Resource> = { notImplemented() },
    var onPatch: (String, N3Patch) -> SolidResult<Unit> = { _, _ -> SolidResult.Success(Unit) },
    var onPutRaw: (String, ByteArray, String) -> SolidResult<Unit> = { _, _, _ -> SolidResult.Success(Unit) },
) : SolidResourceManager {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> = onRead(resource) as SolidResult<T>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> readPublic(
        uri: String,
        clazz: Class<T>,
    ): SolidResult<T> = onReadPublic(uri) as SolidResult<T>

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> =
        onHead(uri)

    override suspend fun headPublic(uri: String): SolidResult<SolidMetadata> =
        onHeadPublic(uri)

    override suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<String?> = onPost(uri)

    override suspend fun delete(
        webId: String,
        resourceUri: String,
        ifMatch: String?,
    ): SolidResult<Boolean> = onDelete(resourceUri)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T> =
        onCreate(resource) as SolidResult<T>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = onUpdate(newResource) as SolidResult<T>

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = onPatch(uri, patch)

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun <T : Resource> delete(webId: String, resource: T): SolidResult<T> =
        notImplemented()

    override suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> = onPutRaw(uri, body, contentType)

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?> = notImplemented()

    companion object {
        private fun <T> notImplemented(): SolidResult<T> =
            SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised by this test")))
    }
}
