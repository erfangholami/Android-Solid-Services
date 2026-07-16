package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketOrganization
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStyle
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketsIndexRDFTest {

    private val indexUri = "https://alice.pod/tickets/index.ttl"

    private fun ticket(id: String, title: String, issuer: String? = null): TicketRDF =
        TicketRDF("https://alice.pod/tickets/$id.ttl#this").apply {
            setTicketData(
                NewTicket(
                    title = title,
                    category = TicketCategory.CINEMA,
                    event = TicketEvent(name = title, startDate = "2026-08-01T20:00:00Z"),
                    validThrough = "2026-08-02T00:00:00Z",
                    issuer = issuer?.let { TicketOrganization(name = it) },
                ),
            )
        }

    @Test
    fun `added rows are listed with their cached fields`() {
        val index = TicketsIndexRDF(indexUri)
        index.addTicket(ticket("t1", "Dune III", issuer = "Pathé"))
        index.addTicket(ticket("t2", "Oppenheimer"))

        val rows = index.getTickets().sortedBy { it.uri }
        assertEquals(2, rows.size)
        assertEquals(
            TicketSummary(
                uri = "https://alice.pod/tickets/t1.ttl#this",
                title = "Dune III",
                category = TicketCategory.CINEMA,
                eventStart = "2026-08-01T20:00:00Z",
                issuer = "Pathé",
                validThrough = "2026-08-02T00:00:00Z",
            ),
            rows[0],
        )
    }

    @Test
    fun `pass colours are cached on the row when the ticket has a style`() {
        val styled = TicketRDF("https://alice.pod/tickets/t9.ttl#this").apply {
            setTicketData(
                NewTicket(
                    title = "Flight",
                    category = TicketCategory.FLIGHT,
                    style = TicketStyle(
                        backgroundColor = "rgb(60,65,76)",
                        foregroundColor = "rgb(255,255,255)",
                    ),
                ),
            )
        }
        val index = TicketsIndexRDF(indexUri)
        index.addTicket(styled)
        index.addTicket(ticket("t1", "Dune III"))

        val rows = index.getTickets().sortedBy { it.uri }
        assertEquals("rgb(60,65,76)", rows[1].backgroundColor)
        assertEquals("rgb(255,255,255)", rows[1].foregroundColor)
        assertEquals(null, rows[0].backgroundColor)
    }

    @Test
    fun `rows survive a serialize-parse wire round-trip`() {
        val index = TicketsIndexRDF(indexUri)
        index.addTicket(ticket("t1", "Dune III", issuer = "Pathé"))
        val jsonLd = index.getEntity().bufferedReader().use { it.readText() }
        val reparsed = TicketsIndexRDF(
            indexUri,
            quads = RDFResource.parseJsonLd(jsonLd, indexUri.toString()),
        )
        assertEquals(index.getTickets(), reparsed.getTickets())
    }

    @Test
    fun `updateTicket replaces the row and drops absent fields`() {
        val index = TicketsIndexRDF(indexUri)
        index.addTicket(ticket("t1", "Dune III", issuer = "Pathé"))

        val changed = TicketRDF("https://alice.pod/tickets/t1.ttl#this").apply {
            setTicketData(NewTicket(title = "Dune III (IMAX)", category = TicketCategory.CINEMA))
        }
        assertTrue(index.updateTicket(changed))

        val row = index.getTickets().single()
        assertEquals("Dune III (IMAX)", row.title)
        assertEquals(null, row.issuer)
        assertEquals(null, row.eventStart)
    }

    @Test
    fun `updateTicket returns false for an unknown ticket and adds no row`() {
        val index = TicketsIndexRDF(indexUri)
        assertFalse(index.updateTicket(ticket("ghost", "Ghost")))
        assertEquals(emptyList<TicketSummary>(), index.getTickets())
    }

    @Test
    fun `removeTicket removes all row triples`() {
        val index = TicketsIndexRDF(indexUri)
        index.addTicket(ticket("t1", "Dune III"))
        assertTrue(index.removeTicket("https://alice.pod/tickets/t1.ttl#this"))
        assertFalse(index.removeTicket("https://alice.pod/tickets/t1.ttl#this"))
        assertEquals(emptyList<TicketSummary>(), index.getTickets())
    }
}
