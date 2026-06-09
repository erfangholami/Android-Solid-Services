package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import java.net.URI

internal class FakeSolidResourceManager(
    var onRead: (URI) -> SolidNetworkResponse<out Resource> = { notImplemented() },
    var onReadPublic: (URI) -> SolidNetworkResponse<out Resource> = { notImplemented() },
    var onHead: (URI) -> SolidNetworkResponse<SolidMetadata> = { SolidNetworkResponse.Success(SolidMetadata.EMPTY) },
    var onHeadPublic: (URI) -> SolidNetworkResponse<SolidMetadata> = { SolidNetworkResponse.Success(SolidMetadata.EMPTY) },
    var onPost: (URI) -> SolidNetworkResponse<URI?> = { SolidNetworkResponse.Success(null) },
    var onDelete: (URI) -> SolidNetworkResponse<Boolean> = { SolidNetworkResponse.Success(true) },
) : SolidResourceManager {

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = onRead(resource) as SolidNetworkResponse<T>

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = onReadPublic(uri) as SolidNetworkResponse<T>

    override suspend fun head(webid: String, uri: URI): SolidNetworkResponse<SolidMetadata> =
        onHead(uri)

    override suspend fun headPublic(uri: URI): SolidNetworkResponse<SolidMetadata> =
        onHeadPublic(uri)

    override suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidNetworkResponse<URI?> = onPost(uri)

    override suspend fun delete(webid: String, resourceUri: URI): SolidNetworkResponse<Boolean> =
        onDelete(resourceUri)

    override suspend fun <T : Resource> create(webid: String, resource: T): SolidNetworkResponse<T> =
        notImplemented()

    override suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String?,
    ): SolidNetworkResponse<T> = notImplemented()

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

    override suspend fun <T : Resource> delete(webid: String, resource: T): SolidNetworkResponse<T> =
        notImplemented()

    override suspend fun putRaw(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidNetworkResponse<Unit> = notImplemented()

    companion object {
        private fun <T> notImplemented(): SolidNetworkResponse<T> =
            SolidNetworkResponse.Exception(NotImplementedError("not exercised by this test"))
    }
}
