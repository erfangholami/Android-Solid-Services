package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.XSD

/**
 * RDF representation of the tickets index document inside a tickets container — a
 * `solidshare:TicketIndex`, the document the type index's `solid:instance` registration
 * for `schema:Ticket` points at.
 *
 * Mirrors the people-index idiom of the contacts data module: each ticket in the
 * container gets one cached row keyed by its URI, so a wallet list can be rendered
 * from a single GET without fetching every ticket document:
 *
 * ```turtle
 * <> rdf:type solidshare:TicketIndex .
 *
 * <…/{uuid}/ticket#this>
 *     rdf:type            schema:Ticket ;
 *     schema:name         "Concert" ;
 *     solidshare:category "EVENT" ;
 *     schema:startDate    "2026-07-14T19:30:00Z"^^xsd:dateTime ;
 *     solidshare:issuer   "Ticketmaster" ;
 *     schema:validThrough "2026-07-14T23:59:00Z"^^xsd:dateTime .
 * ```
 *
 * Writers must keep rows in step with the ticket documents (the tickets data
 * module does this on every create/update/delete).
 */
public class TicketsIndexRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier(), SolidShare.TICKET_INDEX_CLASS)
    }

    /**
     * Returns every cached ticket row in this index. Rows missing a title are
     * silently skipped.
     */
    public fun getTickets(): List<TicketSummary> =
        quads
            .filter { it.predicate == RDF.TYPE && it.`object` == Schema.TICKET }
            .map { it.subject }
            .distinct()
            .mapNotNull { subject ->
                val title = findPropertyForSubject(subject, Schema.NAME)
                    ?: return@mapNotNull null
                TicketSummary(
                    uri = subject,
                    title = title,
                    category = findPropertyForSubject(subject, SolidShare.CATEGORY)
                        ?.let { raw -> runCatching { TicketCategory.valueOf(raw) }.getOrNull() }
                        ?: TicketCategory.GENERIC,
                    eventStart = findPropertyForSubject(subject, Schema.START_DATE),
                    issuer = findPropertyForSubject(subject, SolidShare.ISSUER),
                    validThrough = findPropertyForSubject(subject, Schema.VALID_THROUGH),
                    backgroundColor = findPropertyForSubject(subject, SolidShare.BACKGROUND_COLOR),
                    foregroundColor = findPropertyForSubject(subject, SolidShare.FOREGROUND_COLOR),
                )
            }

    /**
     * Caches a row for [ticket]: its URI, title, category, event start, issuer
     * display name, validity end, and pass colours (so a wallet list can paint
     * each pass without fetching its document).
     */
    public fun addTicket(ticket: TicketRDF) {
        val subject = ticket.getIdentifier()
        addQuad(subject, RDF.TYPE, Schema.TICKET)
        addQuadLiteral(subject, Schema.NAME, ticket.getTitle(), XSD.STRING)
        addQuadLiteral(subject, SolidShare.CATEGORY, ticket.getCategory().name, XSD.STRING)
        ticket.getIndexStartDate()?.let {
            addQuadLiteral(subject, Schema.START_DATE, it, XSD.dateTypeFor(it))
        }
        ticket.getIssuerName()?.let {
            addQuadLiteral(subject, SolidShare.ISSUER, it, XSD.STRING)
        }
        ticket.getValidThrough()?.let {
            addQuadLiteral(subject, Schema.VALID_THROUGH, it, XSD.dateTypeFor(it))
        }
        ticket.getStyle()?.backgroundColor?.let {
            addQuadLiteral(subject, SolidShare.BACKGROUND_COLOR, it, XSD.STRING)
        }
        ticket.getStyle()?.foregroundColor?.let {
            addQuadLiteral(subject, SolidShare.FOREGROUND_COLOR, it, XSD.STRING)
        }
    }

    /**
     * Replaces the cached row for [ticket] (remove + re-add, so fields that became
     * absent are dropped).
     *
     * @return `true` if a row existed and was replaced, `false` if the ticket was
     *   not in this index (no row is added in that case).
     */
    public fun updateTicket(ticket: TicketRDF): Boolean {
        if (!removeTicket(ticket.getIdentifier())) return false
        addTicket(ticket)
        return true
    }

    /**
     * Removes all row triples for [ticketUri].
     *
     * @return `true` if any triples were removed, `false` if the ticket was not found.
     */
    public fun removeTicket(ticketUri: String): Boolean {
        val affected = quads.any { it.subject == ticketUri }
        if (!affected) return false
        quads.removeAll { it.subject == ticketUri }
        return true
    }
}
