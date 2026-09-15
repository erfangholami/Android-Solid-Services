package com.erfangholami.androidsolidservices.client.internal.fakes

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidSourceReference
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * Answers every verb the guard lets through, and records that it was reached.
 *
 * The point of the guard suite is what the policy decides, not what a pod would return, so this
 * succeeds blandly. [reached] tells a refusal apart from a failure: a denied call must never get
 * this far, and a test that only checked the error code could not tell the difference.
 */
internal class StubResourceManager : SolidResourceManager {

    @Volatile
    var reached: String? = null
        private set

    fun forget() {
        reached = null
    }

    private fun <T> answer(verb: String, value: T): SolidResult<T> {
        reached = verb
        return SolidResult.Success(value)
    }

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> =
        answer("head", Fixtures.METADATA)

    override suspend fun headPublic(uri: String): SolidResult<SolidMetadata> =
        answer("headPublic", Fixtures.METADATA)

    override suspend fun listContainer(
        webId: String,
        containerUri: String,
        enrichWithHead: Boolean,
    ): SolidResult<List<SolidSourceReference>> = answer("listContainer", emptyList())

    override suspend fun delete(
        webId: String,
        resourceUri: String,
        ifMatch: String?,
    ): SolidResult<Boolean> = answer("delete", true)

    override suspend fun <T : Resource> delete(webId: String, resource: T): SolidResult<T> =
        answer("deleteResource", resource)

    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> = SolidResult.Failure(SolidError.Unknown("read is not reached by these tests"))

    override suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T> =
        answer("create", resource)

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = answer("update", newResource)

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = answer("patch", Unit)

    override suspend fun <T : Resource> readPublic(uri: String, clazz: Class<T>): SolidResult<T> =
        SolidResult.Failure(SolidError.Unknown("readPublic is not reached by these tests"))

    override suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> = answer("putRaw", Unit)

    override suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<String?> = answer("post", uri)

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?> = answer("createInContainer", containerUri)

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = answer("patchRaw", Unit)
}
