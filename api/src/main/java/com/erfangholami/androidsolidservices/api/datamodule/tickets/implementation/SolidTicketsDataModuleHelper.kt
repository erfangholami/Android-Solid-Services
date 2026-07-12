package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.api.sharing.implementation.nowIsoDateTime
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKET_FILE_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKET_FRAGMENT
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketRDF
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketsIndexRDF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import java.net.URI
import java.util.UUID

internal class SolidTicketsDataModuleHelper {

    companion object {
        @Volatile
        private var INSTANCE: SolidTicketsDataModuleHelper? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidTicketsDataModuleHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidTicketsDataModuleHelper(authenticator).also { INSTANCE = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidTicketsDataModuleHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidTicketsDataModuleHelper(resourceManager).also { INSTANCE = it }
            }
        }
    }

    val solidResourceManager: SolidResourceManager

    private constructor(authenticator: Authenticator) {
        this.solidResourceManager = SolidResourceManager.getInstance(authenticator)
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.solidResourceManager = resourceManager
    }

    suspend fun resolveTicketContainers(ownerWebId: String): List<String> {
        val private = TypeIndexResolver.getPrivateTypeIndex(solidResourceManager, ownerWebId)
            .getInstanceContainers(Schema.TICKET)
        val public = TypeIndexResolver.getPublicTypeIndex(solidResourceManager, ownerWebId)
            .getInstanceContainers(Schema.TICKET)
        return (private + public).distinct()
    }

    suspend fun getTicketSummaries(ownerWebId: String): List<TicketSummary> =
        resolveTicketContainers(ownerWebId).flatMap { container ->
            runCatching { getIndex(ownerWebId, container).getTickets() }
                .getOrDefault(emptyList())
        }

    suspend fun getTicket(
        ownerWebId: String,
        ticketUri: URI
    ): TicketRDF {
        return solidResourceManager.read(ownerWebId, ticketUri, TicketRDF::class.java)
            .getOrThrow()
    }

    suspend fun createTicket(
        ownerWebId: String,
        storage: String?,
        newTicket: NewTicket,
        artifact: ByteArray?,
        artifactContentType: String?,
        isPrivate: Boolean,
        container: String?,
    ): TicketRDF {
        val ticketsContainer =
            ensureTicketsContainer(ownerWebId, storage, isPrivate, container)
        val ticketId = UUID.randomUUID().toString()
        val documentUri = "${ticketsContainer}${ticketId}${TICKET_FILE_SUFFIX}"
        val ticketUri = "${documentUri}${TICKET_FRAGMENT}"

        val now = nowIsoDateTime()
        val ticketRdf = TicketRDF(
            identifier = URI.create(ticketUri),
            contentType = "application/ld+json",
            quads = null,
            headers = null
        ).apply {
            applyNewTicket(this, newTicket)
            setCreated(now)
            setModified(now)
        }

        if (artifact != null) {
            val contentType = artifactContentType ?: "application/octet-stream"
            val artifactUri =
                "${ticketsContainer}${ticketId}${artifactExtensionFor(contentType)}"
            solidResourceManager.putRaw(
                webid = ownerWebId,
                uri = URI.create(artifactUri),
                contentType = contentType,
                body = artifact,
                ifMatch = null,
                linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
            ).getOrThrow()
            ticketRdf.setArtifactUri(artifactUri)
        }

        val created = solidResourceManager.create(ownerWebId, ticketRdf).getOrThrow()

        updateIndex(ownerWebId, ticketsContainer) {
            it.addTicket(created)
            true
        }
        return created
    }

    suspend fun updateTicket(
        ownerWebId: String,
        ticketUri: URI,
        updated: NewTicket,
    ): TicketRDF {
        val fresh = solidResourceManager.casUpdate(
            ownerWebId,
            read = { solidResourceManager.read(ownerWebId, ticketUri, TicketRDF::class.java) },
            mutate = { ticketRdf ->
                applyNewTicket(ticketRdf, updated)
                if (ticketRdf.getCreated() == null) ticketRdf.setCreated(nowIsoDateTime())
                ticketRdf.setModified(nowIsoDateTime())
                true
            },
        ).getOrThrow()

        val ticketsContainer = containerOf(ticketUri.toString())
        updateIndex(ownerWebId, ticketsContainer) {
            if (!it.updateTicket(fresh)) it.addTicket(fresh)
            true
        }
        return fresh
    }

    suspend fun deleteTicket(
        ownerWebId: String,
        ticketUri: URI,
    ): TicketRDF {
        val ticketUriString = ticketUri.toString()
        val ticketsContainer = containerOf(ticketUriString)
        val old = runCatching { getTicket(ownerWebId, ticketUri) }.getOrNull()

        if (old != null) {
            val documentUri = ticketUriString.substringBefore('#')
            solidResourceManager.delete(ownerWebId, URI.create(documentUri)).getOrThrow()
            old.getArtifactUri()?.let { artifactUri ->
                deleteTolerant(ownerWebId, URI.create(artifactUri))
            }
        }

        updateIndex(ownerWebId, ticketsContainer) {
            it.removeTicket(ticketUriString)
        }
        return old ?: TicketRDF(identifier = ticketUri).apply { setTitle("") }
    }

    suspend fun getTicketArtifact(
        ownerWebId: String,
        artifactUri: URI,
    ): TicketArtifact {
        val resource =
            solidResourceManager.read(ownerWebId, artifactUri, SolidNonRDFResource::class.java)
                .getOrThrow()
        val bytes = resource.getEntity().use { it.readBytes() }
        return TicketArtifact(artifactUri.toString(), resource.getContentType(), bytes)
    }

    private suspend fun ensureTicketsContainer(
        ownerWebId: String,
        storage: String?,
        isPrivate: Boolean,
        container: String?,
    ): String {
        val registered = resolveTicketContainers(ownerWebId)
        val target = container
            ?: registered.firstOrNull()
            ?: "${requireStorage(ownerWebId, storage)}${TICKETS_DIRECTORY_SUFFIX}"
        if (target in registered) return target

        ensureContainer(ownerWebId, URI.create(target))
        val index = TicketsIndexRDF(
            identifier = URI.create("${target}${TICKETS_INDEX_FILE_NAME}"),
            contentType = "application/ld+json",
            quads = null,
            headers = null
        )
        when (val response = solidResourceManager.create(ownerWebId, index)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (response.error.code != SolidErrorCode.CONFLICT &&
                    response.error.code != SolidErrorCode.PRECONDITION_FAILED
                ) response.getOrThrow()
        }

        if (isPrivate) {
            val typeIndex =
                TypeIndexResolver.getPrivateTypeIndex(solidResourceManager, ownerWebId)
            typeIndex.addInstanceContainer(Schema.TICKET, target)
            solidResourceManager.update(ownerWebId, typeIndex).getOrThrow()
        } else {
            val typeIndex =
                TypeIndexResolver.getPublicTypeIndex(solidResourceManager, ownerWebId)
            typeIndex.addInstanceContainer(Schema.TICKET, target)
            solidResourceManager.update(ownerWebId, typeIndex).getOrThrow()
        }
        return target
    }

    private suspend fun requireStorage(ownerWebId: String, storage: String?): String =
        storage
            ?: StorageDiscovery.discover(solidResourceManager, ownerWebId)?.toString()
            ?: error("Could not discover a storage for $ownerWebId")

    private suspend fun ensureContainer(ownerWebId: String, containerUri: URI) {
        val missing = solidResourceManager.head(ownerWebId, containerUri).let {
            it is SolidResult.Failure && it.error.code == SolidErrorCode.NOT_FOUND
        }
        if (missing) {
            solidResourceManager.create(ownerWebId, SolidContainer(containerUri)).getOrThrow()
        }
    }

    private suspend fun deleteTolerant(ownerWebId: String, uri: URI) {
        when (val result = solidResourceManager.delete(ownerWebId, uri)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (result.error.code == SolidErrorCode.NOT_FOUND) Unit else result.getOrThrow()
        }
    }

    private suspend fun getIndex(
        ownerWebId: String,
        containerUri: String,
    ): TicketsIndexRDF {
        return solidResourceManager.read(
            ownerWebId,
            URI.create("${containerUri}${TICKETS_INDEX_FILE_NAME}"),
            TicketsIndexRDF::class.java
        ).getOrThrow()
    }

    /**
     * Compare-and-swap read-modify-write of a container's tickets index: [mutate] the
     * fresh index in place (return `false` to skip a no-op write), with `If-Match` +
     * retry so a concurrent ticket add/update/remove can't be lost.
     */
    private suspend fun updateIndex(
        ownerWebId: String,
        containerUri: String,
        mutate: (TicketsIndexRDF) -> Boolean,
    ) {
        val indexUri = URI.create("${containerUri}${TICKETS_INDEX_FILE_NAME}")
        solidResourceManager.casUpdate(
            ownerWebId,
            read = { solidResourceManager.read(ownerWebId, indexUri, TicketsIndexRDF::class.java) },
            mutate = mutate,
        ).getOrThrow()
    }

    private fun applyNewTicket(target: TicketRDF, newTicket: NewTicket) {
        require(newTicket.title.isNotBlank()) { "A ticket needs a non-blank title" }
        target.setTitle(newTicket.title)
        target.setDescription(newTicket.description)
        target.setTicketNumber(newTicket.ticketNumber)
        target.setTicketToken(newTicket.ticketToken)
        target.setBarcodeFormat(newTicket.barcodeFormat)
        target.setCategory(newTicket.category)
        target.setIssuerName(newTicket.issuerName)
        target.setUnderName(newTicket.underName)
        target.setSeat(newTicket.seat)
        target.setTotalPrice(newTicket.totalPrice)
        target.setPriceCurrency(newTicket.priceCurrency)
        target.setDateIssued(newTicket.dateIssued)
        target.setEvent(newTicket.event)
        target.setValidFrom(newTicket.validFrom)
        target.setValidThrough(newTicket.validThrough)
        target.setSource(newTicket.source)
    }

    private fun containerOf(ticketUri: String): String {
        val documentUri = ticketUri.substringBefore('#')
        return documentUri.substring(0, documentUri.lastIndexOf("/") + 1)
    }

    private fun artifactExtensionFor(contentType: String): String =
        when (contentType.lowercase().substringBefore(';').trim()) {
            "application/vnd.apple.pkpass" -> ".pkpass"
            "application/zip" -> ".zip"
            "application/json" -> ".json"
            "application/pdf" -> ".pdf"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            else -> ".bin"
        }
}
