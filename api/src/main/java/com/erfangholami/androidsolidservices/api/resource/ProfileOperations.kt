package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
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
    val primaryUri = URI.create(webId)
    val primary = when (val r = readPublic(primaryUri, WebId::class.java)) {
        is SolidResult.Success -> r.value
        is SolidResult.Failure -> return SolidResult.Failure(r.error)
    }
    val primaryDoc = webId.substringBefore('#')
    val extraDocs = (primary.getPrimaryTopicDocuments() + primary.getRelatedResources())
        .distinct()
        .filter { it.toString().substringBefore('#') != primaryDoc }

    val merged: MutableList<RdfQuad> = primary.getAllQuads().toMutableList()
    extraDocs.forEach { doc ->
        readPublic(doc, WebId::class.java).getOrNull()?.let { merged += it.getAllQuads() }
    }
    return SolidResult.Success(WebId(primaryUri, merged.distinct()))
}

/**
 * Updates the single-valued FOAF text fields of [webId]'s profile. Only non-`null`
 * arguments are written; each is a safe replace (bind-and-delete the existing value via
 * an N3 `where`, then insert the new one) targeting the WebID document. Returns the
 * re-read profile.
 */
public suspend fun SolidResourceManager.updateProfile(
    webId: String,
    name: String? = null,
    givenName: String? = null,
    familyName: String? = null,
): SolidResult<WebId> {
    val docUri = URI.create(webId.substringBefore('#'))
    val current = when (val r = read(webId, URI.create(webId), WebId::class.java)) {
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
        is SolidResult.Success -> read(webId, URI.create(webId), WebId::class.java)
    }
}

/**
 * Uploads [avatar] as a sibling of the WebID document and points `foaf:img` at it
 * (safe-replacing any existing avatar link). Returns the re-read profile.
 */
public suspend fun SolidResourceManager.setAvatar(
    webId: String,
    avatar: ByteArray,
    contentType: String,
): SolidResult<WebId> {
    val docUri = webId.substringBefore('#')
    val container = docUri.substringBeforeLast('/') + "/"
    val avatarUri = "${container}avatar${avatarExtension(contentType)}"

    val put = putRaw(
        webId = webId,
        uri = URI.create(avatarUri),
        contentType = contentType,
        body = avatar,
        linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
    )
    if (put is SolidResult.Failure) return SolidResult.Failure(put.error)

    val current = when (val r = read(webId, URI.create(webId), WebId::class.java)) {
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
    return when (val patched = patch(webId, URI.create(docUri), profilePatch)) {
        is SolidResult.Failure -> SolidResult.Failure(patched.error)
        is SolidResult.Success -> read(webId, URI.create(webId), WebId::class.java)
    }
}

private fun avatarExtension(contentType: String): String =
    when (contentType.lowercase().substringBefore(';').trim()) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        else -> ""
    }
