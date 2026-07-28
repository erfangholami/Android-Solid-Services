package com.erfangholami.androidsolidservices.api.datamodule.tickets

import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * Manages the wallet tickets (`schema:Ticket` resources) of a pod user.
 *
 * The tickets container (`{storage}tickets/` by default) holds an index document that caches
 * the fields a wallet list needs, and one sub-container per ticket named by a random UUID.
 * Everything belonging to a ticket lives inside that sub-container — its RDF document, the
 * original imported artifact and any stored pass images — so granting access to the one
 * container shares the complete pass:
 *
 * ```
 * {storage}tickets/
 *   index                  ← cached wallet rows (solidshare:TicketIndex); in the type index
 *   {uuid}/
 *     ticket#this          ← the schema:Ticket document
 *     artifact.pkpass      ← original imported file (solidshare:artifact)
 *     logo.png, strip.png… ← stored pass images (solidshare:logoImage …)
 * ```
 *
 * Every hop is a followed URL, never a guessed name: the user's type index registers the
 * index document as a `solid:instance` for `schema:Ticket`, the index rows carry each
 * ticket's URI, and the ticket document links its attachments. The RDF documents are
 * deliberately extension-less — the module speaks JSON-LD and the server persists whatever
 * representation it likes. The container, the index and the registration are bootstrapped on
 * first use; a legacy `solid:instanceContainer` registration is migrated to the
 * `solid:instance` form (its `index.ttl` keeps its name, reached via the registration), and
 * tickets created flat inside the container by earlier versions remain readable.
 *
 * Reached via [SolidTicketsDataModule.tickets], mirroring the contacts module's stores — so
 * the verbs match: [list] / [get] / [create] / [update] / [delete].
 */
public interface TicketStore {

    /**
     * Lists ticket summaries from every tickets container registered in [ownerWebId]'s private
     * and public type indexes (via `solid:instance` index registrations, plus legacy
     * `solid:instanceContainer` ones). Containers whose index document is missing are skipped.
     */
    public suspend fun list(ownerWebId: String): SolidResult<TicketList>

    /** Reads the full ticket at [ticketUri]. */
    public suspend fun get(ownerWebId: String, ticketUri: String): SolidResult<Ticket>

    /**
     * Creates [newTicket] in its own sub-container of [ownerWebId]'s tickets container,
     * bootstrapping the tickets container, its index, and the type-index registration on
     * first use.
     *
     * @param storage The pod storage (root) URL the tickets container is allocated under when
     *   none is registered yet; omit (`null`) to have it discovered from the profile / hierarchy.
     * @param artifact Optional original imported artifact (e.g. `.pkpass` bytes), stored inside
     *   the ticket's container and linked via `solidshare:artifact`.
     * @param artifactContentType The media type of [artifact]; required when [artifact] is non-null.
     * @param images Optional pass images (e.g. extracted from the `.pkpass`), stored inside the
     *   ticket's container as `{role}.png` and linked via `solidshare:logoImage` ….
     * @param isPrivate When `true` (default), a bootstrapped container is registered in the private
     *   type index; when `false`, in the public one.
     * @param container Optional tickets-container URI to use instead of the registered / default one.
     */
    public suspend fun create(
        ownerWebId: String,
        newTicket: NewTicket,
        storage: String? = null,
        artifact: ByteArray? = null,
        artifactContentType: String? = null,
        images: NewTicketImages? = null,
        isPrivate: Boolean = true,
        container: String? = null,
    ): SolidResult<Ticket>

    /**
     * Rewrites the ticket at [ticketUri] from [updated] with replace semantics: properties absent
     * from [updated] are removed. `dcterms:created`, the `solidshare:artifact` link and the stored
     * image links are preserved, `dcterms:modified` is refreshed, and the index row is re-cached.
     */
    public suspend fun update(
        ownerWebId: String,
        ticketUri: String,
        updated: NewTicket,
    ): SolidResult<Ticket>

    /**
     * Deletes the ticket at [ticketUri]: its index row and its whole sub-container — document,
     * artifact and stored images. A legacy flat ticket (document directly inside the tickets
     * container) loses its document and artifact binary instead. Returns the removed ticket.
     */
    public suspend fun delete(ownerWebId: String, ticketUri: String): SolidResult<Ticket>

    /**
     * Downloads a binary stored with a ticket — the original imported artifact (a
     * `solidshare:artifact` target) or one of the stored pass images ([Ticket.images]).
     */
    public suspend fun getArtifact(ownerWebId: String, artifactUri: String): SolidResult<TicketArtifact>
}
