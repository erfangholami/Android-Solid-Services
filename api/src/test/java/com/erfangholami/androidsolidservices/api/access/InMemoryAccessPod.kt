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

/**
 * A minimal in-memory pod that models exactly what the WAC/ACP access backends
 * touch: `HEAD` on a resource advertises its ACL/ACR link, `HEAD` on that
 * ACL/ACR reports existence + an ETag, `read` parses the stored document back
 * from N-Triples, and `putRaw` stores it and bumps the ETag.
 *
 * The ACL/ACR round-trips through the library's own [NTriples] codec, so a
 * backend that writes a document and reads it back sees exactly the triples it
 * wrote — the same contract a real Solid server offers over
 * `application/n-triples`.
 */
internal class InMemoryAccessPod : SolidResourceManager {

    private class StoredDoc(val body: ByteArray, val etag: String)

    private val stored = mutableMapOf<String, StoredDoc>()
    private var etagSeq = 0

    /** When set, the next [putRaw] returns `412` once, then clears itself. */
    var failNextPutWith412: Boolean = false

    /**
     * URIs whose stored body is treated as unreadable: `HEAD` still succeeds (the
     * document exists, with an ETag) but `read` fails — modelling an ACR the parser
     * can't handle (e.g. a Turtle-serialised ACR, or malformed JSON-LD).
     */
    val unreadable: MutableSet<String> = mutableSetOf()

    /** Records every [putRaw] (target URI + the `If-Match` sent) for assertions. */
    val putLog: MutableList<Pair<String, String?>> = mutableListOf()

    fun aclUriFor(resource: URI): URI = URI.create("$resource.acl")

    private fun isAclUri(s: String): Boolean = s.endsWith(".acl")

    override suspend fun head(webid: String, uri: URI): SolidResult<SolidMetadata> {
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
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidResult<T> {
        if (resource.toString() in unreadable) {
            return SolidResult.Failure(SolidError.fromHttp(200, "unreadable body (test)"))
        }
        val doc = stored[resource.toString()]
            ?: return SolidResult.Failure(SolidError.fromHttp(404, "not found: $resource"))
        val quads = NTriples.parse(String(doc.body, Charsets.UTF_8), resource)
        val headers = SolidHeaders(mapOf("ETag" to listOf(doc.etag)))
        val parsed: Resource = if (SolidACLResource::class.java.isAssignableFrom(clazz)) {
            SolidACLResource(resource, quads, headers)
        } else {
            SolidRDFResource(resource, "application/n-triples", quads, headers)
        }
        return SolidResult.Success(parsed as T)
    }

    override suspend fun putRaw(
        webid: String,
        uri: URI,
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
        uri: URI,
        clazz: Class<T>,
    ): SolidResult<T> = read("", uri, clazz)

    override suspend fun headPublic(uri: URI): SolidResult<SolidMetadata> = head("", uri)

    override suspend fun <T : Resource> create(webid: String, resource: T): SolidResult<T> =
        notImplemented()

    override suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = notImplemented()

    override suspend fun patch(
        webid: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun patchRaw(
        webid: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = notImplemented()

    override suspend fun delete(
        webid: String,
        resourceUri: URI,
        ifMatch: String?,
    ): SolidResult<Boolean> = notImplemented()

    override suspend fun <T : Resource> delete(webid: String, resource: T): SolidResult<T> =
        notImplemented()

    override suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<URI?> = notImplemented()

    override suspend fun <T : Resource> createInContainer(
        webid: String,
        containerUri: URI,
        resource: T,
    ): SolidResult<URI?> = notImplemented()

    private fun <T> notImplemented(): SolidResult<T> =
        SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised by the access-backend tests")))
}
