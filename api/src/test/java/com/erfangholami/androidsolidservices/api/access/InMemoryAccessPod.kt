package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.access.SolidACLResource
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import java.net.URI

internal class InMemoryAccessPod : SolidResourceManager {

    private class StoredDoc(val body: ByteArray, val etag: String)

    private val stored = mutableMapOf<String, StoredDoc>()
    private var etagSeq = 0

    var failNextPutWith412: Boolean = false

    val unreadable: MutableSet<String> = mutableSetOf()

    val putLog: MutableList<Pair<String, String?>> = mutableListOf()

    fun aclUriFor(resource: String): String = "$resource.acl"

    private fun isAclUri(s: String): Boolean = s.endsWith(".acl")

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> {
        val s = uri.toString()
        return if (isAclUri(s)) {
            stored[s]?.let {
                SolidResult.Success(SolidMetadata.EMPTY.copy(etag = it.etag))
            } ?: SolidResult.Failure(SolidError.fromHttp(404, "no ACL/ACR at $s"))
        } else {
            SolidResult.Success(SolidMetadata.EMPTY.copy(aclUri = aclUriFor(uri)))
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> {
        if (resource in unreadable) {
            return SolidResult.Failure(SolidError.fromHttp(200, "unreadable body (test)"))
        }
        val doc = stored[resource]
            ?: return SolidResult.Failure(SolidError.fromHttp(404, "not found: $resource"))
        val quads = NTriples.parse(String(doc.body, Charsets.UTF_8), URI.create(resource))
        val headers = SolidHeaders(mapOf("ETag" to listOf(doc.etag)))
        val parsed: Resource = if (SolidACLResource::class.java.isAssignableFrom(clazz)) {
            SolidACLResource(resource, quads, headers)
        } else {
            SolidRDFResource(resource, "application/n-triples", quads, headers)
        }
        return SolidResult.Success(parsed as T)
    }

    override suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> {
        putLog += uri.toString() to ifMatch
        if (failNextPutWith412) {
            failNextPutWith412 = false
            return SolidResult.Failure(SolidError.fromHttp(412, "precondition failed (test)"))
        }
        etagSeq++
        stored[uri.toString()] = StoredDoc(body, "etag-$etagSeq")
        return SolidResult.Success(Unit)
    }

    override suspend fun <T : Resource> readPublic(
        uri: String,
        clazz: Class<T>,
    ): SolidResult<T> = read("", uri, clazz)

    override suspend fun headPublic(uri: String): SolidResult<SolidMetadata> = head("", uri)

    override suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T> =
        notImplemented()

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = notImplemented()

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun delete(
        webId: String,
        resourceUri: String,
        ifMatch: String?,
    ): SolidResult<Boolean> = notImplemented()

    override suspend fun <T : Resource> delete(webId: String, resource: T): SolidResult<T> =
        notImplemented()

    override suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<String?> = notImplemented()

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?> = notImplemented()

    private fun <T> notImplemented(): SolidResult<T> =
        SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised by the access-backend tests")))
}
