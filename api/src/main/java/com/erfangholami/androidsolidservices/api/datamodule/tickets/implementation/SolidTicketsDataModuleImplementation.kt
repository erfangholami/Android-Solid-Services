package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.runResult
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import java.net.URI

internal class SolidTicketsDataModuleImplementation : SolidTicketsDataModule {

    companion object {
        @Volatile
        private var INSTANCE: SolidTicketsDataModule? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidTicketsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidTicketsDataModuleImplementation(authenticator).also { INSTANCE = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidTicketsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidTicketsDataModuleImplementation(resourceManager).also { INSTANCE = it }
            }
        }
    }

    val helper: SolidTicketsDataModuleHelper

    private constructor(authenticator: Authenticator) {
        this.helper = SolidTicketsDataModuleHelper.getInstance(authenticator)
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.helper = SolidTicketsDataModuleHelper.getInstance(resourceManager)
    }

    override suspend fun getTickets(
        ownerWebId: String,
    ): DataModuleResult<TicketList> = runResult {
        TicketList(helper.getTicketSummaries(ownerWebId))
    }

    override suspend fun getTicket(
        ownerWebId: String,
        ticketUri: String,
    ): DataModuleResult<Ticket> = runResult {
        Ticket.createFromRdf(helper.getTicket(ownerWebId, URI.create(ticketUri)))
    }

    override suspend fun createTicket(
        ownerWebId: String,
        storage: String,
        newTicket: NewTicket,
        artifact: ByteArray?,
        artifactContentType: String?,
        isPrivate: Boolean,
        container: String?,
    ): DataModuleResult<Ticket> = runResult {
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

    override suspend fun updateTicket(
        ownerWebId: String,
        ticketUri: String,
        updated: NewTicket,
    ): DataModuleResult<Ticket> = runResult {
        Ticket.createFromRdf(helper.updateTicket(ownerWebId, URI.create(ticketUri), updated))
    }

    override suspend fun deleteTicket(
        ownerWebId: String,
        ticketUri: String,
    ): DataModuleResult<Ticket> = runResult {
        Ticket.createFromRdf(helper.deleteTicket(ownerWebId, URI.create(ticketUri)))
    }

    override suspend fun getTicketArtifact(
        ownerWebId: String,
        artifactUri: String,
    ): DataModuleResult<TicketArtifact> = runResult {
        helper.getTicketArtifact(ownerWebId, URI.create(artifactUri))
    }
}
