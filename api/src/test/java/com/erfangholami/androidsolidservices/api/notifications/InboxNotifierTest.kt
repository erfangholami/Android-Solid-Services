package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxNotifier
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxPostResult
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationTransportImplementation
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class InboxNotifierTest {

    private val sender = "https://alice.pod/profile/card#me"
    private val receiver = "https://id.inrupt.com/bob"
    private val storage = "https://storage.inrupt.com/abc/"
    private val resource = URI.create("${storage}photos/trip.jpg")

    private fun notifier(fake: FakeSolidResourceManager): InboxNotifier {
        val discovery = InboxDiscovery(fake)
        val transport = NotificationTransportImplementation.create(fake, discovery, Dispatchers.Unconfined)
        return InboxNotifier(transport, discovery, SolidShareNotificationProfile)
    }

    private fun storageOnlyProfile(): WebId =
        WebId(receiver, listOf(RdfQuad(receiver, PIM.STORAGE, storage)))

    private fun privateEverywhere(uri: String, profile: WebId): SolidResult<WebId> =
        if (uri == receiver) SolidResult.Success(profile) else SolidResult.Failure(SolidError.fromHttp(401, "private"))

    @Test
    fun `falls back to the conventional storage inbox when nothing is advertised`() {
        val posted = mutableListOf<String>()
        val fake = FakeSolidResourceManager(
            onReadPublic = { privateEverywhere(it, storageOnlyProfile()) },
            onRead = { SolidResult.Failure(SolidError.fromHttp(403, "private")) },
            onPost = { uri -> posted += uri; SolidResult.Success("${uri}req-1") },
        )

        val result = runBlocking {
            notifier(fake).postRequest(sender, receiver, resource, ShareMode.READ, null)
        }

        assertTrue(result is InboxPostResult.Success)
        assertEquals(listOf("${storage}inbox/"), posted)
    }

    @Test
    fun `a guess that is not accepted is reported as no inbox, not as a refusal`() {
        val fake = FakeSolidResourceManager(
            onReadPublic = { privateEverywhere(it, storageOnlyProfile()) },
            onRead = { SolidResult.Failure(SolidError.fromHttp(403, "private")) },
            onPost = { SolidResult.Failure(SolidError.fromHttp(404, "no such container")) },
        )

        val result = runBlocking {
            notifier(fake).postRequest(sender, receiver, resource, ShareMode.READ, null)
        }

        assertEquals(InboxPostResult.NoInbox(receiver), result)
    }

    @Test
    fun `an advertised inbox that refuses is still a refusal`() {
        val advertised = "https://bob.pod/inbox/"
        val profile = WebId(receiver, listOf(RdfQuad(receiver, LDP.INBOX, advertised)))
        val fake = FakeSolidResourceManager(
            onReadPublic = { SolidResult.Success(profile) },
            onPost = { SolidResult.Failure(SolidError.fromHttp(403, "no")) },
        )

        val result = runBlocking {
            notifier(fake).postOffer(sender, receiver, resource, ShareMode.READ)
        }

        assertEquals(InboxPostResult.Forbidden(advertised), result)
    }

    @Test
    fun `no inbox and no storage means no inbox without a post`() {
        val posted = mutableListOf<String>()
        val fake = FakeSolidResourceManager(
            onReadPublic = { SolidResult.Success(WebId(receiver, emptyList())) },
            onRead = { SolidResult.Failure(SolidError.fromHttp(403, "private")) },
            onPost = { uri -> posted += uri; SolidResult.Success(null) },
        )

        val result = runBlocking {
            notifier(fake).postRequest(sender, receiver, resource, ShareMode.READ, null)
        }

        assertEquals(InboxPostResult.NoInbox(receiver), result)
        assertTrue(posted.isEmpty())
    }
}
