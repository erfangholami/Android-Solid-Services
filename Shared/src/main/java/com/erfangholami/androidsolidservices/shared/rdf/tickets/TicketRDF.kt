package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import java.net.URI

/**
 * RDF representation of a single wallet ticket (`schema:Ticket`).
 *
 * Wraps the quads of one ticket document on a pod and exposes typed accessors and
 * mutators. The document's primary subject is `{document}#this`; sub-entities live
 * on sibling fragment nodes of the same document:
 *
 * - `#issuer` — `schema:Organization` (via `schema:issuedBy`)
 * - `#underName` — `schema:Person` (via `schema:underName`)
 * - `#seat` — `schema:Seat` (via `schema:ticketedSeat`)
 * - `#event` — `schema:Event` (via `solidshare:event`)
 * - `#place` — `schema:Place` (via `schema:location` on the event)
 *
 * Construct from a pod response by passing the parsed quads, or create a new empty
 * instance by supplying only the identifier. All mutator methods update the
 * in-memory quad list; call the tickets data module to persist the change.
 */
public class TicketRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier().toString(), Schema.TICKET)
    }

    private val selfUri: String
        get() = getIdentifier().toString()

    private val documentUri: String
        get() = selfUri.substringBefore('#')

    private val issuerNode: String get() = "$documentUri#issuer"
    private val underNameNode: String get() = "$documentUri#underName"
    private val seatNode: String get() = "$documentUri#seat"
    private val eventNode: String get() = "$documentUri#event"
    private val placeNode: String get() = "$documentUri#place"

    /** Returns the ticket's display title (`schema:name`). */
    public fun getTitle(): String =
        findPropertyForSubject(selfUri, Schema.NAME)
            ?: error("Ticket ${getIdentifier()} is missing a schema:name (title)")

    /** Sets the ticket's display title. */
    public fun setTitle(title: String) {
        addQuadLiteral(selfUri, Schema.NAME, title, XSD.STRING)
    }

    /** Returns the ticket's free-text description (`schema:description`), or `null`. */
    public fun getDescription(): String? =
        findPropertyForSubject(selfUri, Schema.DESCRIPTION)

    /** Sets (or clears, when `null`) the ticket's description. */
    public fun setDescription(description: String?) {
        setOptionalLiteral(Schema.DESCRIPTION, description)
    }

    /** Returns the human-readable ticket number (`schema:ticketNumber`), or `null`. */
    public fun getTicketNumber(): String? =
        findPropertyForSubject(selfUri, Schema.TICKET_NUMBER)

    /** Sets (or clears, when `null`) the ticket number. */
    public fun setTicketNumber(ticketNumber: String?) {
        setOptionalLiteral(Schema.TICKET_NUMBER, ticketNumber)
    }

    /** Returns the exact barcode payload (`schema:ticketToken`), or `null`. */
    public fun getTicketToken(): String? =
        findPropertyForSubject(selfUri, Schema.TICKET_TOKEN)

    /** Sets (or clears, when `null`) the barcode payload. Stored verbatim. */
    public fun setTicketToken(ticketToken: String?) {
        setOptionalLiteral(Schema.TICKET_TOKEN, ticketToken)
    }

    /** Returns the barcode symbology, tolerating unknown stored values as [TicketBarcodeFormat.NONE]. */
    public fun getBarcodeFormat(): TicketBarcodeFormat =
        enumOrDefault(
            findPropertyForSubject(selfUri, SolidShare.BARCODE_FORMAT),
            TicketBarcodeFormat.NONE,
        )

    /** Sets the barcode symbology (`solidshare:barcodeFormat`). */
    public fun setBarcodeFormat(format: TicketBarcodeFormat) {
        addQuadLiteral(selfUri, SolidShare.BARCODE_FORMAT, format.name, XSD.STRING)
    }

    /** Returns the pass category, tolerating unknown stored values as [TicketCategory.GENERIC]. */
    public fun getCategory(): TicketCategory =
        enumOrDefault(
            findPropertyForSubject(selfUri, SolidShare.CATEGORY),
            TicketCategory.GENERIC,
        )

    /** Sets the pass category (`solidshare:category`). */
    public fun setCategory(category: TicketCategory) {
        addQuadLiteral(selfUri, SolidShare.CATEGORY, category.name, XSD.STRING)
    }

    /** Returns the issuing organization's display name (`schema:issuedBy` → `schema:name`), or `null`. */
    public fun getIssuerName(): String? =
        findPropertyForSubject(selfUri, Schema.ISSUED_BY)?.let { node ->
            findPropertyForSubject(node, Schema.NAME)
        }

    /** Sets (or clears, when `null`) the issuing organization's display name. */
    public fun setIssuerName(name: String?) {
        clearNode(issuerNode, Schema.ISSUED_BY)
        if (name.isNullOrBlank()) return
        addQuad(selfUri, Schema.ISSUED_BY, issuerNode)
        addQuad(issuerNode, RDF.TYPE, Schema.ORGANIZATION)
        addQuadLiteral(issuerNode, Schema.NAME, name, XSD.STRING)
    }

    /** Returns the ticket holder's display name (`schema:underName` → `schema:name`), or `null`. */
    public fun getUnderName(): String? =
        findPropertyForSubject(selfUri, Schema.UNDER_NAME)?.let { node ->
            findPropertyForSubject(node, Schema.NAME)
        }

    /** Sets (or clears, when `null`) the ticket holder's display name. */
    public fun setUnderName(name: String?) {
        clearNode(underNameNode, Schema.UNDER_NAME)
        if (name.isNullOrBlank()) return
        addQuad(selfUri, Schema.UNDER_NAME, underNameNode)
        addQuad(underNameNode, RDF.TYPE, Schema.PERSON)
        addQuadLiteral(underNameNode, Schema.NAME, name, XSD.STRING)
    }

    /** Returns the seat assignment (`schema:ticketedSeat`), or `null` when absent. */
    public fun getSeat(): TicketSeat? {
        val node = findPropertyForSubject(selfUri, Schema.TICKETED_SEAT) ?: return null
        val seat = TicketSeat(
            seatNumber = findPropertyForSubject(node, Schema.SEAT_NUMBER),
            seatRow = findPropertyForSubject(node, Schema.SEAT_ROW),
            seatSection = findPropertyForSubject(node, Schema.SEAT_SECTION),
        )
        return seat.takeIf {
            it.seatNumber != null || it.seatRow != null || it.seatSection != null
        }
    }

    /** Sets (or clears, when `null` or empty) the seat assignment. */
    public fun setSeat(seat: TicketSeat?) {
        clearNode(seatNode, Schema.TICKETED_SEAT)
        if (seat == null) return
        val parts = listOfNotNull(
            seat.seatNumber?.takeIf { it.isNotBlank() }?.let { Schema.SEAT_NUMBER to it },
            seat.seatRow?.takeIf { it.isNotBlank() }?.let { Schema.SEAT_ROW to it },
            seat.seatSection?.takeIf { it.isNotBlank() }?.let { Schema.SEAT_SECTION to it },
        )
        if (parts.isEmpty()) return
        addQuad(selfUri, Schema.TICKETED_SEAT, seatNode)
        addQuad(seatNode, RDF.TYPE, Schema.SEAT)
        parts.forEach { (predicate, value) ->
            addQuadLiteral(seatNode, predicate, value, XSD.STRING)
        }
    }

    /** Returns the price as its raw lexical value (`schema:totalPrice`), or `null`. */
    public fun getTotalPrice(): String? =
        findPropertyForSubject(selfUri, Schema.TOTAL_PRICE)

    /** Sets (or clears, when `null`) the price. Stored verbatim for exact round-tripping. */
    public fun setTotalPrice(price: String?) {
        setOptionalLiteral(Schema.TOTAL_PRICE, price)
    }

    /** Returns the ISO-4217 currency of the price (`schema:priceCurrency`), or `null`. */
    public fun getPriceCurrency(): String? =
        findPropertyForSubject(selfUri, Schema.PRICE_CURRENCY)

    /** Sets (or clears, when `null`) the price currency. */
    public fun setPriceCurrency(currency: String?) {
        setOptionalLiteral(Schema.PRICE_CURRENCY, currency)
    }

    /** Returns when the ticket was issued (`schema:dateIssued`, ISO-8601), or `null`. */
    public fun getDateIssued(): String? =
        findPropertyForSubject(selfUri, Schema.DATE_ISSUED)

    /** Sets (or clears, when `null`) the issue date-time (ISO-8601). */
    public fun setDateIssued(dateIssued: String?) {
        setOptionalDateTime(Schema.DATE_ISSUED, dateIssued)
    }

    /** Returns the event this ticket admits to (`solidshare:event`), or `null` when absent. */
    public fun getEvent(): TicketEvent? {
        val node = findPropertyForSubject(selfUri, SolidShare.EVENT) ?: return null
        val locationNode = findPropertyForSubject(node, Schema.LOCATION)
        val location = locationNode?.let {
            TicketPlace(
                name = findPropertyForSubject(it, Schema.NAME),
                address = findPropertyForSubject(it, Schema.ADDRESS),
            ).takeIf { place -> place.name != null || place.address != null }
        }
        val event = TicketEvent(
            name = findPropertyForSubject(node, Schema.NAME),
            startDate = findPropertyForSubject(node, Schema.START_DATE),
            endDate = findPropertyForSubject(node, Schema.END_DATE),
            location = location,
        )
        return event.takeIf {
            it.name != null || it.startDate != null || it.endDate != null || it.location != null
        }
    }

    /** Sets (or clears, when `null` or empty) the event this ticket admits to. */
    public fun setEvent(event: TicketEvent?) {
        clearNode(placeNode, Schema.LOCATION, linkSubject = eventNode)
        clearNode(eventNode, SolidShare.EVENT)
        if (event == null) return
        val hasContent = event.name != null || event.startDate != null ||
                event.endDate != null || event.location != null
        if (!hasContent) return
        addQuad(selfUri, SolidShare.EVENT, eventNode)
        addQuad(eventNode, RDF.TYPE, Schema.EVENT)
        event.name?.takeIf { it.isNotBlank() }?.let {
            addQuadLiteral(eventNode, Schema.NAME, it, XSD.STRING)
        }
        event.startDate?.takeIf { it.isNotBlank() }?.let {
            addQuadLiteral(eventNode, Schema.START_DATE, it, XSD.dateTypeFor(it))
        }
        event.endDate?.takeIf { it.isNotBlank() }?.let {
            addQuadLiteral(eventNode, Schema.END_DATE, it, XSD.dateTypeFor(it))
        }
        val location = event.location
        if (location != null && (location.name != null || location.address != null)) {
            addQuad(eventNode, Schema.LOCATION, placeNode)
            addQuad(placeNode, RDF.TYPE, Schema.PLACE)
            location.name?.takeIf { it.isNotBlank() }?.let {
                addQuadLiteral(placeNode, Schema.NAME, it, XSD.STRING)
            }
            location.address?.takeIf { it.isNotBlank() }?.let {
                addQuadLiteral(placeNode, Schema.ADDRESS, it, XSD.STRING)
            }
        }
    }

    /** Returns the validity start (`schema:validFrom`, ISO-8601), or `null`. */
    public fun getValidFrom(): String? =
        findPropertyForSubject(selfUri, Schema.VALID_FROM)

    /** Sets (or clears, when `null`) the validity start (ISO-8601). */
    public fun setValidFrom(validFrom: String?) {
        setOptionalDateTime(Schema.VALID_FROM, validFrom)
    }

    /** Returns the validity end (`schema:validThrough`, ISO-8601), or `null`. */
    public fun getValidThrough(): String? =
        findPropertyForSubject(selfUri, Schema.VALID_THROUGH)

    /** Sets (or clears, when `null`) the validity end (ISO-8601). */
    public fun setValidThrough(validThrough: String?) {
        setOptionalDateTime(Schema.VALID_THROUGH, validThrough)
    }

    /** Returns the ticket's provenance, tolerating unknown stored values as [TicketSource.MANUAL]. */
    public fun getSource(): TicketSource =
        enumOrDefault(
            findPropertyForSubject(selfUri, SolidShare.SOURCE),
            TicketSource.MANUAL,
        )

    /** Sets the ticket's provenance (`solidshare:source`). */
    public fun setSource(source: TicketSource) {
        addQuadLiteral(selfUri, SolidShare.SOURCE, source.name, XSD.STRING)
    }

    /** Returns the pod URI of the original imported artifact (`solidshare:artifact`), or `null`. */
    public fun getArtifactUri(): String? =
        findPropertyForSubject(selfUri, SolidShare.ARTIFACT)

    /** Sets (or clears, when `null`) the artifact link. */
    public fun setArtifactUri(artifactUri: String?) {
        if (artifactUri.isNullOrBlank()) {
            clearProperties(SolidShare.ARTIFACT, selfUri)
        } else {
            addQuad(selfUri, SolidShare.ARTIFACT, artifactUri)
        }
    }

    /** Returns the creation timestamp (`dcterms:created`, ISO-8601), or `null`. */
    public fun getCreated(): String? =
        findPropertyForSubject(selfUri, DC.CREATED)

    /** Sets the creation timestamp (ISO-8601). */
    public fun setCreated(isoDateTime: String) {
        addQuadLiteral(selfUri, DC.CREATED, isoDateTime, XSD.DATE_TIME)
    }

    /** Returns the last-modification timestamp (`dcterms:modified`, ISO-8601), or `null`. */
    public fun getModified(): String? =
        findPropertyForSubject(selfUri, DC.MODIFIED)

    /** Sets the last-modification timestamp (ISO-8601). */
    public fun setModified(isoDateTime: String) {
        addQuadLiteral(selfUri, DC.MODIFIED, isoDateTime, XSD.DATE_TIME)
    }

    private fun setOptionalLiteral(predicate: String, value: String?) {
        if (value.isNullOrBlank()) {
            clearProperties(predicate, selfUri)
        } else {
            addQuadLiteral(selfUri, predicate, value, XSD.STRING)
        }
    }

    private fun setOptionalDateTime(predicate: String, value: String?) {
        if (value.isNullOrBlank()) {
            clearProperties(predicate, selfUri)
        } else {
            addQuadLiteral(selfUri, predicate, value, XSD.dateTypeFor(value))
        }
    }

    private fun clearNode(nodeIri: String, linkPredicate: String, linkSubject: String = selfUri) {
        quads.removeAll { it.subject == nodeIri }
        quads.removeAll { it.subject == linkSubject && it.predicate == linkPredicate && it.`object` == nodeIri }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        raw?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() } ?: default
}
