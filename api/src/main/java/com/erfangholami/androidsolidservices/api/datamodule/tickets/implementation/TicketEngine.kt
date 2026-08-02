package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.result.solidCatching
import java.net.URI

internal class TicketEngine(
    private val helper: SolidTicketsDataModuleHelper,
) : TicketStore {

    override suspend fun list(ownerWebId: String): SolidResult<TicketList> = solidCatching {
        TicketList(helper.getTicketSummaries(ownerWebId))
    }

    override suspend fun get(ownerWebId: String, ticketUri: String): SolidResult<Ticket> = solidCatching {
        Ticket.createFromRdf(helper.getTicket(ownerWebId, URI.create(ticketUri)))
    }

    override suspend fun create(
        ownerWebId: String,
        newTicket: NewTicket,
        storage: String?,
        artifact: ByteArray?,
        artifactContentType: String?,
        images: NewTicketImages?,
        isPrivate: Boolean,
        container: String?,
    ): SolidResult<Ticket> = solidCatching {
        val ticketRdf = helper.createTicket(
            ownerWebId,
            storage,
            newTicket,
            artifact,
            artifactContentType,
            images,
            isPrivate,
            container,
        )
        Ticket.createFromRdf(ticketRdf)
    }

    override suspend fun update(
        ownerWebId: String,
        ticketUri: String,
        updated: NewTicket,
    ): SolidResult<Ticket> = solidCatching {
        Ticket.createFromRdf(helper.updateTicket(ownerWebId, URI.create(ticketUri), updated))
    }

    override suspend fun putArtifact(
        ownerWebId: String,
        ticketUri: String,
        artifact: ByteArray,
        artifactContentType: String,
        images: NewTicketImages?,
    ): SolidResult<Ticket> = solidCatching {
        Ticket.createFromRdf(
            helper.putTicketArtifact(
                ownerWebId,
                URI.create(ticketUri),
                artifact,
                artifactContentType,
                images,
            ),
        )
    }

    override suspend fun delete(ownerWebId: String, ticketUri: String): SolidResult<Ticket> = solidCatching {
        Ticket.createFromRdf(helper.deleteTicket(ownerWebId, URI.create(ticketUri)))
    }

    override suspend fun getArtifact(
        ownerWebId: String,
        artifactUri: String,
    ): SolidResult<TicketArtifact> = solidCatching {
        helper.getTicketArtifact(ownerWebId, URI.create(artifactUri))
    }
}
