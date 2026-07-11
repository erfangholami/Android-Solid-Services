package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

/**
 * Tests for ticket RDF hygiene: date-only values must not be over-typed as
 * `xsd:dateTime`, and blank seat parts must not create empty literals.
 */
class TicketRDFHygieneTest {

    private val ticketUri = URI.create("https://alice.pod/tickets/abc.ttl#this")

    private fun quadsOf(ticket: TicketRDF): List<RdfQuad> {
        val json = ticket.getEntity().bufferedReader().use { it.readText() }
        return RDFResource.parseJsonLd(json, ticketUri.toString().substringBefore('#'))
    }

    @Test
    fun `date-only validFrom is typed xsd date`() {
        val ticket = TicketRDF(ticketUri).apply {
            setTitle("Show")
            setValidFrom("2026-07-14")
        }
        val quad = quadsOf(ticket).single { it.predicate == Schema.VALID_FROM }
        assertEquals(XSD.DATE, quad.datatype)
    }

    @Test
    fun `validThrough with a time keeps xsd dateTime`() {
        val ticket = TicketRDF(ticketUri).apply {
            setTitle("Show")
            setValidThrough("2026-07-14T23:59:00Z")
        }
        val quad = quadsOf(ticket).single { it.predicate == Schema.VALID_THROUGH }
        assertEquals(XSD.DATE_TIME, quad.datatype)
    }

    @Test
    fun `date-only event start is typed xsd date after round trip`() {
        val ticket = TicketRDF(ticketUri).apply {
            setTitle("Show")
            setEvent(TicketEvent(name = "Concert", startDate = "2026-07-14"))
        }
        val quad = quadsOf(ticket).single { it.predicate == Schema.START_DATE }
        assertEquals(XSD.DATE, quad.datatype)
    }

    @Test
    fun `blank seat parts are dropped and produce no seat node`() {
        val ticket = TicketRDF(ticketUri).apply {
            setTitle("Show")
            setSeat(TicketSeat(seatNumber = "  ", seatRow = "", seatSection = null))
        }
        assertNull("all-blank seat must not be persisted", ticket.getSeat())
        assertEquals(
            "no schema:ticketedSeat link should be written",
            0,
            quadsOf(ticket).count { it.predicate == Schema.TICKETED_SEAT },
        )
    }

    @Test
    fun `a seat with one real part keeps only that part`() {
        val ticket = TicketRDF(ticketUri).apply {
            setTitle("Show")
            setSeat(TicketSeat(seatNumber = "27", seatRow = "   ", seatSection = ""))
        }
        val seat = ticket.getSeat()
        assertEquals("27", seat?.seatNumber)
        assertNull(seat?.seatRow)
        assertNull(seat?.seatSection)
    }
}
