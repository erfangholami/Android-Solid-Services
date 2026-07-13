package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.domain.repository.SolidResourceRepository
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolidResourceRepositoryImplementation @Inject constructor(
    private val resourceManager: SolidResourceManager,
) : SolidResourceRepository {

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> =
        resourceManager.head(webId, uri)

    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> = resourceManager.read(webId, resource, clazz)

    override suspend fun <T : Resource> create(
        webId: String,
        resource: T,
    ): SolidResult<T> = resourceManager.create(webId, resource)

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
    ): SolidResult<T> = resourceManager.update(webId, newResource, ifMatch)

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = resourceManager.patchRaw(webId, uri, n3Body, ifMatch)

    override suspend fun <T : Resource> delete(
        webId: String,
        resource: T,
    ): SolidResult<T> = resourceManager.delete(webId, resource)

    override suspend fun delete(webId: String, resourceUri: String): SolidResult<Boolean> =
        resourceManager.delete(webId, resourceUri)
}
