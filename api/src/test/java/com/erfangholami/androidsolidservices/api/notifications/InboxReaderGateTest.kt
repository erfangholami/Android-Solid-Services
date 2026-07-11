package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxReader
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareNotificationRDF
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

/**
 * Tests the anti-impersonation gate in [InboxReader]: an inbound `as:Offer`
 * must be dropped unless its `as:actor` is provably the owner of the offered
 * resource. Regression guard for the removed bare "same host as the actor's
 * WebID" fallback, which let any user on a shared multi-tenant pod forge an
 * offer for another user's resource.
 */
class InboxReaderGateTest {

    private val readerWebId = "https://solidweb.org/alice/profile/card#me"
    private val inboxUri = "https://solidweb.org/alice/inbox/"
    private val itemUri = "https://solidweb.org/alice/inbox/item1"

    private fun readerProfile() =
        WebId(URI.create(readerWebId), listOf(RdfQuad(readerWebId, LDP.INBOX, inboxUri)))

    private fun inboxContainer() = SolidContainer(
        URI.create(inboxUri),
        "application/ld+json",
        listOf(RdfQuad(inboxUri, LDP.CONTAINS, itemUri)),
    )

    private fun offer(actorWebId: String, resourceUri: String) = ShareNotificationRDF(
        URI.create(itemUri),
        quads = listOf(
            RdfQuad("$itemUri#offer", RDF.TYPE, AS.OFFER),
            RdfQuad("$itemUri#offer", AS.ACTOR, actorWebId),
            RdfQuad("$itemUri#offer", AS.OBJECT, resourceUri),
            RdfQuad("$itemUri#offer", ACL.MODE, ACL.READ),
        ),
    )

    private fun reader(
        actorWebId: String,
        resourceUri: String,
        actorStorage: String,
        headOwner: String? = null,
    ): InboxReader {
        val fake = FakeSolidResourceManager(
            onRead = { uri ->
                when (uri.toString()) {
                    readerWebId -> SolidNetworkResponse.Success(readerProfile())
                    inboxUri -> SolidNetworkResponse.Success(inboxContainer())
                    itemUri -> SolidNetworkResponse.Success(offer(actorWebId, resourceUri))
                    else -> SolidNetworkResponse.Error(404, "not found: $uri")
                }
            },
            onReadPublic = { uri ->
                if (uri.toString() == actorWebId) {
                    SolidNetworkResponse.Success(
                        WebId(
                            URI.create(actorWebId),
                            listOf(RdfQuad(actorWebId, PIM.STORAGE, actorStorage)),
                        ),
                    )
                } else {
                    SolidNetworkResponse.Error(404, "not found: $uri")
                }
            },
            onHead = { uri ->
                if (uri.toString() == resourceUri) {
                    SolidNetworkResponse.Success(
                        headOwner?.let { SolidMetadata.EMPTY.copy(ownerUri = URI.create(it)) }
                            ?: SolidMetadata.EMPTY,
                    )
                } else {
                    SolidNetworkResponse.Error(404, "not found: $uri")
                }
            },
        )
        return InboxReader(fake, InboxDiscovery(fake), SolidShareNotificationProfile)
    }

    @Test
    fun `forged offer for another tenant's resource on a shared host is dropped`() = runBlocking {
        // Path-based multi-tenant pod: bob and alice share host solidweb.org.
        val bob = "https://solidweb.org/bob/profile/card#me"
        val aliceResource = "https://solidweb.org/alice/private/secret"
        val result = reader(bob, aliceResource, actorStorage = "https://solidweb.org/bob/")
            .listNotifications(readerWebId)
        assertEquals(emptyList<Any>(), result.map { it.resourceUri })
    }

    @Test
    fun `offer for a resource under the actor's own declared storage is kept`() = runBlocking {
        val bob = "https://solidweb.org/bob/profile/card#me"
        val bobResource = "https://solidweb.org/bob/photos/x"
        val result = reader(bob, bobResource, actorStorage = "https://solidweb.org/bob/")
            .listNotifications(readerWebId)
        assertEquals(listOf(bobResource), result.map { it.resourceUri })
    }

    @Test
    fun `offer whose owner header names the actor is kept`() = runBlocking {
        val bob = "https://solidweb.org/bob/profile/card#me"
        val resource = "https://solidweb.org/alice/shared/thing"
        val result = reader(bob, resource, actorStorage = "https://solidweb.org/bob/", headOwner = bob)
            .listNotifications(readerWebId)
        assertEquals(listOf(resource), result.map { it.resourceUri })
    }

    @Test
    fun `offer whose owner header names someone else is dropped`() = runBlocking {
        val bob = "https://solidweb.org/bob/profile/card#me"
        val resource = "https://solidweb.org/alice/shared/thing"
        val result = reader(
            bob, resource,
            actorStorage = "https://solidweb.org/bob/",
            headOwner = "https://solidweb.org/carol/profile/card#me",
        ).listNotifications(readerWebId)
        assertEquals(emptyList<Any>(), result.map { it.resourceUri })
    }

    @Test
    fun `ESS-style split of identity and storage across sibling subdomains is kept`() = runBlocking {
        val bob = "https://id.provider.example/bob/card#me"
        val bobResource = "https://storage.provider.example/bob/x"
        val result = reader(bob, bobResource, actorStorage = "https://storage.provider.example/bob/")
            .listNotifications(readerWebId)
        assertEquals(listOf(bobResource), result.map { it.resourceUri })
    }
}
