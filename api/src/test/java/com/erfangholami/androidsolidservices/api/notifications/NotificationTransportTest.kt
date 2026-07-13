package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.auth.implementation.AuthSession
import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationTransportImplementation
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.Notify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class NotificationTransportTest {

    private val fakeAuth = object : AuthSession {
        override suspend fun getLastTokenResponse(webId: String, forceRefresh: Boolean): TokenResponse? =
            mock(TokenResponse::class.java)

        override suspend fun getAuthHeaders(webId: String, httpMethod: String, uri: String): Map<String, String> =
            emptyMap()

        override fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String) = Unit
    }

    private fun transport(fake: FakeSolidResourceManager): NotificationTransport =
        NotificationTransportImplementation.create(fake, InboxDiscovery(fake), Dispatchers.Unconfined)

    private fun authedTransport(fake: FakeSolidResourceManager): NotificationTransport =
        NotificationTransportImplementation.create(fake, InboxDiscovery(fake), Dispatchers.Unconfined, fakeAuth)

    @Test
    fun `post returns the created location on success`() = runBlocking {
        val location = "https://bob.pod/inbox/123"
        val fake = FakeSolidResourceManager(onPost = { SolidResult.Success(location) })

        val result = transport(fake).post(
            webId = "https://alice.pod/card#me",
            inbox = "https://bob.pod/inbox/",
            contentType = HTTPAcceptType.TURTLE,
            body = ByteArray(0),
            slug = "offer",
        )

        assertEquals(SolidResult.Success(location.toString()), result)
    }

    @Test
    fun `post propagates the server http error code`() = runBlocking {
        val fake = FakeSolidResourceManager(onPost = { SolidResult.Failure(SolidError.fromHttp(403, "Forbidden")) })

        val result = transport(fake).post(
            webId = "https://alice.pod/card#me",
            inbox = "https://bob.pod/inbox/",
            contentType = HTTPAcceptType.TURTLE,
            body = ByteArray(0),
            slug = null,
        )

        assertTrue(result is SolidResult.Failure && result.error.httpStatus == 403)
    }

    @Test
    fun `delete delegates to the resource manager`() = runBlocking {
        val fake = FakeSolidResourceManager(onDelete = { SolidResult.Success(true) })

        val result = transport(fake).delete("https://alice.pod/card#me", "https://alice.pod/inbox/1")

        assertEquals(SolidResult.Success(true), result)
    }

    @Test
    fun `subscribe without an authenticated session fails`() = runBlocking {
        val result = transport(FakeSolidResourceManager())
            .subscribe("https://alice.pod/card#me", "https://alice.pod/doc")

        assertTrue(result is SolidResult.Failure)
    }

    @Test
    fun `subscribe fails when the pod advertises no WebSocket subscription service`() = runBlocking {
        val storageDesc = "https://alice.pod/.well-known/solid"
        val service = "https://alice.pod/.notifications/EventSourceChannel2023/"
        val quads = listOf(
            RdfQuad("https://alice.pod/", Notify.SUBSCRIPTION, service, null, null),
            RdfQuad(service, Notify.CHANNEL_TYPE, Notify.EVENT_SOURCE_CHANNEL_2023, null, null),
        )
        val fake = FakeSolidResourceManager(
            onHead = { SolidResult.Success(SolidMetadata.EMPTY.copy(storageDescriptionUri = storageDesc)) },
            onRead = { SolidResult.Success(SolidRDFResource(it, quads, null)) },
        )

        val result = authedTransport(fake).subscribe("https://alice.pod/card#me", "https://alice.pod/doc")

        assertTrue("only WebSocketChannel2023 services count", result is SolidResult.Failure)
    }

    @Test
    fun `list surfaces an inbox read error without throwing`() = runBlocking {
        val fake = FakeSolidResourceManager(onRead = { SolidResult.Failure(SolidError.fromHttp(403, "Forbidden")) })

        val result = transport(fake).list("https://alice.pod/card#me", "https://alice.pod/inbox/")

        assertTrue(result is SolidResult.Failure && result.error.httpStatus == 403)
    }

    @Test
    fun `discoverInbox finds an inbox advertised via a HEAD link`() = runBlocking {
        val inbox = "https://alice.pod/inbox/"
        val fake = FakeSolidResourceManager(
            onRead = { SolidResult.Failure(SolidError.fromThrowable(RuntimeException("profile has no body"))) },
            onHead = { SolidResult.Success(SolidMetadata.EMPTY.copy(inboxUri = inbox)) },
        )

        val result = transport(fake).discoverInbox("https://alice.pod/profile/card#me")

        assertEquals(SolidResult.Success(inbox.toString()), result)
    }

    @Test
    fun `discoverInbox returns null when nothing is advertised`() = runBlocking {
        val fake = FakeSolidResourceManager(
            onRead = { SolidResult.Failure(SolidError.fromThrowable(RuntimeException("profile has no body"))) },
            onHead = { SolidResult.Success(SolidMetadata.EMPTY) },
        )

        val result = transport(fake).discoverInbox("https://alice.pod/profile/card#me")

        assertEquals(SolidResult.Success<String?>(null), result)
    }
}
