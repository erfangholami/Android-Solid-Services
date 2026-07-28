package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxReader
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
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

class InboxReaderGateTest {

    private val readerWebId = "https://solidweb.org/alice/profile/card#me"
    private val inboxUri = "https://solidweb.org/alice/inbox/"
    private val itemUri = "https://solidweb.org/alice/inbox/item1"

    private fun readerProfile() =
        WebId(readerWebId, listOf(RdfQuad(readerWebId, LDP.INBOX, inboxUri)))

    private fun inboxContainer() = SolidContainer(
        inboxUri,
        "application/ld+json",
        listOf(RdfQuad(inboxUri, LDP.CONTAINS, itemUri)),
    )

    private fun offer(actorWebId: String, resourceUri: String) = ShareNotificationRDF(
        itemUri,
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
                    readerWebId -> SolidResult.Success(readerProfile())
                    inboxUri -> SolidResult.Success(inboxContainer())
                    itemUri -> SolidResult.Success(offer(actorWebId, resourceUri))
                    else -> SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
            onReadPublic = { uri ->
                if (uri.toString() == actorWebId) {
                    SolidResult.Success(
                        WebId(
                            actorWebId,
                            listOf(RdfQuad(actorWebId, PIM.STORAGE, actorStorage)),
                        ),
                    )
                } else {
                    SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
            onHead = { uri ->
                if (uri.toString() == resourceUri) {
                    SolidResult.Success(
                        headOwner?.let { SolidMetadata.EMPTY.copy(ownerUri = it) }
                            ?: SolidMetadata.EMPTY,
                    )
                } else {
                    SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
        )
        return InboxReader(fake, InboxDiscovery(fake), SolidShareNotificationProfile)
    }

    @Test
    fun `forged offer for another tenant's resource on a shared host is dropped`() = runBlocking {
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
