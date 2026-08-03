package com.erfangholami.androidsolidservices.api.datamodule.core

import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource

/**
 * What a data module contributes about itself, and nothing more: the classes it registers, the
 * names it mints, and how to build an empty index document.
 *
 * Everything else about keeping a collection of entities on a pod — bootstrapping, registering,
 * allocating, CAS-rewriting the index, storing attachments — is [EntityCollection]'s job and is
 * written once. A module supplies this description; it does not supply machinery.
 *
 * @param registeredTypeIri the class registered in the type index, whose instance is the index
 *   document. Discovery follows this registration, never a guessed path.
 * @param entityTypeIri the class a single entity carries, used to locate one inside a container
 *   that was shared with us.
 * @param rootSuffix path appended to a storage root when allocating this module's container,
 *   always below `datamodule/`.
 * @param entityDocumentName document name minted for an entity inside its own container.
 * @param entityFragment fragment identifying an entity's primary subject within its document.
 * @param indexDocumentName document name minted for the index when bootstrapping.
 * @param indexCodec the RDF type the index document is read as.
 * @param newIndex builds an empty index document at the given URI.
 */
internal class CollectionSpec<I : SolidRDFResource>(
    val registeredTypeIri: String,
    val entityTypeIri: String,
    val rootSuffix: String,
    val entityDocumentName: String,
    val entityFragment: String,
    val indexDocumentName: String,
    val indexCodec: Class<I>,
    val newIndex: (String) -> I,
)

/** Where a newly allocated entity lives: its own container, its document, and its subject. */
internal class EntityLocation(
    val container: String,
    val documentUri: String,
    val subjectUri: String,
)
