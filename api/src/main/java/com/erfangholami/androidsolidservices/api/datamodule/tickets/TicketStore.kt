package com.erfangholami.androidsolidservices.api.datamodule.tickets

import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * Manages the wallet tickets (`schema:Ticket` resources) of a pod user.
 *
 * Tickets live as one RDF document each inside a tickets container (`{storage}tickets/` by
 * default) alongside an index document that caches the fields a wallet list needs. The
 * container is registered in the user's type index as a `solid:instanceContainer` for
 * `schema:Ticket`; both the container and the registration are bootstrapped on first use.
 *
 * Reached via [SolidTicketsDataModule.tickets], mirroring the contacts module's stores — so
 * the verbs match: [list] / [get] / [create] / [update] / [delete].
 */
public interface TicketStore {

    /**
     * Lists ticket summaries from every tickets container registered in [ownerWebId]'s private
     * and public type indexes. Containers whose index document is missing are skipped.
     */
    public suspend fun list(ownerWebId: String): SolidResult<TicketList>

    /** Reads the full ticket at [ticketUri]. */
    public suspend fun get(ownerWebId: String, ticketUri: String): SolidResult<Ticket>

    /**
     * Creates [newTicket] in [ownerWebId]'s tickets container, bootstrapping the container, its
     * index, and the type-index registration on first use.
     *
     * @param storage The pod storage (root) URL the tickets container is allocated under when
     *   none is registered yet; omit (`null`) to have it discovered from the profile / hierarchy.
     * @param artifact Optional original imported artifact (e.g. `.pkpass` bytes), stored as a
     *   sibling binary and linked via `solidshare:artifact`.
     * @param artifactContentType The media type of [artifact]; required when [artifact] is non-null.
     * @param isPrivate When `true` (default), a bootstrapped container is registered in the private
     *   type index; when `false`, in the public one.
     * @param container Optional container URI to use instead of the registered / default one.
     */
    public suspend fun create(
        ownerWebId: String,
        newTicket: NewTicket,
        storage: String? = null,
        artifact: ByteArray? = null,
        artifactContentType: String? = null,
        isPrivate: Boolean = true,
        container: String? = null,
    ): SolidResult<Ticket>

    /**
     * Rewrites the ticket at [ticketUri] from [updated] with replace semantics: properties absent
     * from [updated] are removed. `dcterms:created` and the `solidshare:artifact` link are
     * preserved, `dcterms:modified` is refreshed, and the container index row is re-cached.
     */
    public suspend fun update(
        ownerWebId: String,
        ticketUri: String,
        updated: NewTicket,
    ): SolidResult<Ticket>

    /**
     * Deletes the ticket at [ticketUri]: its index row, its artifact binary (when present), and
     * the ticket document itself. Returns the removed ticket.
     */
    public suspend fun delete(ownerWebId: String, ticketUri: String): SolidResult<Ticket>

    /** Downloads the original imported artifact at [artifactUri] (a `solidshare:artifact` target). */
    public suspend fun getArtifact(ownerWebId: String, artifactUri: String): SolidResult<TicketArtifact>
}
