package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketRDFHygieneTest {

    private val ticketUri = "https://alice.pod/tickets/abc.ttl#this"

    private fun ticket(data: NewTicket): TicketRDF =
        TicketRDF(ticketUri).apply { setTicketData(data) }

    private fun quadsOf(ticket: TicketRDF): List<RdfQuad> {
        val json = ticket.getEntity().bufferedReader().use { it.readText() }
        return RDFResource.parseJsonLd(json, ticketUri.substringBefore('#'))
    }

    @Test
    fun `date-only validFrom is typed xsd date`() {
        val quad = quadsOf(ticket(NewTicket(title = "Show", validFrom = "2026-07-14")))
            .single { it.predicate == Schema.VALID_FROM }
        assertEquals(XSD.DATE, quad.datatype)
    }

    @Test
    fun `validThrough with a time keeps xsd dateTime`() {
        val quad = quadsOf(ticket(NewTicket(title = "Show", validThrough = "2026-07-14T23:59:00Z")))
            .single { it.predicate == Schema.VALID_THROUGH }
        assertEquals(XSD.DATE_TIME, quad.datatype)
    }

    @Test
    fun `date-only event start is typed xsd date after round trip`() {
        val data = NewTicket(
            title = "Show",
            category = TicketCategory.EVENT,
            event = TicketEvent(name = "Concert", startDate = "2026-07-14"),
        )
        val quad = quadsOf(ticket(data)).single { it.predicate == Schema.START_DATE }
        assertEquals(XSD.DATE, quad.datatype)
    }

    @Test
    fun `blank seat parts are dropped and produce no seat node`() {
        val data = NewTicket(
            title = "Show",
            seats = listOf(TicketSeat(seatNumber = "  ", seatRow = "", seatSection = null)),
        )
        val rdf = ticket(data)
        assertTrue("all-blank seat must not be persisted", rdf.toNewTicket().seats.isEmpty())
        assertEquals(
            "no schema:ticketedSeat link should be written",
            0,
            quadsOf(rdf).count { it.predicate == Schema.TICKETED_SEAT },
        )
    }

    @Test
    fun `a seat with one real part keeps only that part`() {
        val data = NewTicket(
            title = "Show",
            seats = listOf(TicketSeat(seatNumber = "27", seatRow = "   ", seatSection = "")),
        )
        val seat = ticket(data).toNewTicket().seats.single()
        assertEquals("27", seat.seatNumber)
        assertNull(seat.seatRow)
        assertNull(seat.seatSection)
    }
}
