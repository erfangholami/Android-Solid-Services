package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.access.NTriples
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.access.SolidACLResource
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.rdf.sharing.GivenSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import java.net.URI

/**
 * In-memory pod for exercising the sharing engine's `createShare` path end to
 * end: it serves the owner's WebID (with a `pim:storage`), round-trips WAC ACLs
 * through the N-Triples codec (so a grant/revoke actually changes observable
 * state), returns an empty given-shares index, and — the seam under test —
 * fails the `PATCH` on the given-shares index so the index write inside
 * `createShare` throws and drives the rollback branch.
 *
 * Every non-`.acl` URI is reported as existing and advertises a same-origin
 * `acl` link, which forces the WAC backend (never ACP) and lets the container
 * bootstrap short-circuit without creating anything.
 */
internal class InMemorySharingPod(
    private val ownerWebId: String,
    private val podRoot: String,
) : SolidResourceManager {

    private class Doc(val body: ByteArray, val etag: String)

    private val acls = mutableMapOf<String, Doc>()
    private var etagSeq = 0

    /** Every ACL/ACR URI written, in order — grant then (on rollback) revoke. */
    val aclPutLog: MutableList<String> = mutableListOf()

    private val givenIndexUri = "${podRoot}solidshare/shares/given_shares.ttl"

    private fun isAcl(s: String) = s.endsWith(".acl")

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> {
        val s = uri.toString()
        return if (isAcl(s)) {
            acls[s]?.let { SolidResult.Success(SolidMetadata.EMPTY.copy(etag = it.etag)) }
                ?: SolidResult.Failure(SolidError.fromHttp(404, "no ACL at $s"))
        } else {
            SolidResult.Success(SolidMetadata.EMPTY.copy(aclUri = "$s.acl"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> {
        val s = resource
        return when {
            WebId::class.java.isAssignableFrom(clazz) && s == ownerWebId ->
                SolidResult.Success(
                    WebId(
                        ownerWebId,
                        listOf(RdfQuad(ownerWebId, PIM.STORAGE, podRoot)),
                    ) as T,
                )

            SolidACLResource::class.java.isAssignableFrom(clazz) -> {
                val doc = acls[s] ?: return SolidResult.Failure(SolidError.fromHttp(404, "no ACL: $s"))
                val quads = NTriples.parse(String(doc.body, Charsets.UTF_8), URI.create(resource))
                SolidResult.Success(
                    SolidACLResource(resource, quads, SolidHeaders(mapOf("ETag" to listOf(doc.etag)))) as T,
                )
            }

            GivenSharesIndexRDF::class.java.isAssignableFrom(clazz) ->
                SolidResult.Success(
                    GivenSharesIndexRDF(resource, "application/ld+json", emptyList(), null) as T,
                )

            else -> SolidResult.Failure(SolidError.fromHttp(404, "not served: $s as ${clazz.simpleName}"))
        }
    }

    override suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> {
        aclPutLog += uri.toString()
        etagSeq++
        acls[uri.toString()] = Doc(body, "etag-$etagSeq")
        return SolidResult.Success(Unit)
    }

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> =
        if (uri.toString() == givenIndexUri) {
            SolidResult.Failure(SolidError.fromHttp(500, "index patch boom (test)"))
        } else {
            SolidResult.Success(Unit)
        }

    override suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T> =
        SolidResult.Success(resource)

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = SolidResult.Success(newResource)

    override suspend fun <T : Resource> readPublic(
        uri: String,
        clazz: Class<T>,
    ): SolidResult<T> = read("", uri, clazz)

    override suspend fun headPublic(uri: String): SolidResult<SolidMetadata> = head("", uri)

    override suspend fun patchRaw(
        webId: String,
        uri: String,
        n3Body: String,
        ifMatch: String?,
    ): SolidResult<Unit> = SolidResult.Success(Unit)

    override suspend fun delete(
        webId: String,
        resourceUri: String,
        ifMatch: String?,
    ): SolidResult<Boolean> = SolidResult.Success(true)

    override suspend fun <T : Resource> delete(webId: String, resource: T): SolidResult<T> =
        SolidResult.Success(resource)

    override suspend fun post(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidResult<String?> = SolidResult.Success(null)

    override suspend fun <T : Resource> createInContainer(
        webId: String,
        containerUri: String,
        resource: T,
    ): SolidResult<String?> = SolidResult.Success(null)
}
