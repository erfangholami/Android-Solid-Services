package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import java.net.URI

interface SolidResourceRepository {

    suspend fun head(webId: String, uri: URI): SolidNetworkResponse<SolidMetadata>

    suspend fun <T : Resource> read(
        webId: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T>

    suspend fun <T : Resource> create(webId: String, resource: T): SolidNetworkResponse<T>

    suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String? = null,
    ): SolidNetworkResponse<T>

    suspend fun patchRaw(
        webId: String,
        uri: URI,
        n3Body: String,
        ifMatch: String? = null,
    ): SolidNetworkResponse<Unit>

    suspend fun <T : Resource> delete(webId: String, resource: T): SolidNetworkResponse<T>

    suspend fun delete(webId: String, resourceUri: URI): SolidNetworkResponse<Boolean>
}
