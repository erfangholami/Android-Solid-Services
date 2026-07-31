package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.auth.implementation.AuthSession
import com.erfangholami.androidsolidservices.shared.model.resource.NonRDFResource
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

class SolidHttpClientTest {

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
    fun `getPublic follows a 303 to the document it names`() {
        server.enqueue(
            MockResponse().setResponseCode(303).addHeader("Location", server.url("/card?lookup").toString()),
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "text/plain")
                .setBody("hello"),
        )

        val result = runBlocking { client.getPublic(url("/card"), NonRDFResource::class.java) }

        assertTrue(
            "an anonymous read must chase the redirect identity hosts serve profiles behind, got $result",
            result is SolidResult.Success,
        )
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("/card?lookup", followed.path)
        assertNull("the public path carries no credentials", followed.getHeader("Authorization"))
    }

    @Test
    fun `headPublic follows a redirect and keeps the HEAD method`() {
        server.enqueue(
            MockResponse().setResponseCode(301).addHeader("Location", server.url("/moved").toString()),
        )
        server.enqueue(MockResponse().setResponseCode(200))

        val result = runBlocking { client.headPublic(url("/r")) }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("HEAD", server.takeRequest().method)
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
    fun `getStream returns the response body as a live stream`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "text/plain").setBody("streamed body"),
        )

        val body = runBlocking {
            client.getStream(webId, url("/r")).getOrThrow().use { it.stream().readBytes().decodeToString() }
        }

        assertEquals("streamed body", body)
    }

    @Test
    fun `putStream streams the body, reports progress, and re-opens the source on a nonce retry`() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .addHeader("WWW-Authenticate", "DPoP error=\"use_dpop_nonce\"")
                .addHeader("DPoP-Nonce", "n1"),
        )
        server.enqueue(MockResponse().setResponseCode(201))

        var opens = 0
        val progress = mutableListOf<Long>()
        val result = runBlocking {
            client.putStream(
                webId, url("/r"), "text/plain",
                contentLength = 5L, ifMatch = null, onProgress = { written, _ -> progress += written },
            ) {
                opens++
                "hello".byteInputStream()
            }
        }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)
        assertEquals("the source is re-opened for the retried request", 2, opens)
        assertTrue("progress was reported", progress.isNotEmpty())
        server.takeRequest()
        assertEquals("the streamed body reaches the server", "hello", server.takeRequest().body.readUtf8())
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

    private val n3PatchBody = """
        @prefix solid: <http://www.w3.org/ns/solid/terms#> .
        _:p a solid:InsertDeletePatch ;
          solid:inserts { <https://a.example/x> <http://schema.org/name> "hello" . } .
    """.trimIndent()

    @Test
    fun `patchRaw retries as SPARQL Update when the server refuses N3`() {
        server.enqueue(MockResponse().setResponseCode(415))
        server.enqueue(MockResponse().setResponseCode(205))

        val result = runBlocking { client.patchRaw(webId, url("/r"), n3PatchBody) }

        assertTrue(result is SolidResult.Success)
        assertEquals(2, server.requestCount)

        val first = server.takeRequest()
        assertTrue(first.getHeader("Content-Type").orEmpty().startsWith("text/n3"))

        val second = server.takeRequest()
        assertTrue(
            "the retry must restate the patch as SPARQL Update",
            second.getHeader("Content-Type").orEmpty().startsWith("application/sparql-update"),
        )
        val body = second.body.readUtf8()
        assertTrue(body.contains("INSERT DATA {"))
        assertTrue(body.contains("\"hello\""))
    }

    @Test
    fun `patchRaw keeps the 415 when the body cannot be translated`() {
        server.enqueue(MockResponse().setResponseCode(415))

        val result = runBlocking { client.patchRaw(webId, url("/r"), "not an N3 patch document") }

        assertTrue(result is SolidResult.Failure)
        assertEquals(415, (result as SolidResult.Failure).error.httpStatus)
        assertEquals("nothing to retry with — one request only", 1, server.requestCount)
    }

    @Test
    fun `patchRaw does not reinterpret other failures as format problems`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = runBlocking { client.patchRaw(webId, url("/r"), n3PatchBody) }

        assertTrue(result is SolidResult.Failure)
        assertEquals(403, (result as SolidResult.Failure).error.httpStatus)
        assertEquals("a denial is not a media-type negotiation", 1, server.requestCount)
    }
}
