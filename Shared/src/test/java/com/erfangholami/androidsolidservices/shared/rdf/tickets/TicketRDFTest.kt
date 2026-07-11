package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class TicketRDFTest {

    private val ticketUri = URI.create("https://alice.pod/tickets/abc123.ttl#this")

    private fun fullTicket(): TicketRDF = TicketRDF(ticketUri).apply {
        setTitle("Coldplay — Music of the Spheres")
        setDescription("Gate opens 18:00")
        setTicketNumber("TKT-0042")
        setTicketToken("c3RhZGl1bS10aWNrZXQ=")
        setBarcodeFormat(TicketBarcodeFormat.AZTEC)
        setCategory(TicketCategory.EVENT)
        setIssuerName("Ticketmaster")
        setUnderName("Erfan Gholami")
        setSeat(TicketSeat(seatNumber = "27", seatRow = "F", seatSection = "B12"))
        setTotalPrice("89.50")
        setPriceCurrency("EUR")
        setDateIssued("2026-06-01T10:00:00Z")
        setEvent(
            TicketEvent(
                name = "Music of the Spheres Tour",
                startDate = "2026-07-14T19:30:00Z",
                endDate = "2026-07-14T23:00:00Z",
                location = TicketPlace(
                    name = "Johan Cruijff ArenA",
                    address = "Arena Boulevard 1, Amsterdam",
                ),
            )
        )
        setValidFrom("2026-07-14T17:00:00Z")
        setValidThrough("2026-07-14T23:59:00Z")
        setSource(TicketSource.PKPASS)
        setArtifactUri("https://alice.pod/tickets/abc123.pkpass")
        setCreated("2026-07-02T09:15:00Z")
        setModified("2026-07-02T09:15:00Z")
    }

    private fun assertFullTicket(ticket: TicketRDF) {
        assertEquals("Coldplay — Music of the Spheres", ticket.getTitle())
        assertEquals("Gate opens 18:00", ticket.getDescription())
        assertEquals("TKT-0042", ticket.getTicketNumber())
        assertEquals("c3RhZGl1bS10aWNrZXQ=", ticket.getTicketToken())
        assertEquals(TicketBarcodeFormat.AZTEC, ticket.getBarcodeFormat())
        assertEquals(TicketCategory.EVENT, ticket.getCategory())
        assertEquals("Ticketmaster", ticket.getIssuerName())
        assertEquals("Erfan Gholami", ticket.getUnderName())
        assertEquals(TicketSeat("27", "F", "B12"), ticket.getSeat())
        assertEquals("89.50", ticket.getTotalPrice())
        assertEquals("EUR", ticket.getPriceCurrency())
        assertEquals("2026-06-01T10:00:00Z", ticket.getDateIssued())
        assertEquals(
            TicketEvent(
                name = "Music of the Spheres Tour",
                startDate = "2026-07-14T19:30:00Z",
                endDate = "2026-07-14T23:00:00Z",
                location = TicketPlace("Johan Cruijff ArenA", "Arena Boulevard 1, Amsterdam"),
            ),
            ticket.getEvent(),
        )
        assertEquals("2026-07-14T17:00:00Z", ticket.getValidFrom())
        assertEquals("2026-07-14T23:59:00Z", ticket.getValidThrough())
        assertEquals(TicketSource.PKPASS, ticket.getSource())
        assertEquals("https://alice.pod/tickets/abc123.pkpass", ticket.getArtifactUri())
        assertEquals("2026-07-02T09:15:00Z", ticket.getCreated())
        assertEquals("2026-07-02T09:15:00Z", ticket.getModified())
    }

    @Test
    fun `every field round-trips in memory`() {
        assertFullTicket(fullTicket())
    }

    @Test
    fun `every field survives a serialize-parse wire round-trip`() {
        val jsonLd = fullTicket().getEntity().bufferedReader().use { it.readText() }
        val quads = RDFResource.parseJsonLd(jsonLd, ticketUri.toString().substringBefore('#'))
        assertFullTicket(TicketRDF(ticketUri, quads = quads))
    }

    @Test
    fun `model createFromRdf maps every field`() {
        val model = Ticket.createFromRdf(fullTicket())
        assertEquals(ticketUri.toString(), model.uri)
        assertEquals("Coldplay — Music of the Spheres", model.title)
        assertEquals(TicketBarcodeFormat.AZTEC, model.barcodeFormat)
        assertEquals(TicketCategory.EVENT, model.category)
        assertEquals("Ticketmaster", model.issuerName)
        assertEquals("Johan Cruijff ArenA", model.event?.location?.name)
        assertEquals(TicketSource.PKPASS, model.source)
        assertEquals("2026-07-02T09:15:00Z", model.createdAt)
    }

    @Test
    fun `clearing optional fields removes their nodes`() {
        val ticket = fullTicket()
        ticket.setDescription(null)
        ticket.setSeat(null)
        ticket.setEvent(null)
        ticket.setIssuerName(null)
        ticket.setUnderName(null)
        ticket.setArtifactUri(null)
        assertNull(ticket.getDescription())
        assertNull(ticket.getSeat())
        assertNull(ticket.getEvent())
        assertNull(ticket.getIssuerName())
        assertNull(ticket.getUnderName())
        assertNull(ticket.getArtifactUri())
        val orphaned = ticket.getAllQuads().filter {
            it.subject.endsWith("#seat") || it.subject.endsWith("#event") ||
                    it.subject.endsWith("#place") || it.subject.endsWith("#issuer") ||
                    it.subject.endsWith("#underName")
        }
        assertEquals(emptyList<Any>(), orphaned)
    }

    @Test
    fun `unknown stored enum values fall back to defaults`() {
        val ticket = TicketRDF(ticketUri).apply {
            setTitle("Mystery")
            addQuadLiteral(ticketUri.toString(), SolidShare.CATEGORY, "HOVERCRAFT", XSD.STRING)
            addQuadLiteral(ticketUri.toString(), SolidShare.BARCODE_FORMAT, "TATTOO", XSD.STRING)
            addQuadLiteral(ticketUri.toString(), SolidShare.SOURCE, "TELEPATHY", XSD.STRING)
        }
        assertEquals(TicketCategory.GENERIC, ticket.getCategory())
        assertEquals(TicketBarcodeFormat.NONE, ticket.getBarcodeFormat())
        assertEquals(TicketSource.MANUAL, ticket.getSource())
    }

    @Test
    fun `setters replace rather than accumulate`() {
        val ticket = fullTicket()
        ticket.setTitle("New title")
        ticket.setSeat(TicketSeat(seatNumber = "1"))
        ticket.setEvent(TicketEvent(name = "Only a name"))
        assertEquals("New title", ticket.getTitle())
        assertEquals(TicketSeat(seatNumber = "1"), ticket.getSeat())
        assertEquals(TicketEvent(name = "Only a name"), ticket.getEvent())
    }
}
