package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.DetailPlacement
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcode
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketDetail
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketJourney
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketMembership
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketOrganization
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPerson
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketReservation
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketReservationStatus
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStop
import com.erfangholami.androidsolidservices.shared.model.tickets.TransportMode
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketRDFTest {

    private val ticketUri = "https://alice.pod/tickets/abc123.ttl#this"
    private val documentUri = "https://alice.pod/tickets/abc123.ttl"

    /** Writes [data] as RDF, serialises to JSON-LD, parses it back, and returns the re-read snapshot. */
    private fun roundTrip(data: NewTicket): NewTicket {
        val written = TicketRDF(ticketUri).apply { setTicketData(data) }
        val jsonLd = written.getEntity().bufferedReader().use { it.readText() }
        val quads = RDFResource.parseJsonLd(jsonLd, documentUri)
        return TicketRDF(ticketUri, quads = quads).toNewTicket()
    }

    // ---- The case the old model could not represent at all: a two-ended bus journey -----------

    @Test
    fun `a bus ticket round-trips origin, destination, platform and booking reference`() {
        val bus = NewTicket(
            title = "Amsterdam → Berlin",
            ticketNumber = "TKT-0042",
            category = TicketCategory.BUS,
            source = TicketSource.PKPASS,
            issuer = TicketOrganization(name = "FlixBus", logoUri = "$documentUri/logo.png"),
            holder = TicketPerson(name = "Erfan Gholami"),
            seats = listOf(TicketSeat(seatNumber = "12A")),
            barcodes = listOf(TicketBarcode("MSBYRUZBTiBHSE9MQU1J", TicketBarcodeFormat.AZTEC, "iso-8859-1", "PNR7X2Q")),
            totalPrice = "24.99",
            priceCurrency = "EUR",
            reservation = TicketReservation(
                status = TicketReservationStatus.CONFIRMED,
                bookingReference = "PNR7X2Q",
                fareClass = "Standard",
            ),
            journey = TicketJourney(
                mode = TransportMode.BUS,
                carrierName = "FlixBus",
                serviceNumber = "042",
                serviceName = "FlixBus 042",
                departure = TicketStop(name = "Amsterdam Sloterdijk", cityName = "Amsterdam", timeZone = "Europe/Amsterdam", platform = "B3"),
                arrival = TicketStop(name = "Berlin ZOB", cityName = "Berlin", timeZone = "Europe/Berlin"),
                departureTime = "2026-08-02T08:15:00+02:00",
                arrivalTime = "2026-08-02T14:40:00+02:00",
                duration = "PT6H25M",
            ),
        )

        assertEquals(bus, roundTrip(bus))
    }

    // ---- Flight: gate, terminal, boarding group, multiple seats, IATA codes -------------------

    @Test
    fun `a flight ticket round-trips gate, terminal, boarding group and IATA codes`() {
        val flight = NewTicket(
            title = "AMS → JFK",
            category = TicketCategory.FLIGHT,
            source = TicketSource.BCBP,
            issuer = TicketOrganization(name = "KLM", iataCode = "KL"),
            holder = TicketPerson(givenName = "Erfan", familyName = "Gholami"),
            seats = listOf(TicketSeat(seatNumber = "27A"), TicketSeat(seatNumber = "27B")),
            barcodes = listOf(TicketBarcode("M1GHOLAMI", TicketBarcodeFormat.PDF_417)),
            reservation = TicketReservation(
                bookingReference = "ABC123",
                boardingGroup = "2",
                passengerSequenceNumber = "0031",
                electronicTicket = true,
                fastTrack = true,
            ),
            journey = TicketJourney(
                mode = TransportMode.FLIGHT,
                serviceNumber = "KL641",
                departure = TicketStop(name = "Schiphol", iataCode = "AMS", terminal = "1", gate = "D57"),
                arrival = TicketStop(name = "John F. Kennedy", iataCode = "JFK", terminal = "4"),
                departureTime = "2026-09-01T10:20:00+02:00",
                originalDepartureTime = "2026-09-01T10:00:00+02:00",
                boardingTime = "2026-09-01T09:40:00+02:00",
                aircraft = "Boeing 777",
            ),
        )

        assertEquals(flight, roundTrip(flight))
    }

    // ---- Event: venue, doors, performers, admission level -------------------------------------

    @Test
    fun `an event ticket round-trips venue, doors-open, performers and admission level`() {
        val event = NewTicket(
            title = "Coldplay — Music of the Spheres",
            description = "Gate opens 18:00",
            category = TicketCategory.EVENT,
            source = TicketSource.PKPASS,
            issuer = TicketOrganization(name = "Ticketmaster"),
            barcodes = listOf(TicketBarcode("c3RhZGl1bQ", TicketBarcodeFormat.QR_CODE)),
            event = TicketEvent(
                name = "Music of the Spheres Tour",
                startDate = "2026-07-14T19:30:00Z",
                doorTime = "2026-07-14T18:00:00Z",
                performers = listOf("Coldplay", "H.E.R."),
                genre = "Pop",
                admissionLevel = "VIP",
                admissionLevelAbbreviation = "VIP",
                location = TicketPlace(
                    name = "Johan Cruijff ArenA",
                    address = "Arena Boulevard 1, Amsterdam",
                    entrance = "Gate A",
                ),
            ),
        )

        assertEquals(event, roundTrip(event))
    }

    // ---- Loyalty: membership + balance, no reservation ----------------------------------------

    @Test
    fun `a loyalty card round-trips membership and balance with no reservation node`() {
        val loyalty = NewTicket(
            title = "Flying Blue",
            category = TicketCategory.LOYALTY,
            membership = TicketMembership(
                programName = "Flying Blue",
                membershipNumber = "FB-9988",
                membershipStatus = "Gold",
                pointsBalance = "42500",
            ),
        )

        val parsed = roundTrip(loyalty)
        assertEquals(loyalty, parsed)
        assertNull("a loyalty card has no reservation", parsed.reservation)
        assertNull("a loyalty card has no journey", parsed.journey)
    }

    @Test
    fun `a loyalty card writes no reservation node in RDF`() {
        val rdf = TicketRDF(ticketUri).apply {
            setTicketData(NewTicket(title = "Flying Blue", category = TicketCategory.LOYALTY))
        }
        assertEquals(
            0,
            rdf.getAllQuads().count { it.subject.endsWith("#reservation") },
        )
    }

    // ---- Repeatable structures -----------------------------------------------------------------

    @Test
    fun `multiple barcodes and seats all survive`() {
        val data = NewTicket(
            title = "Family entry",
            barcodes = listOf(
                TicketBarcode("AAA", TicketBarcodeFormat.QR_CODE),
                TicketBarcode("BBB", TicketBarcodeFormat.AZTEC, altText = "child"),
            ),
            seats = listOf(
                TicketSeat(seatNumber = "1", seatRow = "A"),
                TicketSeat(seatNumber = "2", seatRow = "A"),
                TicketSeat(seatNumber = "3", seatRow = "A"),
            ),
        )

        val parsed = roundTrip(data)
        assertEquals(2, parsed.barcodes.size)
        assertEquals(3, parsed.seats.size)
        assertEquals(data.barcodes, parsed.barcodes)
        assertEquals(data.seats, parsed.seats)
    }

    @Test
    fun `the first barcode is mirrored into schema ticketToken`() {
        val rdf = TicketRDF(ticketUri).apply {
            setTicketData(
                NewTicket(
                    title = "X",
                    barcodes = listOf(
                        TicketBarcode("PRIMARY", TicketBarcodeFormat.QR_CODE),
                        TicketBarcode("SECONDARY", TicketBarcodeFormat.AZTEC),
                    ),
                ),
            )
        }
        val token = rdf.getAllQuads().single { it.subject == ticketUri && it.predicate == Schema.TICKET_TOKEN }
        assertEquals("PRIMARY", token.`object`)
    }

    // ---- The no-data-lost catch-all -----------------------------------------------------------

    @Test
    fun `detail catch-all round-trips with placement and order`() {
        val data = NewTicket(
            title = "Odd pass",
            details = listOf(
                TicketDetail(label = "Terms", value = "No refunds", placement = DetailPlacement.BACK, order = 0),
                TicketDetail(label = "Note", value = "Bring ID", placement = DetailPlacement.AUXILIARY, order = 1),
            ),
        )

        assertEquals(data.details, roundTrip(data).details)
    }

    // ---- Model bridge --------------------------------------------------------------------------

    @Test
    fun `model createFromRdf carries the lifecycle fields`() {
        val rdf = TicketRDF(ticketUri).apply {
            setTicketData(NewTicket(title = "Show", category = TicketCategory.EVENT))
            setArtifactUri("$documentUri.pkpass")
            setArtifactVerified(true)
            setCreated("2026-07-02T09:15:00Z")
            setModified("2026-07-03T09:15:00Z")
        }
        val model = Ticket.createFromRdf(rdf)
        assertEquals(ticketUri, model.uri)
        assertEquals("Show", model.title)
        assertEquals("$documentUri.pkpass", model.artifactUri)
        assertEquals(true, model.artifactVerified)
        assertEquals("2026-07-02T09:15:00Z", model.createdAt)
        assertEquals("2026-07-03T09:15:00Z", model.modifiedAt)
    }

    @Test
    fun `a full replace drops fields that became absent but keeps created and artifact`() {
        val rdf = TicketRDF(ticketUri).apply {
            setTicketData(
                NewTicket(
                    title = "Rich",
                    category = TicketCategory.EVENT,
                    event = TicketEvent(name = "Gig", startDate = "2026-01-01T20:00:00Z"),
                    seats = listOf(TicketSeat(seatNumber = "7")),
                ),
            )
            setArtifactUri("$documentUri.pkpass")
            setCreated("2026-07-02T09:15:00Z")
        }
        rdf.setTicketData(NewTicket(title = "Bare"))

        val parsed = rdf.toNewTicket()
        assertEquals("Bare", parsed.title)
        assertNull(parsed.event)
        assertTrue(parsed.seats.isEmpty())
        assertEquals("the artifact link survives a replace", "$documentUri.pkpass", rdf.getArtifactUri())
        assertEquals("dcterms:created survives a replace", "2026-07-02T09:15:00Z", rdf.getCreated())
    }

}
