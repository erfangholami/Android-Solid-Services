package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketRDF
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Regression tests for the codec `init` blocks: constructing a codec over a
 * parsed document must NOT strip `rdf:type` triples another application wrote
 * on the primary subject. Before `ensureType`, the single-valued `addQuad`
 * clobbered every foreign type.
 */
class CodecForeignTypeTest {

    private val contactUri =
        URI.create("https://alice.pod/contacts/b1/Person/p1/index.ttl#this")
    private val ticketUri = URI.create("https://alice.pod/tickets/abc.ttl#this")

    @Test
    fun `ContactRDF keeps a foreign rdf-type already on the subject`() {
        val subject = contactUri.toString()
        val contact = ContactRDF(
            contactUri,
            quads = listOf(
                RdfQuad(subject, RDF.TYPE, FOAF.PERSON),
                RdfQuad(subject, VCARD.FN, "Jane", datatype = "http://www.w3.org/2001/XMLSchema#string"),
            ),
        )
        val types = contact.findAllPropertiesForSubject(subject, RDF.TYPE)
        assertTrue("vcard:Individual must be present", types.contains(VCARD.INDIVIDUAL))
        assertTrue("foaf:Person must be preserved", types.contains(FOAF.PERSON))
    }

    @Test
    fun `ContactRDF does not duplicate its own type`() {
        val subject = contactUri.toString()
        val contact = ContactRDF(
            contactUri,
            quads = listOf(RdfQuad(subject, RDF.TYPE, VCARD.INDIVIDUAL)),
        )
        assertEquals(
            listOf(VCARD.INDIVIDUAL),
            contact.findAllPropertiesForSubject(subject, RDF.TYPE),
        )
    }

    @Test
    fun `foreign type survives a full serialize-reparse round trip`() {
        val subject = contactUri.toString()
        val original = ContactRDF(
            contactUri,
            quads = listOf(
                RdfQuad(subject, RDF.TYPE, FOAF.PERSON),
                RdfQuad(subject, VCARD.FN, "Jane", datatype = "http://www.w3.org/2001/XMLSchema#string"),
            ),
        )
        val json = original.getEntity().bufferedReader().use { it.readText() }
        val quads = RDFResource.parseJsonLd(json, contactUri.toString().substringBefore('#'))
        val reparsed = ContactRDF(contactUri, quads = quads)
        val types = reparsed.findAllPropertiesForSubject(subject, RDF.TYPE)
        assertTrue(types.contains(VCARD.INDIVIDUAL))
        assertTrue(types.contains(FOAF.PERSON))
    }

    @Test
    fun `TicketRDF keeps a foreign rdf-type already on the subject`() {
        val subject = ticketUri.toString()
        val ticket = TicketRDF(
            ticketUri,
            quads = listOf(
                RdfQuad(subject, RDF.TYPE, "https://schema.org/Event"),
                RdfQuad(subject, Schema.NAME, "Show", datatype = "http://www.w3.org/2001/XMLSchema#string"),
            ),
        )
        val types = ticket.findAllPropertiesForSubject(subject, RDF.TYPE)
        assertTrue(types.contains(Schema.TICKET))
        assertTrue(types.contains("https://schema.org/Event"))
    }
}
