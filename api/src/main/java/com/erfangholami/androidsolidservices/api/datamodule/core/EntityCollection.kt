package com.erfangholami.androidsolidservices.api.datamodule.core

import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import java.util.UUID

/**
 * A collection of entities kept on a pod behind one index document, written once for every data
 * module.
 *
 * The pattern it owns is the whole of what tickets, contacts and every module after them do the
 * same way: discover the index through its type-index registration, bootstrap the container and
 * index when absent, allocate one container per entity, rewrite the index under compare-and-set
 * on every write, and store attachments beside the entity. What differs between modules — the RDF, the
 * names, the classes — arrives as a [CollectionSpec].
 *
 * Modules whose collection is nested (contacts: books, each with its own people index) use the
 * registry and allocation verbs here and keep their own thin layer for the nesting, rather than
 * this class growing a mode for it.
 */
internal class EntityCollection<I : SolidRDFResource>(
    private val resourceManager: SolidResourceManager,
    private val spec: CollectionSpec<I>,
) {

    /** Every index document registered for this module. */
    suspend fun indexes(ownerWebId: String): List<String> =
        registeredInstances(resourceManager, ownerWebId, spec.registeredTypeIri).distinct()

    /**
     * The index to write to, bootstrapping and registering it when this pod has none.
     *
     * [container] pins which collection to use on a pod that has several; without it the first
     * registration wins, then a freshly allocated container under `datamodule/`.
     */
    suspend fun ensureIndex(
        ownerWebId: String,
        storage: String?,
        isPrivate: Boolean,
        container: String?,
    ): String {
        val instances = registeredInstances(resourceManager, ownerWebId, spec.registeredTypeIri)

        val existing = if (container == null) {
            instances.firstOrNull()
        } else {
            instances.firstOrNull { containerOf(it) == container }
        }
        if (existing != null) return existing

        val target = container
            ?: "${requireStorage(resourceManager, ownerWebId, storage)}${spec.rootSuffix}"
        val indexUri = target + spec.indexDocumentName

        resourceManager.ensureContainer(ownerWebId, target).getOrThrow()
        createIfAbsent(ownerWebId, spec.newIndex(indexUri))

        TypeIndexResolver.addInstance(
            resourceManager = resourceManager,
            webIdString = ownerWebId,
            forClass = spec.registeredTypeIri,
            instanceUri = indexUri,
            isPrivate = isPrivate,
        )
        return indexUri
    }

    /**
     * The index holding [entityUri], resolved by matching containers rather than by guessing a
     * name, falling back to the conventional name so a first write can still land.
     */
    suspend fun indexFor(ownerWebId: String, entityUri: String): String {
        val collection = collectionContainerOf(entityUri.substringBefore('#'))
        return indexes(ownerWebId).firstOrNull { containerOf(it) == collection }
            ?: "$collection${spec.indexDocumentName}"
    }

    suspend fun readIndex(ownerWebId: String, indexUri: String): I =
        resourceManager.read(ownerWebId, indexUri, spec.indexCodec).getOrThrow()

    /** Rewrites the index under compare-and-set; [mutate] returns false to skip the write. */
    suspend fun updateIndex(
        ownerWebId: String,
        indexUri: String,
        mutate: (I) -> Boolean,
    ) {
        resourceManager.casUpdate(
            ownerWebId,
            read = { resourceManager.read(ownerWebId, indexUri, spec.indexCodec) },
            mutate = mutate,
        ).getOrThrow()
    }

    /** Allocates and creates the container for one new entity beside [indexUri]. */
    suspend fun allocateEntity(ownerWebId: String, indexUri: String): EntityLocation {
        val container = "${containerOf(indexUri)}${UUID.randomUUID()}/"
        val documentUri = "$container${spec.entityDocumentName}"
        resourceManager.ensureContainer(ownerWebId, container).getOrThrow()
        return EntityLocation(container, documentUri, "$documentUri${spec.entityFragment}")
    }

    /** The container holding the whole entity: every entity owns `{collection}/{uuid}/`. */
    fun entityContainerOf(documentUri: String): String = containerOf(documentUri)

    /** Locates this module's single entity inside a container someone shared with us. */
    suspend fun findEntity(ownerWebId: String, containerUri: String): ContainerEntity =
        findEntityInContainer(
            resourceManager = resourceManager,
            ownerWebId = ownerWebId,
            containerUri = containerUri,
            entityTypeIri = spec.entityTypeIri,
            conventionalDocumentName = spec.entityDocumentName.substringBefore('#'),
            subjectFragment = spec.entityFragment,
        )

    /** The collection container an entity document belongs to. */
    private fun collectionContainerOf(documentUri: String): String =
        containerOf(containerOf(documentUri).dropLast(1))

    private suspend fun createIfAbsent(ownerWebId: String, index: I) {
        when (val response = resourceManager.create(ownerWebId, index)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (response.error.code != SolidErrorCode.CONFLICT &&
                    response.error.code != SolidErrorCode.PRECONDITION_FAILED
                ) {
                    response.getOrThrow()
                }
        }
    }
}
