package com.erfangholami.androidsolidservices.api.datamodule.tickets

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation.SolidTicketsDataModuleImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * Manages wallet tickets (`schema:Ticket` resources) on a user's Solid pod.
 *
 * Tickets live as one RDF document each inside a tickets container
 * (`{storage}tickets/` by default) alongside an index document that caches the
 * fields a wallet list needs. The container is registered in the user's type
 * index as a `solid:instanceContainer` for `schema:Ticket`, so other Solid apps
 * can discover it; both the container and the registration are bootstrapped on
 * first use.
 *
 * All operations are performed on behalf of [ownerWebId] and return a
 * [SolidResult] to distinguish data errors from unexpected exceptions.
 *
 * Obtain an instance via [SolidTicketsDataModule.getInstance].
 */
public interface SolidTicketsDataModule {

    public companion object {
        /**
         * Returns the application-scoped singleton [SolidTicketsDataModule]
         * built on [authenticator]'s resource manager.
         */
        public fun getInstance(authenticator: Authenticator): SolidTicketsDataModule =
            SolidTicketsDataModuleImplementation.getInstance(authenticator)

        /**
         * Returns the application-scoped singleton [SolidTicketsDataModule]
         * built on [resourceManager].
         */
        public fun getInstance(resourceManager: SolidResourceManager): SolidTicketsDataModule =
            SolidTicketsDataModuleImplementation.getInstance(resourceManager)
    }

    /**
     * Lists ticket summaries from every tickets container registered in
     * [ownerWebId]'s private and public type indexes. Containers whose index
     * document is missing are skipped.
     */
    public suspend fun getTickets(
        ownerWebId: String,
    ): SolidResult<TicketList>

    /** Reads the full ticket at [ticketUri]. */
    public suspend fun getTicket(
        ownerWebId: String,
        ticketUri: String,
    ): SolidResult<Ticket>

    /**
     * Creates [newTicket] in [ownerWebId]'s tickets container, bootstrapping the
     * container, its index, and the type-index registration on first use.
     *
     * @param storage The pod storage (root) URL the tickets container is allocated
     *   under when none is registered yet.
     * @param artifact Optional original imported artifact (e.g. the `.pkpass`
     *   file bytes), stored as a sibling binary and linked via `solidshare:artifact`.
     * @param artifactContentType The media type of [artifact]; required when
     *   [artifact] is non-null.
     * @param isPrivate When `true` (default), a bootstrapped container is
     *   registered in the private type index; when `false`, in the public one.
     * @param container Optional container URI to use instead of the registered /
     *   default tickets container.
     */
    public suspend fun createTicket(
        ownerWebId: String,
        storage: String,
        newTicket: NewTicket,
        artifact: ByteArray? = null,
        artifactContentType: String? = null,
        isPrivate: Boolean = true,
        container: String? = null,
    ): SolidResult<Ticket>

    /**
     * Rewrites the ticket at [ticketUri] from [updated] with replace semantics:
     * properties absent from [updated] are removed. `dcterms:created` and the
     * `solidshare:artifact` link are preserved, `dcterms:modified` is refreshed,
     * and the container index row is re-cached.
     */
    public suspend fun updateTicket(
        ownerWebId: String,
        ticketUri: String,
        updated: NewTicket,
    ): SolidResult<Ticket>

    /**
     * Deletes the ticket at [ticketUri]: its index row, its artifact binary (when
     * present), and the ticket document itself. Returns the removed ticket.
     */
    public suspend fun deleteTicket(
        ownerWebId: String,
        ticketUri: String,
    ): SolidResult<Ticket>

    /** Downloads the original imported artifact at [artifactUri] (a `solidshare:artifact` target). */
    public suspend fun getTicketArtifact(
        ownerWebId: String,
        artifactUri: String,
    ): SolidResult<TicketArtifact>
}
