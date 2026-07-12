package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.runResult
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import java.net.URI

/** [TicketStore] backed by [SolidTicketsDataModuleHelper]'s pod access. */
internal class TicketEngine(
    private val helper: SolidTicketsDataModuleHelper,
) : TicketStore {

    override suspend fun list(ownerWebId: String): SolidResult<TicketList> = runResult {
        TicketList(helper.getTicketSummaries(ownerWebId))
    }

    override suspend fun get(ownerWebId: String, ticketUri: String): SolidResult<Ticket> = runResult {
        Ticket.createFromRdf(helper.getTicket(ownerWebId, URI.create(ticketUri)))
    }

    override suspend fun create(
        ownerWebId: String,
        newTicket: NewTicket,
        storage: String?,
        artifact: ByteArray?,
        artifactContentType: String?,
        isPrivate: Boolean,
        container: String?,
    ): SolidResult<Ticket> = runResult {
        val ticketRdf = helper.createTicket(
            ownerWebId,
            storage,
            newTicket,
            artifact,
            artifactContentType,
            isPrivate,
            container,
        )
        Ticket.createFromRdf(ticketRdf)
    }

    override suspend fun update(
        ownerWebId: String,
        ticketUri: String,
        updated: NewTicket,
    ): SolidResult<Ticket> = runResult {
        Ticket.createFromRdf(helper.updateTicket(ownerWebId, URI.create(ticketUri), updated))
    }

    override suspend fun delete(ownerWebId: String, ticketUri: String): SolidResult<Ticket> = runResult {
        Ticket.createFromRdf(helper.deleteTicket(ownerWebId, URI.create(ticketUri)))
    }

    override suspend fun getArtifact(
        ownerWebId: String,
        artifactUri: String,
    ): SolidResult<TicketArtifact> = runResult {
        helper.getTicketArtifact(ownerWebId, URI.create(artifactUri))
    }
}
