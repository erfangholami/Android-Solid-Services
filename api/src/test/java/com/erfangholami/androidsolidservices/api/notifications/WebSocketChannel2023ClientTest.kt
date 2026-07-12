package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.auth.implementation.AuthSession
import com.erfangholami.androidsolidservices.api.notifications.implementation.WebSocketChannel2023Client
import com.erfangholami.androidsolidservices.shared.vocab.AS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.openid.appauth.TokenResponse
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.net.URI

/**
 * Tests the `WebSocketChannel2023` client end-to-end against a real [MockWebServer]: the channel
 * negotiation reads `notify:receiveFrom` from the response, and the opened WebSocket decodes each
 * pushed frame into a [RawNotification].
 */
class WebSocketChannel2023ClientTest {

    private val webId = "https://alice.pod/profile/card#me"

    private class FakeAuthSession : AuthSession {
        override suspend fun getLastTokenResponse(webId: String, forceRefresh: Boolean): TokenResponse? =
            mock(TokenResponse::class.java)

        override suspend fun getAuthHeaders(webId: String, httpMethod: String, uri: String): Map<String, String> =
            mapOf("Authorization" to "DPoP token", "DPoP" to "proof")

        override fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String) = Unit
    }

    private lateinit var server: MockWebServer
    private lateinit var client: WebSocketChannel2023Client

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = WebSocketChannel2023Client(FakeAuthSession(), Dispatchers.IO, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `negotiate reads receiveFrom from the channel response`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                { "@context": ["https://www.w3.org/ns/solid/notifications-context/v1"],
                  "type": "WebSocketChannel2023",
                  "topic": "https://alice.pod/doc",
                  "receiveFrom": "wss://alice.pod/ws/abc" }
                """.trimIndent(),
            ),
        )

        val receiveFrom = runBlocking {
            client.negotiate(webId, server.url("/subscription").toUri(), URI.create("https://alice.pod/doc"))
        }

        assertEquals(URI.create("wss://alice.pod/ws/abc"), receiveFrom)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue("the channel request names the topic", request.body.readUtf8().contains("https://alice.pod/doc"))
    }

    @Test
    fun `connect decodes each pushed frame into a RawNotification`() {
        val frame = """
            { "@id": "urn:uuid:n1",
              "@type": "${AS.NAMESPACE}Update",
              "${AS.NAMESPACE}object": { "@id": "https://alice.pod/doc" } }
        """.trimIndent()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send(frame)
                }
            }),
        )

        val notification = runBlocking {
            withTimeout(5_000) {
                client.connect(webId, server.url("/ws").toUri()).first()
            }
        }

        assertTrue("carries the AS Update type", notification.types.contains(AS.UPDATE))
        assertEquals("https://alice.pod/doc", notification.`object`)
    }
}
