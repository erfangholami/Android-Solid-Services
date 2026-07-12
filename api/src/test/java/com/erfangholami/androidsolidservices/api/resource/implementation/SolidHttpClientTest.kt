package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.auth.implementation.AuthSession
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.runBlocking
import net.openid.appauth.TokenResponse
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.net.URI

/**
 * Network-behaviour tests for [SolidHttpClient] driven with a real
 * [MockWebServer]. Covers the DPoP-nonce retry, the expired-token
 * force-refresh, conditional-write status handling, redirects, and that the
 * auth headers assembled by [AuthSession] actually reach the wire.
 *
 * The response cache is disabled so every logical call maps to a deterministic
 * network exchange.
 */
class SolidHttpClientTest {

    /**
     * Records nonce updates and refresh calls, and returns canned auth headers.
     * The [TokenResponse] it hands back is only null-checked by the client, so a
     * bare mock suffices.
     */
    private class FakeAuthSession : AuthSession {
        val recordedNonces = mutableListOf<String>()
        var forceRefreshCount = 0
        var noRefreshCount = 0
        private var dpop = "proof-initial"

        override suspend fun getLastTokenResponse(
            webId: String,
            forceRefresh: Boolean,
        ): TokenResponse? {
            if (forceRefresh) forceRefreshCount++ else noRefreshCount++
            return mock(TokenResponse::class.java)
        }

        override suspend fun getAuthHeaders(
            webId: String,
            httpMethod: String,
            uri: String,
        ): Map<String, String> = mapOf(
            "Authorization" to "DPoP token-123",
            "DPoP" to dpop,
        )

        override fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String) {
            recordedNonces += nonce
            dpop = "proof-with-nonce-$nonce"
        }
    }

    private val webId = "https://alice.pod/profile/card#me"
    private lateinit var server: MockWebServer
    private lateinit var fake: FakeAuthSession
    private lateinit var client: SolidHttpClient

    @Before
    fun setUp() {
        SolidHttpClient.cacheEnabled = false
        server = MockWebServer().apply { start() }
        fake = FakeAuthSession()
        client = SolidHttpClient(auth = fake, httpClient = OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
        SolidHttpClient.cacheEnabled = true
    }

    private fun url(path: String): URI = server.url(path).toUri()

    @Test
    fun `a use_dpop_nonce 401 is retried with the nonce and no token refresh`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("WWW-Authenticate", "DPoP error=\"use_dpop_nonce\"")
                .addHeader("DPoP-Nonce", "nonce-abc"),
        )
        server.enqueue(MockResponse().setResponseCode(200))

        val result = runBlocking { client.delete(webId, url("/r")) }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)
        assertTrue("the nonce should have been recorded", "nonce-abc" in fake.recordedNonces)
        assertEquals("a nonce challenge must not force a token refresh", 0, fake.forceRefreshCount)

        server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("proof-with-nonce-nonce-abc", retried.getHeader("DPoP"))
    }

    @Test
    fun `an expired_token 401 triggers one force-refresh then succeeds`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("WWW-Authenticate", "DPoP error=\"expired_token\""),
        )
        server.enqueue(MockResponse().setResponseCode(200))

        val result = runBlocking { client.delete(webId, url("/r")) }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)
        assertEquals(1, fake.forceRefreshCount)
    }

    @Test
    fun `a persistent auth failure is returned after a single refresh`() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .addHeader("WWW-Authenticate", "DPoP error=\"expired_token\""),
        )
        server.enqueue(
            MockResponse().setResponseCode(401)
                .addHeader("WWW-Authenticate", "DPoP error=\"expired_token\""),
        )

        val result = runBlocking { client.delete(webId, url("/r")) }

        assertTrue(result is SolidResult.Failure)
        assertEquals(401, (result as SolidResult.Failure).error.httpStatus)
        assertEquals("must not retry endlessly on a real auth failure", 2, server.requestCount)
        assertEquals(1, fake.forceRefreshCount)
    }

    @Test
    fun `a 412 on putRaw surfaces as Error 412 and sends a quoted If-Match`() {
        server.enqueue(MockResponse().setResponseCode(412))

        val result = runBlocking {
            client.putRaw(webId, url("/r"), "text/turtle", ByteArray(0), ifMatch = "etag1")
        }

        assertTrue(result is SolidResult.Failure)
        assertEquals(412, (result as SolidResult.Failure).error.httpStatus)
        assertEquals(1, server.requestCount)
        assertEquals("\"etag1\"", server.takeRequest().getHeader("If-Match"))
    }

    @Test
    fun `auth headers assembled by the session reach the wire`() {
        server.enqueue(MockResponse().setResponseCode(200))
        runBlocking { client.delete(webId, url("/r")) }

        val request = server.takeRequest()
        assertEquals("DPoP token-123", request.getHeader("Authorization"))
        assertEquals("proof-initial", request.getHeader("DPoP"))
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `a same-origin redirect is followed and re-attaches credentials for the new location`() {
        server.enqueue(
            MockResponse().setResponseCode(307).addHeader("Location", server.url("/moved").toString()),
        )
        server.enqueue(MockResponse().setResponseCode(200))

        val result = runBlocking { client.delete(webId, url("/r")) }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("/moved", followed.path)
        assertEquals("DELETE", followed.method)
        assertEquals(
            "credentials must be re-attached (and DPoP re-signed) on a same-origin hop",
            "DPoP token-123", followed.getHeader("Authorization"),
        )
        assertEquals("proof-initial", followed.getHeader("DPoP"))
    }

    @Test
    fun `a cross-origin redirect is followed without forwarding credentials`() {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(
                MockResponse().setResponseCode(308).addHeader("Location", other.url("/r").toString()),
            )
            other.enqueue(MockResponse().setResponseCode(200))

            val result = runBlocking { client.delete(webId, url("/r")) }

            assertTrue(result is SolidResult.Success)
            server.takeRequest()
            val followed = other.takeRequest()
            assertNull(
                "a cross-origin hop must not forward the Authorization header",
                followed.getHeader("Authorization"),
            )
            assertNull("nor the DPoP proof", followed.getHeader("DPoP"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `a 415 on a sparql-update PATCH falls back to a text-n3 PATCH`() {
        server.enqueue(MockResponse().setResponseCode(415))
        server.enqueue(MockResponse().setResponseCode(205))

        val patch = N3Patch.build {
            insert("https://alice.pod/r#it", "https://example.org/p", "https://example.org/o")
        }
        val result = runBlocking { client.patch(webId, url("/r"), patch) }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)
        assertTrue(
            "first attempt uses application/sparql-update",
            server.takeRequest().getHeader("Content-Type").orEmpty().startsWith("application/sparql-update"),
        )
        assertTrue(
            "the 415 retry falls back to text/n3",
            server.takeRequest().getHeader("Content-Type").orEmpty().startsWith("text/n3"),
        )
    }

    @Test
    fun `post returns the server-allocated Location URI`() {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .addHeader("Location", "https://alice.pod/shared/new-item"),
        )

        val result = runBlocking {
            client.post(webId, url("/shared/"), "text/turtle", "data".toByteArray())
        }

        assertTrue(result is SolidResult.Success)
        assertEquals(
            URI.create("https://alice.pod/shared/new-item"),
            (result as SolidResult.Success).value,
        )
    }
}
