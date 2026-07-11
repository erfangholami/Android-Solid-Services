package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.access.NTriples
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
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

    override suspend fun head(webid: String, uri: URI): SolidNetworkResponse<SolidMetadata> {
        val s = uri.toString()
        return if (isAcl(s)) {
            acls[s]?.let { SolidNetworkResponse.Success(SolidMetadata.EMPTY.copy(etag = it.etag)) }
                ?: SolidNetworkResponse.Error(404, "no ACL at $s")
        } else {
            SolidNetworkResponse.Success(SolidMetadata.EMPTY.copy(aclUri = URI.create("$s.acl")))
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webid: String,
        resource: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> {
        val s = resource.toString()
        return when {
            WebId::class.java.isAssignableFrom(clazz) && s == ownerWebId ->
                SolidNetworkResponse.Success(
                    WebId(
                        URI.create(ownerWebId),
                        listOf(RdfQuad(ownerWebId, PIM.STORAGE, podRoot)),
                    ) as T,
                )

            SolidACLResource::class.java.isAssignableFrom(clazz) -> {
                val doc = acls[s] ?: return SolidNetworkResponse.Error(404, "no ACL: $s")
                val quads = NTriples.parse(String(doc.body, Charsets.UTF_8), resource)
                SolidNetworkResponse.Success(
                    SolidACLResource(resource, quads, SolidHeaders(mapOf("ETag" to listOf(doc.etag)))) as T,
                )
            }

            GivenSharesIndexRDF::class.java.isAssignableFrom(clazz) ->
                SolidNetworkResponse.Success(
                    GivenSharesIndexRDF(resource, "application/ld+json", emptyList(), null) as T,
                )

            else -> SolidNetworkResponse.Error(404, "not served: $s as ${clazz.simpleName}")
        }
    }

    override suspend fun putRaw(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidNetworkResponse<Unit> {
        aclPutLog += uri.toString()
        etagSeq++
        acls[uri.toString()] = Doc(body, "etag-$etagSeq")
        return SolidNetworkResponse.Success(Unit)
    }

    override suspend fun patch(
        webid: String,
        uri: URI,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> =
        if (uri.toString() == givenIndexUri) {
            SolidNetworkResponse.Error(500, "index patch boom (test)")
        } else {
            SolidNetworkResponse.Success(Unit)
        }

    override suspend fun <T : Resource> create(webid: String, resource: T): SolidNetworkResponse<T> =
        SolidNetworkResponse.Success(resource)

    override suspend fun <T : Resource> update(
        webid: String,
        newResource: T,
        ifMatch: String?,
    ): SolidNetworkResponse<T> = SolidNetworkResponse.Success(newResource)

    override suspend fun <T : Resource> readPublic(
        uri: URI,
        clazz: Class<T>,
    ): SolidNetworkResponse<T> = read("", uri, clazz)

    override suspend fun headPublic(uri: URI): SolidNetworkResponse<SolidMetadata> = head("", uri)

    override suspend fun patchRaw(
        webid: String,
        uri: URI,
        n3Body: String,
        ifMatch: String?,
    ): SolidNetworkResponse<Unit> = SolidNetworkResponse.Success(Unit)

    override suspend fun delete(webid: String, resourceUri: URI): SolidNetworkResponse<Boolean> =
        SolidNetworkResponse.Success(true)

    override suspend fun <T : Resource> delete(webid: String, resource: T): SolidNetworkResponse<T> =
        SolidNetworkResponse.Success(resource)

    override suspend fun post(
        webid: String,
        uri: URI,
        contentType: String,
        body: ByteArray,
        additionalHeaders: Map<String, String>,
    ): SolidNetworkResponse<URI?> = SolidNetworkResponse.Success(null)

    override suspend fun <T : Resource> createInContainer(
        webid: String,
        containerUri: URI,
        resource: T,
    ): SolidNetworkResponse<URI?> = SolidNetworkResponse.Success(null)
}
