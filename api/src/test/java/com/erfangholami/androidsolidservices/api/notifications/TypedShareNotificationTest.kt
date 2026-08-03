package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxNotifier
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxReader
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareNotificationRDF
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class TypedShareNotificationTest {

    private val owner = "https://alice.pod/profile/card#me"
    private val receiver = "https://bob.pod/profile/card#me"
    private val receiverInbox = "https://bob.pod/inbox/"
    private val resource = "https://alice.pod/tickets/u1/"

    private class CapturingTransport : NotificationTransport {
        var lastBody: String? = null
        var lastSlug: String? = null

        override suspend fun discoverInbox(webId: String): SolidResult<String?> =
            SolidResult.Success(null)

        override suspend fun post(
            webId: String,
            inbox: String,
            contentType: String,
            body: ByteArray,
            slug: String?,
        ): SolidResult<String?> {
            lastBody = String(body, Charsets.UTF_8)
            lastSlug = slug
            return SolidResult.Success(null)
        }

        override suspend fun list(webId: String, inbox: String): SolidResult<List<RawNotification>> =
            SolidResult.Success(emptyList())

        override suspend fun read(webId: String, notificationUri: String): SolidResult<RawNotification> =
            SolidResult.Failure(SolidError.fromHttp(404, "not exercised"))

        override suspend fun delete(webId: String, notificationUri: String): SolidResult<Boolean> =
            SolidResult.Success(false)

        override suspend fun subscribe(
            webId: String,
            resourceUri: String,
        ): SolidResult<Flow<RawNotification>> =
            SolidResult.Failure(SolidError.fromHttp(404, "not exercised"))
    }

    private fun notifier(transport: CapturingTransport): InboxNotifier {
        val rm = FakeSolidResourceManager(
            onRead = { uri ->
                if (uri == receiver) {
                    SolidResult.Success(
                        WebId(receiver, listOf(RdfQuad(receiver, LDP.INBOX, receiverInbox))),
                    )
                } else {
                    SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
            onReadPublic = { uri ->
                if (uri == receiver) {
                    SolidResult.Success(
                        WebId(receiver, listOf(RdfQuad(receiver, LDP.INBOX, receiverInbox))),
                    )
                } else {
                    SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
            onHead = { SolidResult.Failure(SolidError.fromHttp(404, "no head")) },
        )
        return InboxNotifier(transport, InboxDiscovery(rm), SolidShareNotificationProfile)
    }

    @Test
    fun `a typed offer describes its object with rdf type and schema name`() = runBlocking {
        val transport = CapturingTransport()

        notifier(transport).postOffer(
            owner, receiver, URI.create(resource), ShareMode.READ,
            resourceType = Schema.TICKET,
            resourceName = "Coldplay — Music of the Spheres",
        )

        val body = requireNotNull(transport.lastBody)
        assertTrue(body.contains("<$resource>"))
        assertTrue(body.contains("<${Schema.TICKET}>"))
        assertTrue(body.contains("<${Schema.NAME}>"))
        assertTrue(body.contains("Coldplay — Music of the Spheres"))
    }

    @Test
    fun `an untyped offer carries no object description`() = runBlocking {
        val transport = CapturingTransport()

        notifier(transport).postOffer(owner, receiver, URI.create(resource), ShareMode.READ)

        val body = requireNotNull(transport.lastBody)
        assertFalse(body.contains(Schema.TICKET))
        assertFalse(body.contains(Schema.NAME))
    }

    @Test
    fun `the reader surfaces the object's type and name on the parsed notification`() = runBlocking {
        val readerWebId = "https://bob.pod/profile/card#me"
        val inboxUri = "https://bob.pod/inbox/"
        val itemUri = "https://bob.pod/inbox/offer1"
        val sharedResource = "https://alice.pod/tickets/u1/"

        val fake = FakeSolidResourceManager(
            onRead = { uri ->
                when (uri) {
                    readerWebId -> SolidResult.Success(
                        WebId(readerWebId, listOf(RdfQuad(readerWebId, LDP.INBOX, inboxUri))),
                    )

                    inboxUri -> SolidResult.Success(
                        SolidContainer(
                            inboxUri,
                            "application/ld+json",
                            listOf(RdfQuad(inboxUri, LDP.CONTAINS, itemUri)),
                        ),
                    )

                    itemUri -> SolidResult.Success(
                        ShareNotificationRDF(
                            itemUri,
                            quads = listOf(
                                RdfQuad("$itemUri#offer", RDF.TYPE, AS.OFFER),
                                RdfQuad("$itemUri#offer", AS.ACTOR, owner),
                                RdfQuad("$itemUri#offer", AS.OBJECT, sharedResource),
                                RdfQuad("$itemUri#offer", ACL.MODE, ACL.READ),
                                RdfQuad(sharedResource, RDF.TYPE, Schema.TICKET),
                                RdfQuad(sharedResource, Schema.NAME, "Coldplay"),
                            ),
                        ),
                    )

                    else -> SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
            onReadPublic = { uri ->
                SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
            },
            onHead = { uri ->
                if (uri == sharedResource) {
                    SolidResult.Success(SolidMetadata.EMPTY.copy(ownerUri = owner))
                } else {
                    SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
        )

        val notifications = InboxReader(fake, InboxDiscovery(fake), SolidShareNotificationProfile)
            .listNotifications(readerWebId)

        val offer = notifications.single()
        assertEquals(Schema.TICKET, offer.resourceType)
        assertEquals("Coldplay", offer.resourceName)
    }
}
