package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.access.NTriples
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationsManagerImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerHelper
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerImplementation
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.access.SolidACLResource
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.rdf.sharing.GivenSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ReceivedSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

class TypedShareEngineTest {

    private val alice = "https://alice.pod/profile/card#me"
    private val bobWebId = "https://bob.pod/profile/card#me"
    private val bob = ShareReceiver.WebIdReceiver(bobWebId)
    private val podRoot = "https://alice.pod/"
    private val container = "https://alice.pod/tickets/u1/"
    private val artifact = "https://alice.pod/tickets/u1/artifact.pkpass"

    private lateinit var pod: PatchApplyingSharingPod

    @Before
    fun setUp() {
        SharingManagerImplementation.resetForTest()
        SharingManagerHelper.resetForTest()
        NotificationsManagerImplementation.resetForTest()
        pod = PatchApplyingSharingPod(alice, podRoot)
    }

    private fun manager() = SharingManagerImplementation.getInstance(pod)

    @Test
    fun `createShare records the entity type and title on the given index`() = runBlocking {
        manager().createShare(
            alice, container, ShareMode.READ, bob,
            notifyReceiver = false,
            resourceType = Schema.TICKET,
            resourceName = "Coldplay — Music of the Spheres",
        ).getOrThrow()

        val stored = manager().getStoredGivenShares(alice).getOrThrow().single()
        assertEquals(Schema.TICKET, stored.resourceType)
        assertEquals("Coldplay — Music of the Spheres", stored.resourceName)
        assertEquals(ShareMode.READ, stored.mode)
    }

    @Test
    fun `an untyped re-grant never strips the stored typing`() = runBlocking {
        manager().createShare(
            alice, container, ShareMode.READ, bob,
            notifyReceiver = false,
            resourceType = Schema.TICKET,
            resourceName = "Coldplay",
        ).getOrThrow()

        manager().createShare(
            alice, container, ShareMode.WRITE, bob, notifyReceiver = false,
        ).getOrThrow()

        val stored = manager().getStoredGivenShares(alice).getOrThrow().single()
        assertEquals(ShareMode.WRITE, stored.mode)
        assertEquals(Schema.TICKET, stored.resourceType)
        assertEquals("Coldplay", stored.resourceName)
    }

    @Test
    fun `updateShare refreshes the stored title in place`() = runBlocking {
        manager().createShare(
            alice, container, ShareMode.READ, bob,
            notifyReceiver = false,
            resourceType = Schema.TICKET,
            resourceName = "Old title",
        ).getOrThrow()

        manager().updateShare(
            alice, container, ShareMode.READ, bob,
            resourceType = Schema.TICKET,
            resourceName = "New title",
        ).getOrThrow()

        val stored = manager().getStoredGivenShares(alice).getOrThrow().single()
        assertEquals("New title", stored.resourceName)
        assertEquals(Schema.TICKET, stored.resourceType)
    }

    @Test
    fun `revokeShare leaves no orphaned typed-share triples behind`() = runBlocking {
        manager().createShare(
            alice, artifact, ShareMode.READ, ShareReceiver.Public,
            notifyReceiver = false,
            resourceType = Schema.TICKET,
            resourceName = "Coldplay",
        ).getOrThrow()

        manager().revokeShare(alice, artifact, ShareReceiver.Public).getOrThrow()

        assertTrue(manager().getStoredGivenShares(alice).getOrThrow().isEmpty())
        assertTrue(
            "revoke must delete every triple of the share node",
            pod.quadsOf("${podRoot}solidshare/shares/given_shares.ttl")
                .none { it.subject.contains("#share-") },
        )
    }

    @Test
    fun `syncReceivedShares carries the notification's type and name into the received index`() =
        runBlocking {
            val offered = ShareNotification(
                notificationUri = "https://alice.pod/inbox/offer1",
                type = ShareNotificationType.OFFER,
                ownerWebId = bobWebId,
                resourceUri = "https://bob.pod/tickets/u9/",
                mode = ShareMode.READ,
                summary = null,
                publishedAt = "2026-08-01T10:00:00Z",
                resourceType = Schema.TICKET,
                resourceName = "Bob's concert",
            )

            val rows = manager().syncReceivedShares(alice, listOf(offered)).getOrThrow()

            val row = rows.single()
            assertEquals(Schema.TICKET, row.resourceType)
            assertEquals("Bob's concert", row.resourceName)
            assertEquals(bobWebId, row.ownerWebId)
        }

    @Test
    fun `addReceivedShare stores the caller's entity hints`() = runBlocking {
        val row = manager().addReceivedShare(
            alice, "https://bob.pod/tickets/u9/",
            ownerHint = bobWebId,
            resourceType = Schema.TICKET,
            resourceName = "Bob's concert",
        ).getOrThrow()

        assertEquals(Schema.TICKET, row?.resourceType)
        assertEquals("Bob's concert", row?.resourceName)
        val stored = manager().getStoredReceivedShares(alice).getOrThrow().single()
        assertEquals(Schema.TICKET, stored.resourceType)
        assertEquals("Bob's concert", stored.resourceName)
    }
}

