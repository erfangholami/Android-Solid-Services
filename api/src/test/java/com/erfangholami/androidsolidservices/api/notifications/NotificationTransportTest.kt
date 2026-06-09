package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationTransportImplementation
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class NotificationTransportTest {

    private fun transport(fake: FakeSolidResourceManager): NotificationTransport =
        NotificationTransportImplementation.create(fake, InboxDiscovery(fake), Dispatchers.Unconfined)

    @Test
    fun `post returns the created location on success`() = runBlocking {
        val location = URI.create("https://bob.pod/inbox/123")
        val fake = FakeSolidResourceManager(onPost = { SolidNetworkResponse.Success(location) })

        val result = transport(fake).post(
            webId = "https://alice.pod/card#me",
            inbox = "https://bob.pod/inbox/",
            contentType = HTTPAcceptType.TURTLE,
            body = ByteArray(0),
            slug = "offer",
        )

        assertEquals(SolidNetworkResponse.Success(location.toString()), result)
    }

    @Test
    fun `post propagates the server http error code`() = runBlocking {
        val fake = FakeSolidResourceManager(onPost = { SolidNetworkResponse.Error(403, "Forbidden") })

        val result = transport(fake).post(
            webId = "https://alice.pod/card#me",
            inbox = "https://bob.pod/inbox/",
            contentType = HTTPAcceptType.TURTLE,
            body = ByteArray(0),
            slug = null,
        )

        assertTrue(result is SolidNetworkResponse.Error && result.errorCode == 403)
    }

    @Test
    fun `delete delegates to the resource manager`() = runBlocking {
        val fake = FakeSolidResourceManager(onDelete = { SolidNetworkResponse.Success(true) })

        val result = transport(fake).delete("https://alice.pod/card#me", "https://alice.pod/inbox/1")

        assertEquals(SolidNetworkResponse.Success(true), result)
    }

    @Test
    fun `list surfaces an inbox read error without throwing`() = runBlocking {
        val fake = FakeSolidResourceManager(onRead = { SolidNetworkResponse.Error(403, "Forbidden") })

        val result = transport(fake).list("https://alice.pod/card#me", "https://alice.pod/inbox/")

        assertTrue(result is SolidNetworkResponse.Error && result.errorCode == 403)
    }

    @Test
    fun `discoverInbox finds an inbox advertised via a HEAD link`() = runBlocking {
        val inbox = URI.create("https://alice.pod/inbox/")
        val fake = FakeSolidResourceManager(
            onRead = { SolidNetworkResponse.Exception(RuntimeException("profile has no body")) },
            onHead = { SolidNetworkResponse.Success(SolidMetadata.EMPTY.copy(inboxUri = inbox)) },
        )

        val result = transport(fake).discoverInbox("https://alice.pod/profile/card#me")

        assertEquals(SolidNetworkResponse.Success(inbox.toString()), result)
    }

    @Test
    fun `discoverInbox returns null when nothing is advertised`() = runBlocking {
        val fake = FakeSolidResourceManager(
            onRead = { SolidNetworkResponse.Exception(RuntimeException("profile has no body")) },
            onHead = { SolidNetworkResponse.Success(SolidMetadata.EMPTY) },
        )

        val result = transport(fake).discoverInbox("https://alice.pod/profile/card#me")

        assertEquals(SolidNetworkResponse.Success<String?>(null), result)
    }
}
