package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata

interface SolidResourceRepository {

    suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata>

    suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T>

    suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T>

    suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String? = null,
    ): SolidResult<T>

    suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String? = null,
    ): SolidResult<Unit>

    suspend fun <T : Resource> delete(webId: String, resource: T): SolidResult<T>

    suspend fun delete(webId: String, resourceUri: String): SolidResult<Boolean>
}
