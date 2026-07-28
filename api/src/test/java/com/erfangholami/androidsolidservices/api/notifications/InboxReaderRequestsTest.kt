package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxReader
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareRequestRDF
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SAI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class InboxReaderRequestsTest {

    private val readerWebId = "https://alice.pod/profile/card#me"
    private val readerStorage = "https://alice.pod/"
    private val inboxUri = "https://alice.pod/inbox/"
    private val itemUri = "https://alice.pod/inbox/req1"
    private val requester = "https://bob.pod/profile/card#me"

    private fun readerProfile() = WebId(
        readerWebId,
        listOf(
            RdfQuad(readerWebId, LDP.INBOX, inboxUri),
            RdfQuad(readerWebId, PIM.STORAGE, readerStorage),
        ),
    )

    private fun inboxContainer(items: List<String>) = SolidContainer(
        inboxUri,
        "application/ld+json",
        items.map { RdfQuad(inboxUri, LDP.CONTAINS, it) },
    )

    private fun request(resourceUri: String, typed: Boolean = true) = ShareRequestRDF(
        itemUri,
        quads = buildList {
            if (typed) add(RdfQuad("$itemUri#req", RDF.TYPE, SAI.ACCESS_REQUEST))
            add(RdfQuad("$itemUri#req", AS.ACTOR, requester))
            add(RdfQuad("$itemUri#req", AS.OBJECT, resourceUri))
            add(RdfQuad("$itemUri#req", ACL.MODE, ACL.READ))
        },
    )

    private fun reader(resourceUri: String, typed: Boolean = true): InboxReader {
        val fake = FakeSolidResourceManager(
            onRead = { uri ->
                when (uri.toString()) {
                    readerWebId -> SolidResult.Success(readerProfile())
                    inboxUri -> SolidResult.Success(inboxContainer(listOf(itemUri)))
                    itemUri -> SolidResult.Success(request(resourceUri, typed))
                    else -> SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
            onReadPublic = { uri ->
                if (uri.toString() == readerWebId) {
                    SolidResult.Success(readerProfile())
                } else {
                    SolidResult.Failure(SolidError.fromHttp(404, "not found: $uri"))
                }
            },
        )
        return InboxReader(fake, InboxDiscovery(fake), SolidShareNotificationProfile)
    }

    @Test
    fun `a request for the reader's own resource is surfaced`() = runBlocking {
        val resource = "https://alice.pod/photos/summer/"
        val requests = reader(resource).listRequests(readerWebId)
        assertEquals(listOf(resource), requests.map { it.resourceUri })
        assertEquals(requester, requests.single().requesterWebId)
        assertEquals(ShareMode.READ, requests.single().requestedMode)
    }

    @Test
    fun `a request for a resource the reader does not own is dropped`() = runBlocking {
        val foreign = "https://carol.pod/private/thing"
        val requests = reader(foreign).listRequests(readerWebId)
        assertEquals(emptyList<Any>(), requests.map { it.resourceUri })
    }

    @Test
    fun `an item without an AccessRequest type is ignored`() = runBlocking {
        val resource = "https://alice.pod/photos/summer/"
        val requests = reader(resource, typed = false).listRequests(readerWebId)
        assertEquals(emptyList<Any>(), requests.map { it.resourceUri })
    }
}
