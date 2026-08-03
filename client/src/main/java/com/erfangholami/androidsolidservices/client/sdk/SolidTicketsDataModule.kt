package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_DATA_MODULES_SERVICE
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import kotlinx.coroutines.flow.Flow

/**
 * Client SDK for the Solid Tickets data module — the user's wallet: `schema:Ticket`
 * resources stored on their pod and registered in the Solid type index.
 *
 * Calls are delegated over IPC to the Android Solid Services app, which owns the login and
 * the tokens. Obtain an instance via [Solid.getTicketsDataModule]. Collect
 * [ticketsDataModuleServiceConnectionState] and wait for `true` before issuing calls. All
 * operations are `suspend` functions, return `null` when the service yields no result, and
 * throw [SolidException] on failure.
 */
public class SolidTicketsDataModule private constructor(context: Context) {

    public companion object {
        @Volatile
        private var instance: SolidTicketsDataModule? = null

        public fun getInstance(context: Context): SolidTicketsDataModule =
            instance ?: synchronized(this) {
                instance ?: SolidTicketsDataModule(context).also { instance = it }
            }

        /**
         * Drops the singleton and releases its binding, so the next [getInstance] builds a fresh
         * module. Exists only for instrumented tests; nothing in production calls it.
         */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.connector?.unbind()
                instance = null
            }
        }
    }

    private val connector = ServiceConnector(
        context,
        ANDROID_SOLID_SERVICES_DATA_MODULES_SERVICE,
    ) { binder -> IASSDataModulesService.Stub.asInterface(binder).ticketsDataModuleInterface }

    /** Hot [Flow] of the IPC service connection state; emits `true` once connected. */
    public fun ticketsDataModuleServiceConnectionState(): Flow<Boolean> = connector.connectionState

    /**
     * Returns the cached tickets index for [webId] — enough to render a wallet list without
     * fetching each ticket document.
     */
    public suspend fun listTickets(webId: String): TicketList? =
        ticketList { tickets, cb -> tickets.listTickets(webId, cb) }

    /** Reads the full ticket document at [ticketUri]. */
    public suspend fun getTicket(webId: String, ticketUri: String): Ticket? =
        ticket { tickets, cb -> tickets.getTicket(webId, ticketUri, cb) }

    /**
     * Creates a ticket on the user's pod, in its own sub-container of the tickets container.
     *
     * @param artifact Optional original file (e.g. a `.pkpass`), stored inside the ticket's
     *   container and linked from the ticket. Travels inline over Binder, so it is subject to
     *   the ~1 MB transaction limit.
     * @param artifactContentType Required when [artifact] is non-null.
     * @param images Optional pass images, stored inside the ticket's container and linked from
     *   the ticket. Travel inline over Binder like [artifact].
     * @param storage Optional pod storage (root) URL; when `null`, the registered or default
     *   tickets container is used.
     * @param isPrivate When `true` (default) a bootstrapped container is registered in the
     *   private type index; when `false`, in the public one.
     * @param container Optional container URI to use instead of the registered / default one.
     */
    public suspend fun createTicket(
        webId: String,
        newTicket: NewTicket,
        storage: String? = null,
        artifact: ByteArray? = null,
        artifactContentType: String? = null,
        images: NewTicketImages? = null,
        isPrivate: Boolean = true,
        container: String? = null,
    ): Ticket? = ticket { tickets, cb ->
        tickets.createTicket(
            webId,
            newTicket,
            storage,
            artifact,
            artifactContentType,
            images,
            isPrivate,
            container,
            cb,
        )
    }

    /**
     * Rewrites the ticket at [ticketUri] from [updated] with replace semantics: properties
     * absent from [updated] are removed. The created date and the artifact link are preserved.
     */
    public suspend fun updateTicket(
        webId: String,
        ticketUri: String,
        updated: NewTicket,
    ): Ticket? = ticket { tickets, cb -> tickets.updateTicket(webId, ticketUri, updated, cb) }

    /**
     * Replaces the ticket's stored artifact (and any provided pass-image roles) with fresh
     * bytes, refreshing the document links. Travels inline over Binder, so it is subject to
     * the ~1 MB transaction limit.
     */
    public suspend fun putTicketArtifact(
        webId: String,
        ticketUri: String,
        artifact: ByteArray,
        artifactContentType: String,
        images: NewTicketImages? = null,
    ): Ticket? = ticket { tickets, cb ->
        tickets.putTicketArtifact(webId, ticketUri, artifact, artifactContentType, images, cb)
    }

    /**
     * Deletes the ticket at [ticketUri]: its index row and its whole sub-container (document,
     * artifact, stored images). Returns the removed ticket.
     */
    public suspend fun deleteTicket(webId: String, ticketUri: String): Ticket? =
        ticket { tickets, cb -> tickets.deleteTicket(webId, ticketUri, cb) }

    /**
     * Reads a binary stored with a ticket — the original artifact or a stored pass image.
     * Subject to the ~1 MB Binder transaction limit.
     */
    public suspend fun getTicketArtifact(webId: String, artifactUri: String): TicketArtifact? =
        ticketArtifact { tickets, cb -> tickets.getTicketArtifact(webId, artifactUri, cb) }

    private suspend fun ticket(
        call: (IASSTicketsModuleInterface, IASSParcelableCallback) -> Unit,
    ): Ticket? = connector.suspendParcelable(Ticket::class.java, call)

    private suspend fun ticketList(
        call: (IASSTicketsModuleInterface, IASSParcelableCallback) -> Unit,
    ): TicketList? = connector.suspendParcelable(TicketList::class.java, call)

    private suspend fun ticketArtifact(
        call: (IASSTicketsModuleInterface, IASSParcelableCallback) -> Unit,
    ): TicketArtifact? = connector.suspendParcelable(TicketArtifact::class.java, call)
}
