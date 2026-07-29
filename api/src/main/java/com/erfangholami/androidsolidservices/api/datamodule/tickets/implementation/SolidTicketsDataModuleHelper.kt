package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.api.sharing.implementation.nowIsoDateTime
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.LEGACY_TICKETS_INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_INDEX_NAME
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKET_DOCUMENT_NAME
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKET_FRAGMENT
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary
import com.erfangholami.androidsolidservices.shared.model.typeindex.SettingTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketRDF
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketsIndexRDF
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import java.net.URI
import java.util.UUID

internal class SolidTicketsDataModuleHelper {

    companion object {
        @Volatile
        private var instance: SolidTicketsDataModuleHelper? = null

        internal fun resetForTest() {
            instance = null
        }

        fun getInstance(
            authenticator: Authenticator,
        ): SolidTicketsDataModuleHelper {
            return instance ?: synchronized(this) {
                instance ?: SolidTicketsDataModuleHelper(authenticator).also { instance = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidTicketsDataModuleHelper {
            return instance ?: synchronized(this) {
                instance ?: SolidTicketsDataModuleHelper(resourceManager).also { instance = it }
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

    suspend fun resolveTicketIndexes(ownerWebId: String): List<String> {
        val indexes = typeIndexes(ownerWebId)
        val instances = indexes.flatMap { it.getInstances(Schema.TICKET) }
        val legacy = indexes.flatMap { it.getInstanceContainers(Schema.TICKET) }
            .map { "$it$LEGACY_TICKETS_INDEX_FILE_NAME" }
        return (instances + legacy).distinct()
    }

    suspend fun getTicketSummaries(ownerWebId: String): List<TicketSummary> =
        resolveTicketIndexes(ownerWebId).flatMap { indexUri ->
            runCatching { readIndex(ownerWebId, indexUri).getTickets() }
                .getOrDefault(emptyList())
        }

    suspend fun getTicket(
        ownerWebId: String,
        ticketUri: URI
    ): TicketRDF {
        return solidResourceManager.read(ownerWebId, ticketUri.toString(), TicketRDF::class.java)
            .getOrThrow()
    }

    suspend fun createTicket(
        ownerWebId: String,
        storage: String?,
        newTicket: NewTicket,
        artifact: ByteArray?,
        artifactContentType: String?,
        images: NewTicketImages?,
        isPrivate: Boolean,
        container: String?,
    ): TicketRDF {
        val indexUri = ensureTicketsIndex(ownerWebId, storage, isPrivate, container)
        val ticketContainer = "${containerOf(indexUri)}${UUID.randomUUID()}/"
        val documentUri = "${ticketContainer}${TICKET_DOCUMENT_NAME}"
        val ticketUri = "${documentUri}${TICKET_FRAGMENT}"

        ensureContainer(ownerWebId, ticketContainer)

        val now = nowIsoDateTime()
        val ticketRdf = TicketRDF(
            identifier = ticketUri,
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
            val artifactUri = "${ticketContainer}artifact${artifactExtensionFor(contentType)}"
            putBinary(ownerWebId, artifactUri, contentType, artifact)
            ticketRdf.setArtifactUri(artifactUri)
        }
        if (images != null && !images.isEmpty) {
            ticketRdf.setImages(uploadImages(ownerWebId, ticketContainer, images))
        }

        val created = solidResourceManager.create(ownerWebId, ticketRdf).getOrThrow()

        updateIndex(ownerWebId, indexUri) {
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
            read = { solidResourceManager.read(ownerWebId, ticketUri.toString(), TicketRDF::class.java) },
            mutate = { ticketRdf ->
                applyNewTicket(ticketRdf, updated)
                if (ticketRdf.getCreated() == null) ticketRdf.setCreated(nowIsoDateTime())
                ticketRdf.setModified(nowIsoDateTime())
                true
            },
        ).getOrThrow()

        updateIndex(ownerWebId, resolveIndexFor(ownerWebId, ticketUri.toString())) {
            if (!it.updateTicket(fresh)) it.addTicket(fresh)
            true
        }
        return fresh
    }

    suspend fun putTicketArtifact(
        ownerWebId: String,
        ticketUri: URI,
        artifact: ByteArray,
        artifactContentType: String,
        images: NewTicketImages?,
    ): TicketRDF {
        val ticketUriString = ticketUri.toString()
        val documentUri = ticketUriString.substringBefore('#')
        val holder = containerOf(documentUri)
        val existing = getTicket(ownerWebId, ticketUri)
        val artifactUri = existing.getArtifactUri()
            ?: if (isPerTicketDocument(documentUri)) {
                "${holder}artifact${artifactExtensionFor(artifactContentType)}"
            } else {
                documentUri.substringBeforeLast('.') + artifactExtensionFor(artifactContentType)
            }
        putBinary(ownerWebId, artifactUri, artifactContentType, artifact)
        val storedImages = images
            ?.takeIf { !it.isEmpty && isPerTicketDocument(documentUri) }
            ?.let { uploadImages(ownerWebId, holder, it) }
        val fresh = solidResourceManager.casUpdate(
            ownerWebId,
            read = {
                solidResourceManager.read(ownerWebId, ticketUriString, TicketRDF::class.java)
            },
            mutate = { ticketRdf ->
                ticketRdf.setArtifactUri(artifactUri)
                if (storedImages != null) {
                    val current = ticketRdf.getImages()
                    ticketRdf.setImages(
                        TicketImages(
                            logo = storedImages.logo ?: current?.logo,
                            icon = storedImages.icon ?: current?.icon,
                            strip = storedImages.strip ?: current?.strip,
                            thumbnail = storedImages.thumbnail ?: current?.thumbnail,
                            footer = storedImages.footer ?: current?.footer,
                            background = storedImages.background ?: current?.background,
                        ),
                    )
                }
                ticketRdf.setModified(nowIsoDateTime())
                true
            },
        ).getOrThrow()
        updateIndex(ownerWebId, resolveIndexFor(ownerWebId, ticketUriString)) {
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
        val documentUri = ticketUriString.substringBefore('#')
        val old = runCatching { getTicket(ownerWebId, ticketUri) }.getOrNull()

        if (isPerTicketDocument(documentUri)) {
            deleteTolerant(ownerWebId, containerOf(documentUri))
        } else if (old != null) {
            solidResourceManager.delete(ownerWebId, documentUri).getOrThrow()
            old.getArtifactUri()?.let { artifactUri ->
                deleteTolerant(ownerWebId, artifactUri)
            }
        }

        updateIndex(ownerWebId, resolveIndexFor(ownerWebId, ticketUriString)) {
            it.removeTicket(ticketUriString)
        }
        return old ?: TicketRDF(identifier = ticketUriString).apply { setTitle("") }
    }

    suspend fun getTicketArtifact(
        ownerWebId: String,
        artifactUri: URI,
    ): TicketArtifact {
        val resource =
            solidResourceManager.read(ownerWebId, artifactUri.toString(), SolidNonRDFResource::class.java)
                .getOrThrow()
        val bytes = resource.getEntity().use { it.readBytes() }
        return TicketArtifact(artifactUri.toString(), resource.getContentType(), bytes)
    }

    private suspend fun ensureTicketsIndex(
        ownerWebId: String,
        storage: String?,
        isPrivate: Boolean,
        container: String?,
    ): String {
        val indexes = typeIndexes(ownerWebId)
        val instances = indexes.flatMap { it.getInstances(Schema.TICKET) }
        val legacyContainers = indexes.flatMap { it.getInstanceContainers(Schema.TICKET) }

        if (container == null) {
            instances.firstOrNull()?.let { return it }
        } else {
            instances.firstOrNull { containerOf(it) == container }?.let { return it }
        }

        val target = container
            ?: legacyContainers.firstOrNull()
            ?: "${requireStorage(ownerWebId, storage)}${TICKETS_DIRECTORY_SUFFIX}"
        val indexUri = target +
            if (target in legacyContainers) LEGACY_TICKETS_INDEX_FILE_NAME else TICKETS_INDEX_NAME

        ensureContainer(ownerWebId, target)
        val index = TicketsIndexRDF(
            identifier = indexUri,
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

        TypeIndexResolver.addInstance(
            resourceManager = solidResourceManager,
            webIdString = ownerWebId,
            forClass = Schema.TICKET,
            instanceUri = indexUri,
            isPrivate = isPrivate,
        )
        if (target in legacyContainers) {
            TypeIndexResolver.removeResource(solidResourceManager, ownerWebId, target)
        }
        return indexUri
    }

    private suspend fun resolveIndexFor(ownerWebId: String, ticketUri: String): String {
        val ticketsContainer = ticketsContainerOf(ticketUri.substringBefore('#'))
        return resolveTicketIndexes(ownerWebId).firstOrNull { containerOf(it) == ticketsContainer }
            ?: "${ticketsContainer}${TICKETS_INDEX_NAME}"
    }

    private suspend fun typeIndexes(ownerWebId: String): List<SettingTypeIndex> = listOf(
        TypeIndexResolver.getPrivateTypeIndex(solidResourceManager, ownerWebId),
        TypeIndexResolver.getPublicTypeIndex(solidResourceManager, ownerWebId),
    )

    private suspend fun requireStorage(ownerWebId: String, storage: String?): String =
        storage
            ?: StorageDiscovery.discover(solidResourceManager, ownerWebId)
            ?: error("Could not discover a storage for $ownerWebId")

    private suspend fun ensureContainer(ownerWebId: String, containerUri: String) {
        solidResourceManager.ensureContainer(ownerWebId, containerUri).getOrThrow()
    }

    private suspend fun uploadImages(
        ownerWebId: String,
        ticketContainer: String,
        images: NewTicketImages,
    ): TicketImages = TicketImages(
        logo = putImage(ownerWebId, ticketContainer, "logo", images.logo),
        icon = putImage(ownerWebId, ticketContainer, "icon", images.icon),
        strip = putImage(ownerWebId, ticketContainer, "strip", images.strip),
        thumbnail = putImage(ownerWebId, ticketContainer, "thumbnail", images.thumbnail),
        footer = putImage(ownerWebId, ticketContainer, "footer", images.footer),
        background = putImage(ownerWebId, ticketContainer, "background", images.background),
    )

    private suspend fun putImage(
        ownerWebId: String,
        ticketContainer: String,
        role: String,
        bytes: ByteArray?,
    ): String? {
        if (bytes == null) return null
        val uri = "${ticketContainer}${role}.png"
        putBinary(ownerWebId, uri, "image/png", bytes)
        return uri
    }

    private suspend fun putBinary(
        ownerWebId: String,
        uri: String,
        contentType: String,
        body: ByteArray,
    ) {
        solidResourceManager.putRaw(
            webId = ownerWebId,
            uri = uri,
            contentType = contentType,
            body = body,
            ifMatch = null,
            linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
        ).getOrThrow()
    }

    private suspend fun deleteTolerant(ownerWebId: String, uri: String) {
        when (val result = solidResourceManager.delete(ownerWebId, uri)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (result.error.code == SolidErrorCode.NOT_FOUND) Unit else result.getOrThrow()
        }
    }

    private suspend fun readIndex(
        ownerWebId: String,
        indexUri: String,
    ): TicketsIndexRDF {
        return solidResourceManager.read(ownerWebId, indexUri, TicketsIndexRDF::class.java)
            .getOrThrow()
    }

    private suspend fun updateIndex(
        ownerWebId: String,
        indexUri: String,
        mutate: (TicketsIndexRDF) -> Boolean,
    ) {
        solidResourceManager.casUpdate(
            ownerWebId,
            read = { solidResourceManager.read(ownerWebId, indexUri, TicketsIndexRDF::class.java) },
            mutate = mutate,
        ).getOrThrow()
    }

    private fun applyNewTicket(target: TicketRDF, newTicket: NewTicket) {
        target.setTicketData(newTicket)
    }

    private fun isPerTicketDocument(documentUri: String): Boolean =
        documentUri.endsWith("/${TICKET_DOCUMENT_NAME}")

    private fun containerOf(documentUri: String): String =
        documentUri.substring(0, documentUri.lastIndexOf('/') + 1)

    private fun parentContainerOf(containerUri: String): String =
        containerOf(containerUri.dropLast(1))

    private fun ticketsContainerOf(documentUri: String): String {
        val holder = containerOf(documentUri)
        return if (isPerTicketDocument(documentUri)) parentContainerOf(holder) else holder
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
