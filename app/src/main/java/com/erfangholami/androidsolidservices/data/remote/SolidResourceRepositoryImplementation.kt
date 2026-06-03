package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.domain.repository.SolidResourceRepository
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolidResourceRepositoryImplementation @Inject constructor(
    private val resourceManager: SolidResourceManager,
) : SolidResourceRepository {

    override suspend fun head(webId: String, uri: URI): SolidNetworkResponse<SolidMetadata> =
        resourceManager.head(webId, uri)

    override suspend fun <T : Resource> read(
        webId: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = resourceManager.read(webId, resource, clazz)

    override suspend fun <T : Resource> create(
        webId: String,
        resource: T,
    ): SolidNetworkResponse<T> = resourceManager.create(webId, resource)

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
    ): SolidNetworkResponse<T> = resourceManager.update(webId, newResource, ifMatch)

    override suspend fun patchRaw(
        webId: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> = resourceManager.patchRaw(webId, uri, n3Body, ifMatch)

    override suspend fun <T : Resource> delete(
        webId: String,
        resource: T,
    ): SolidNetworkResponse<T> = resourceManager.delete(webId, resource)

    override suspend fun delete(webId: String, resourceUri: URI): SolidNetworkResponse<Boolean> =
        resourceManager.delete(webId, resourceUri)
}
