package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.access.AcpBackend
import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.access.pickBackend
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import java.net.URI

/**
 * Read/update conveniences for a user's WebID profile, layered over the core
 * [SolidResourceManager] verbs so callers don't hand-roll the profile read-merge or the
 * safe-replace FOAF patches. A profile spans the WebID document plus any extended-profile
 * documents it links (`foaf:isPrimaryTopicOf`, `rdfs:seeAlso`).
 */

/**
 * Reads [webId]'s complete public profile: the WebID document merged with every extended
 * profile document it links, so `foaf:name` / `foaf:img` etc. that live in a linked
 * document surface on the returned [WebId]. Read unauthenticated ([readPublic]) — WebID
 * profiles are world-readable and a foreign issuer's token is often rejected.
 */
public suspend fun SolidResourceManager.readProfile(webId: String): SolidResult<WebId> {
    val primary = when (val r = readPublic(webId, WebId::class.java)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val primaryDoc = webId.substringBefore('#')
    val extraDocs = (primary.getPrimaryTopicDocuments() + primary.getRelatedResources())
        .distinct()
        .filter { it.substringBefore('#') != primaryDoc }

    val merged: MutableList<RdfQuad> = primary.getAllQuads().toMutableList()
    extraDocs.forEach { doc ->
        readPublic(doc, WebId::class.java).getOrNull()?.let { merged += it.getAllQuads() }
    }
    return SolidResult.Success(WebId(webId, merged.distinct()))
}

/**
 * The document an edit to [webId]'s profile must be written to.
 *
 * Some identity providers serve the WebID document read-only — Inrupt's `id.inrupt.com` — and
 * the editable profile is then the extended document the WebID links through
 * `foaf:isPrimaryTopicOf` / `rdfs:seeAlso`, hosted on the user's own storage. The candidates
 * are the WebID document first, then the linked documents; the first whose `HEAD` reports
 * `WAC-Allow` write access wins. Without a usable `WAC-Allow`, a WebID document hosted on
 * another origin than every `pim:storage` counts as read-only and the first linked document
 * on a storage origin is chosen; failing that, the WebID document.
 */
public suspend fun SolidResourceManager.writableProfileDocument(webId: String): SolidResult<String> {
    val primary = when (val r = read(webId, webId, WebId::class.java)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val webIdDoc = webId.substringBefore('#')
    val linked = (primary.getPrimaryTopicDocuments() + primary.getRelatedResources())
        .map { it.substringBefore('#') }
        .distinct()
        .filter { it != webIdDoc }

    var sawWacAllow = false
    for (doc in listOf(webIdDoc) + linked) {
        val allow = (head(webId, doc) as? SolidResult.Success)?.value?.wacAllow ?: continue
        sawWacAllow = true
        if (allow.canWrite()) return SolidResult.Success(doc)
    }

    val storageOrigins = primary.getStorages().mapNotNull(::originOf).toSet()
    val onStorage = linked.firstOrNull { originOf(it) in storageOrigins }
    if (onStorage != null && (sawWacAllow || originOf(webIdDoc) !in storageOrigins)) {
        return SolidResult.Success(onStorage)
    }
    return SolidResult.Success(webIdDoc)
}

/**
 * Updates the single-valued FOAF text fields of [webId]'s profile. Only non-`null`
 * arguments are written; each is a safe replace (bind-and-delete the existing value via
 * an N3 `where`, then insert the new one) targeting the document
 * [writableProfileDocument] picks. Returns the re-read document.
 */
public suspend fun SolidResourceManager.updateProfile(
    webId: String,
    name: String? = null,
    givenName: String? = null,
    familyName: String? = null,
): SolidResult<WebId> {
    val docUri = when (val r = writableProfileDocument(webId)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val current = when (val r = read(webId, docUri, WebId::class.java)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val edits = listOf(
        Triple(FOAF.NAME, name, current.getName()),
        Triple(FOAF.GIVEN_NAME, givenName, current.getGivenName()),
        Triple(FOAF.FAMILY_NAME, familyName, current.getFamilyName()),
    ).filter { (_, newValue, _) -> newValue != null }
    if (edits.isEmpty()) return SolidResult.Success(current)

    val profilePatch = N3Patch.build {
        edits.forEachIndexed { index, (predicate, newValue, oldValue) ->
            if (oldValue != null) {
                where(webId, predicate, "old$index")
                deleteVar(webId, predicate, "old$index")
            }
            insertLiteral(webId, predicate, newValue!!)
        }
    }
    return when (val patched = patch(webId, docUri, profilePatch)) {
        is SolidResult.Failure -> SolidResult.Failure(patched.error)
        is SolidResult.Success -> read(webId, docUri, WebId::class.java)
    }
}

/**
 * Uploads [avatar] as a sibling of the writable profile document (see
 * [writableProfileDocument]) and points `foaf:img` at it, safe-replacing any existing avatar
 * link. On a pod whose profile container is not public the image is granted public read
 * (best-effort), so other people's clients can show it. Returns the re-read document.
 */
public suspend fun SolidResourceManager.setAvatar(
    webId: String,
    avatar: ByteArray,
    contentType: String,
): SolidResult<WebId> {
    val docUri = when (val r = writableProfileDocument(webId)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val container = docUri.substringBeforeLast('/') + "/"
    val avatarUri = "${container}avatar${avatarExtension(contentType)}"

    val put = putRaw(
        webId = webId,
        uri = avatarUri,
        contentType = contentType,
        body = avatar,
        linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
    )
    if (put is SolidResult.Failure) return SolidResult.Failure(put.error)
    runCatching { grantPublicReadIfPrivate(webId, avatarUri) }

    val current = when (val r = read(webId, docUri, WebId::class.java)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val profilePatch = N3Patch.build {
        if (current.getPhoto() != null) {
            where(webId, FOAF.IMG, "oldImg")
            deleteVar(webId, FOAF.IMG, "oldImg")
        }
        insert(webId, FOAF.IMG, avatarUri)
    }
    return when (val patched = patch(webId, docUri, profilePatch)) {
        is SolidResult.Failure -> SolidResult.Failure(patched.error)
        is SolidResult.Success -> read(webId, docUri, WebId::class.java)
    }
}

private suspend fun SolidResourceManager.grantPublicReadIfPrivate(webId: String, uri: String) {
    if (headPublic(uri) is SolidResult.Success) return
    val metadata = (head(webId, uri) as? SolidResult.Success)?.value ?: return
    pickBackend(metadata, uri, WacBackend(this), AcpBackend(this)).grant(
        webId = webId,
        resourceUri = uri,
        mode = ShareMode.READ,
        receiver = ShareReceiver.Public,
        isContainer = false,
        includeImpliedModes = false,
    )
}

private fun originOf(uri: String): String? {
    val parsed = runCatching { URI.create(uri) }.getOrNull() ?: return null
    val scheme = parsed.scheme ?: return null
    val authority = parsed.authority ?: return null
    return "${scheme.lowercase()}://${authority.lowercase()}"
}

private fun avatarExtension(contentType: String): String =
    when (contentType.lowercase().substringBefore(';').trim()) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        else -> ""
    }