/**
 * A sharing pod that actually applies N3 patches to the two share indexes, so typed-share
 * writes can be read back — the round-trip complement of [InMemorySharingPod], which fails
 * index patches on purpose.
 */
internal class PatchApplyingSharingPod(
    private val ownerWebId: String,
    private val podRoot: String,
) : SolidResourceManager {

    private class Doc(val body: ByteArray, val etag: String)

    private val acls = mutableMapOf<String, Doc>()
    private val docs = mutableMapOf<String, MutableList<RdfQuad>>()
    private var etagSeq = 0

    private val deletedPrefixes = mutableListOf<String>()

    /** URIs whose HEAD fails with a server error — a transient fault, not a deletion. */
    val failHeadFor: MutableSet<String> = mutableSetOf()

    private val givenIndexUri = "${podRoot}solidshare/shares/given_shares.ttl"
    private val receivedIndexUri = "${podRoot}solidshare/shares/received_shares.ttl"

    fun quadsOf(uri: String): List<RdfQuad> = docs[uri].orEmpty().toList()

    /** Removes [uri] and everything beneath it, the way deleting a container does. */
    fun deleteEverythingUnder(uri: String) {
        deletedPrefixes += uri
        acls.keys.filter { it.startsWith(uri) }.forEach { acls.remove(it) }
    }

    private fun isAcl(s: String) = s.endsWith(".acl")

    private fun isIndex(s: String) = s == givenIndexUri || s == receivedIndexUri

    private fun isDeleted(uri: String) = deletedPrefixes.any { uri.startsWith(it) }

    override suspend fun head(webId: String, uri: String): SolidResult<SolidMetadata> = when {
        uri in failHeadFor -> SolidResult.Failure(SolidError.fromHttp(500, "boom (test)"))

        isDeleted(uri) -> SolidResult.Failure(SolidError.fromHttp(404, "deleted: $uri"))

        isAcl(uri) ->
            acls[uri]?.let { SolidResult.Success(SolidMetadata.EMPTY.copy(etag = it.etag)) }
                ?: SolidResult.Failure(SolidError.fromHttp(404, "no ACL at $uri"))

        else -> SolidResult.Success(SolidMetadata.EMPTY.copy(aclUri = "$uri.acl"))
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Resource> read(
        webId: String,
        resource: String,
        clazz: Class<T>,
    ): SolidResult<T> = when {
        WebId::class.java.isAssignableFrom(clazz) && resource == ownerWebId ->
            SolidResult.Success(
                WebId(ownerWebId, listOf(RdfQuad(ownerWebId, PIM.STORAGE, podRoot))) as T,
            )

        SolidACLResource::class.java.isAssignableFrom(clazz) -> {
            val doc = acls[resource]
                ?: return SolidResult.Failure(SolidError.fromHttp(404, "no ACL: $resource"))
            val quads = NTriples.parse(String(doc.body, Charsets.UTF_8), URI.create(resource))
            SolidResult.Success(
                SolidACLResource(
                    resource, quads, SolidHeaders(mapOf("ETag" to listOf(doc.etag))),
                ) as T,
            )
        }

        GivenSharesIndexRDF::class.java.isAssignableFrom(clazz) && resource == givenIndexUri ->
            SolidResult.Success(
                GivenSharesIndexRDF(resource, "application/ld+json", quadsOf(resource), null) as T,
            )

        ReceivedSharesIndexRDF::class.java.isAssignableFrom(clazz) && resource == receivedIndexUri ->
            SolidResult.Success(
                ReceivedSharesIndexRDF(resource, "application/ld+json", quadsOf(resource), null) as T,
            )

        else -> SolidResult.Failure(SolidError.fromHttp(404, "not served: $resource"))
    }

    override suspend fun patch(
        webId: String,
        uri: String,
        patch: N3Patch,
        ifMatch: String?,
    ): SolidResult<Unit> {
        if (!isIndex(uri)) return SolidResult.Success(Unit)
        val quads = docs.getOrPut(uri) { mutableListOf() }
        patch.deletes?.let { text ->
            NTriples.parse(text, URI.create(uri)).forEach { d ->
                quads.removeAll {
                    it.subject == d.subject && it.predicate == d.predicate && it.`object` == d.`object`
                }
            }
        }
        patch.inserts?.let { text -> quads += NTriples.parse(text, URI.create(uri)) }
        return SolidResult.Success(Unit)
    }

    override suspend fun putRaw(
        webId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
        ifMatch: String?,
        linkHeader: String?,
    ): SolidResult<Unit> {
        etagSeq++
        acls[uri] = Doc(body, "etag-$etagSeq")
        return SolidResult.Success(Unit)
    }

    override suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T> =
        SolidResult.Success(resource)

    override suspend fun <T : Resource> update(
        webId: String,
        newResource: T,
        ifMatch: String?,
        ifUnmodifiedSince: String?,
    ): SolidResult<T> = SolidResult.Success(newResource)

    override suspend fun <T : Resource> readPublic(uri: String, clazz: Class<T>): SolidResult<T> =
        read("", uri, clazz)

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
