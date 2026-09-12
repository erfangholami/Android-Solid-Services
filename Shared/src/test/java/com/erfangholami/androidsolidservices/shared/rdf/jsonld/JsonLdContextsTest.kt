package com.erfangholami.androidsolidservices.shared.rdf.jsonld

import com.apicatalog.jsonld.JsonLdError
import com.apicatalog.jsonld.JsonLdErrorCode
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.loader.DocumentLoader
import com.apicatalog.jsonld.loader.DocumentLoaderOptions
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.vocab.AS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class JsonLdContextsTest {

    private val offline = DocumentLoader { url, _ ->
        throw JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED, "no network for $url")
    }

    @Test
    fun `the Activity Streams context is answered from the bundle`() {
        val loader = JsonLdContexts.loader(offline)

        val document = loader.loadDocument(URI("https://www.w3.org/ns/activitystreams"), DocumentLoaderOptions())

        val json = (document as JsonDocument).jsonContent.get().asJsonObject()
        assertTrue(json.containsKey("@context"))
        assertEquals(URI("https://www.w3.org/ns/activitystreams"), document.documentUrl)
    }

    @Test
    fun `spelling variants of the Activity Streams IRI hit the same bundle`() {
        assertNotNull(JsonLdContexts.bundledDocument(URI("http://www.w3.org/ns/activitystreams")))
        assertNotNull(JsonLdContexts.bundledDocument(URI("https://www.w3.org/ns/activitystreams.jsonld")))
        assertNull(JsonLdContexts.bundledDocument(URI("https://example.org/context.jsonld")))
    }

    @Test
    fun `an unknown IRI still goes to the remote loader`() {
        val loader = JsonLdContexts.loader(offline)

        val failure = runCatching { loader.loadDocument(URI("https://example.org/ctx"), DocumentLoaderOptions()) }

        assertTrue(failure.exceptionOrNull() is JsonLdError)
    }

    @Test
    fun `an Activity Streams notification parses without the network`() {
        val quads = RDFResource.parseJsonLd(
            """
            {
              "@context": "https://www.w3.org/ns/activitystreams",
              "id": "https://alice.pod/inbox/offer-1",
              "type": "Offer",
              "actor": "https://alice.pod/profile/card#me",
              "object": "https://alice.pod/photos/"
            }
            """.trimIndent(),
            "https://alice.pod/inbox/offer-1",
        )

        val objectQuad = quads.single { it.predicate == AS.OBJECT }
        assertEquals("https://alice.pod/inbox/offer-1", objectQuad.subject)
        assertEquals("https://alice.pod/photos/", objectQuad.`object`)
    }
}
