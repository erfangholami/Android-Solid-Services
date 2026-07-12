package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import java.net.URI

internal class FakeSolidResourceManager(
    var onRead: (URI) -> SolidResult<out Resource> = { notImplemented() },
    var onReadPublic: (URI) -> SolidResult<out Resource> = { notImplemented() },
    var onHead: (URI) -> SolidResult<SolidMetadata> = { SolidResult.Success(SolidMetadata.EMPTY) },
    var onHeadPublic: (URI) -> SolidResult<SolidMetadata> = { SolidResult.Success(SolidMetadata.EMPTY) },
    var onPost: (URI) -> SolidResult<URI?> = { SolidResult.Success(null) },
    var onDelete: (URI) -> SolidResult<Boolean> = { SolidResult.Success(true) },
    var onCreate: (Resource) -> SolidResult<out Resource> = { SolidResult.Success(it) },
    var onUpdate: (Resource) -> SolidResult<out Resource> = { notImplemented() },
    var onPatch: (URI, N3Patch) -> SolidResult<Unit> = { _, _ -> SolidResult.Success(Unit) },
    var onPutRaw: (URI, ByteArray, String) -> SolidResult<Unit> = { _, _, _ -> SolidResult.Success(Unit) },
) : SolidResourceManager {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidResult<T> = onRead(resource) as SolidResult<T>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidResult<T> = onReadPublic(uri) as SolidResult<T>

    override suspend fun head(webid: String, uri: URI): SolidResult<SolidMetadata> =
        onHead(uri)

    override suspend fun headPublic(uri: URI): SolidResult<SolidMetadata> =
        onHeadPublic(uri)

    override suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<URI?> = onPost(uri)

    override suspend fun delete(
        webid: String,
        resourceUri: URI,
        ifMatch: String?,
    ): SolidResult<Boolean> = onDelete(resourceUri)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> create(webid: String, resource: T): SolidResult<T> =
        onCreate(resource) as SolidResult<T>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = onUpdate(newResource) as SolidResult<T>

    override suspend fun patch(
        webid: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = onPatch(uri, patch)

    override suspend fun patchRaw(
        webid: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun <T : Resource> delete(webid: String, resource: T): SolidResult<T> =
        notImplemented()

    override suspend fun putRaw(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> = onPutRaw(uri, body, contentType)

    override suspend fun <T : Resource> createInContainer(
        webid: String,
        containerUri: URI,
        resource: T,
    ): SolidResult<URI?> = notImplemented()

    companion object {
        private fun <T> notImplemented(): SolidResult<T> =
            SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised by this test")))
    }
}
