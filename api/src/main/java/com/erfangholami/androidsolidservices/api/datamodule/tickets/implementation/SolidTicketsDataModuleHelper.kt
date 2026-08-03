package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.core.CollectionSpec
import com.erfangholami.androidsolidservices.api.datamodule.core.EntityCollection
import com.erfangholami.androidsolidservices.api.datamodule.core.deleteTolerant
import com.erfangholami.androidsolidservices.api.datamodule.core.extensionForContentType
import com.erfangholami.androidsolidservices.api.datamodule.core.putAttachment
import com.erfangholami.androidsolidservices.api.datamodule.core.putBinary
import com.erfangholami.androidsolidservices.api.datamodule.core.readAttachment
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_INDEX_NAME
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKET_DOCUMENT_NAME
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKET_FRAGMENT
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketRDF
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketsIndexRDF
import com.erfangholami.androidsolidservices.shared.util.nowIsoDateTime
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import java.net.URI

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

        private val SPEC = CollectionSpec(
            registeredTypeIri = Schema.TICKET,
            entityTypeIri = Schema.TICKET,
            rootSuffix = TICKETS_DIRECTORY_SUFFIX,
            entityDocumentName = TICKET_DOCUMENT_NAME,
            entityFragment = TICKET_FRAGMENT,
            indexDocumentName = TICKETS_INDEX_NAME,
            indexCodec = TicketsIndexRDF::class.java,
            newIndex = { TicketsIndexRDF(it, "application/ld+json", null, null) },
        )
    }

    val solidResourceManager: SolidResourceManager
    private val collection: EntityCollection<TicketsIndexRDF>

    private constructor(authenticator: Authenticator) :
        this(SolidResourceManager.getInstance(authenticator))

    private constructor(resourceManager: SolidResourceManager) {
        this.solidResourceManager = resourceManager
        this.collection = EntityCollection(resourceManager, SPEC)
    }

    suspend fun resolveTicketIndexes(ownerWebId: String): List<String> =
        collection.indexes(ownerWebId)

    suspend fun getTicketSummaries(ownerWebId: String): List<TicketSummary> =
        collection.indexes(ownerWebId).flatMap { indexUri ->
            runCatching { collection.readIndex(ownerWebId, indexUri).getTickets() }
                .getOrDefault(emptyList())
        }

    suspend fun getTicket(
        ownerWebId: String,
        ticketUri: URI
    ): TicketRDF {
        return solidResourceManager.read(ownerWebId, ticketUri.toString(), TicketRDF::class.java)
            .getOrThrow()
    }

    suspend fun findTicketInContainer(ownerWebId: String, containerUri: String): TicketRDF {
        val found = collection.findEntity(ownerWebId, containerUri)
        val raw = found.raw ?: return getTicket(ownerWebId, URI.create(found.subjectUri))
        return TicketRDF(
            identifier = found.subjectUri,
            contentType = raw.getContentType(),
            quads = raw.getAllQuads(),
            headers = raw.getHeaders(),
        )
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
        val indexUri = collection.ensureIndex(ownerWebId, storage, isPrivate, container)
        val location = collection.allocateEntity(ownerWebId, indexUri)

        val now = nowIsoDateTime()
        val ticketRdf = TicketRDF(
            identifier = location.subjectUri,
            contentType = "application/ld+json",
            quads = null,
            headers = null
        ).apply {
            setTicketData(newTicket)
            setCreated(now)
            setModified(now)
        }

        if (artifact != null) {
            val contentType = artifactContentType ?: "application/octet-stream"
            ticketRdf.setArtifactUri(
                putAttachment(
                    resourceManager = solidResourceManager,
                    ownerWebId = ownerWebId,
                    container = location.container,
                    role = ARTIFACT_ROLE,
                    contentType = contentType,
                    body = artifact,
                ),
            )
        }
        if (images != null && !images.isEmpty) {
            ticketRdf.setImages(uploadImages(ownerWebId, location.container, images))
        }

        val created = solidResourceManager.create(ownerWebId, ticketRdf).getOrThrow()

        collection.updateIndex(ownerWebId, indexUri) {
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
                ticketRdf.setTicketData(updated)
                if (ticketRdf.getCreated() == null) ticketRdf.setCreated(nowIsoDateTime())
                ticketRdf.setModified(nowIsoDateTime())
                true
            },
        ).getOrThrow()

        reindex(ownerWebId, ticketUri.toString(), fresh)
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
        val holder = collection.entityContainerOf(documentUri)
        val existing = getTicket(ownerWebId, ticketUri)
        val artifactUri = existing.getArtifactUri()
            ?: "$holder$ARTIFACT_ROLE${extensionForContentType(artifactContentType)}"
        putBinary(solidResourceManager, ownerWebId, artifactUri, artifactContentType, artifact)
        val storedImages = images
            ?.takeIf { !it.isEmpty }
            ?.let { uploadImages(ownerWebId, holder, it) }
        val fresh = solidResourceManager.casUpdate(
            ownerWebId,
            read = {
                solidResourceManager.read(ownerWebId, ticketUriString, TicketRDF::class.java)
            },
            mutate = { ticketRdf ->
                ticketRdf.setArtifactUri(artifactUri)
                if (storedImages != null) ticketRdf.mergeImages(storedImages)
                ticketRdf.setModified(nowIsoDateTime())
                true
            },
        ).getOrThrow()
        reindex(ownerWebId, ticketUriString, fresh)
        return fresh
    }

    suspend fun deleteTicket(
        ownerWebId: String,
        ticketUri: URI,
    ): TicketRDF {
        val ticketUriString = ticketUri.toString()
        val documentUri = ticketUriString.substringBefore('#')
        val old = runCatching { getTicket(ownerWebId, ticketUri) }.getOrNull()

        deleteTolerant(solidResourceManager, ownerWebId, collection.entityContainerOf(documentUri))

        collection.updateIndex(ownerWebId, collection.indexFor(ownerWebId, ticketUriString)) {
            it.removeTicket(ticketUriString)
        }
        return old ?: TicketRDF(identifier = ticketUriString).apply { setTitle("") }
    }

    suspend fun getTicketArtifact(
        ownerWebId: String,
        artifactUri: URI,
    ): TicketArtifact {
        val attachment = readAttachment(solidResourceManager, ownerWebId, artifactUri.toString())
        return TicketArtifact(attachment.uri, attachment.contentType, attachment.bytes)
    }

    private suspend fun reindex(ownerWebId: String, ticketUri: String, fresh: TicketRDF) {
        collection.updateIndex(ownerWebId, collection.indexFor(ownerWebId, ticketUri)) {
            if (!it.updateTicket(fresh)) it.addTicket(fresh)
            true
        }
    }

    private fun TicketRDF.mergeImages(stored: TicketImages) {
        val current = getImages()
        setImages(
            TicketImages(
                logo = stored.logo ?: current?.logo,
                icon = stored.icon ?: current?.icon,
                strip = stored.strip ?: current?.strip,
                thumbnail = stored.thumbnail ?: current?.thumbnail,
                footer = stored.footer ?: current?.footer,
                background = stored.background ?: current?.background,
            ),
        )
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
    ): String? = bytes?.let {
        putAttachment(
            resourceManager = solidResourceManager,
            ownerWebId = ownerWebId,
            container = ticketContainer,
            role = role,
            contentType = "image/png",
            body = it,
        )
    }
}

private const val ARTIFACT_ROLE = "artifact"
