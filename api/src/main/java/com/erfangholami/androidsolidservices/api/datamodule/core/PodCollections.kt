package com.erfangholami.androidsolidservices.api.datamodule.core

import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.typeindex.SettingTypeIndex
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF

/**
 * The machinery every data module needs to keep a collection of entities on a pod, written once.
 *
 * A data module differs from its siblings in its RDF, its layout names and its attachments —
 * never in how a container gets bootstrapped, how a binary is named, or how an entity is located
 * inside a container someone shared. Those live here so module #3 inherits them instead of
 * copying them, and so a fix lands in one place.
 */

/** An entity found inside a container, plus the document it was read from. */
internal class ContainerEntity(
    val documentUri: String,
    val subjectUri: String,
    val raw: SolidRDFResource?,
)

/**
 * Locates the single entity of [entityTypeIri] inside [containerUri] — the receiving half of
 * entity sharing, where all that is known is "someone granted me this container".
 *
 * Every hop is a followed link: the container's membership is listed, the conventional document
 * name is preferred when present, and otherwise each member is read until one carries the type.
 * [ContainerEntity.raw] is null when the conventional document was found, leaving the caller to
 * do its own typed read.
 */
internal suspend fun findEntityInContainer(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    containerUri: String,
    entityTypeIri: String,
    conventionalDocumentName: String,
    subjectFragment: String,
): ContainerEntity {
    val container = containerUri.ensureTrailingSlash()
    val members = resourceManager.listContainer(ownerWebId, container).getOrThrow()

    members.firstOrNull { it.identifier.substringBefore('#').endsWith("/$conventionalDocumentName") }
        ?.let { conventional ->
            val documentUri = conventional.identifier.substringBefore('#')
            return ContainerEntity(documentUri, "$documentUri$subjectFragment", raw = null)
        }

    members.forEach { member ->
        if (member.identifier.endsWith("/")) return@forEach
        val raw = resourceManager
            .read(ownerWebId, member.identifier, SolidRDFResource::class.java)
            .getOrNull() ?: return@forEach
        val subject = raw.getAllQuads()
            .firstOrNull { it.predicate == RDF.TYPE && it.`object` == entityTypeIri }
            ?.subject
        if (subject != null) return ContainerEntity(member.identifier, subject, raw)
    }

    throw SolidError.fromHttp(404, "No $entityTypeIri entity found in $container").asException()
}

/** The URIs registered as instances of [classIri] across both type indexes. */
internal suspend fun registeredInstances(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    classIri: String,
): List<String> = typeIndexes(resourceManager, ownerWebId).flatMap { it.getInstances(classIri) }

private suspend fun typeIndexes(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
): List<SettingTypeIndex> = listOf(
    TypeIndexResolver.getPrivateTypeIndex(resourceManager, ownerWebId),
    TypeIndexResolver.getPublicTypeIndex(resourceManager, ownerWebId),
)

/**
 * Stores a binary beside an entity as `{container}{role}{ext}` and returns its URI.
 *
 * The name carries the role, not the bytes' identity, so replacing an attachment overwrites in
 * place instead of accumulating orphans; the extension comes from the one shared table.
 */
internal suspend fun putAttachment(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    container: String,
    role: String,
    contentType: String,
    body: ByteArray,
): String {
    val uri = "$container$role${extensionForContentType(contentType)}"
    putBinary(resourceManager, ownerWebId, uri, contentType, body)
    return uri
}

/**
 * Stores a binary at an exact URI, declared as a non-RDF source.
 *
 * Used when the URI is already decided — replacing an attachment that a pod already links to,
 * where re-deriving the name would move it and orphan the link.
 */
internal suspend fun putBinary(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    uri: String,
    contentType: String,
    body: ByteArray,
) {
    resourceManager.putRaw(
        webId = ownerWebId,
        uri = uri,
        contentType = contentType,
        body = body,
        ifMatch = null,
        linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
    ).getOrThrow()
}

/** A stored binary read back whole, with the content type the pod reports for it. */
internal class PodAttachment(
    val uri: String,
    val contentType: String,
    val bytes: ByteArray,
)

/** Reads an attachment's bytes, from this pod or any other the caller may read. */
internal suspend fun readAttachment(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    uri: String,
): PodAttachment {
    val resource = resourceManager
        .read(ownerWebId, uri, SolidNonRDFResource::class.java)
        .getOrThrow()
    return PodAttachment(uri, resource.getContentType(), resource.getEntity().use { it.readBytes() })
}

/** Deletes [uri], treating "already gone" as success — deletion is idempotent by intent. */
internal suspend fun deleteTolerant(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    uri: String,
) {
    when (val result = resourceManager.delete(ownerWebId, uri)) {
        is SolidResult.Success -> Unit
        is SolidResult.Failure ->
            if (result.error.code != SolidErrorCode.NOT_FOUND) result.getOrThrow()
    }
}

/** The storage to allocate in: the caller's choice, else the one the profile advertises. */
internal suspend fun requireStorage(
    resourceManager: SolidResourceManager,
    ownerWebId: String,
    storage: String?,
): String = storage
    ?: StorageDiscovery.discover(resourceManager, ownerWebId)
    ?: error("Could not discover a storage for $ownerWebId")

/**
 * The file extension to give a stored binary of [contentType].
 *
 * One table for every module: a photo and a pass artifact are the same problem, and two tables
 * meant two answers for the same bytes. An unknown type keeps [fallback] — modules that name
 * their files after a known role pass `""`, those that cannot pass `.bin`.
 */
internal fun extensionForContentType(contentType: String, fallback: String = ".bin"): String =
    when (contentType.lowercase().substringBefore(';').trim()) {
        "application/vnd.apple.pkpass" -> ".pkpass"
        "application/zip" -> ".zip"
        "application/json" -> ".json"
        "application/pdf" -> ".pdf"
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "text/plain" -> ".txt"
        else -> fallback
    }

internal fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

/** The container a document lives in, i.e. everything up to and including its last slash. */
internal fun containerOf(documentUri: String): String =
    documentUri.substring(0, documentUri.lastIndexOf('/') + 1)
