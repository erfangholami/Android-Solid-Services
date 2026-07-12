package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import java.net.URI

internal class InboxDiscovery(private val rm: SolidResourceManager) {

    suspend fun resolveOwnInbox(webId: String): URI? {
        val profile = runCatching {
            rm.read(webId, URI.create(webId), WebId::class.java).getOrThrow()
        }.getOrNull()
        profile?.getInbox()?.let { return it }

        runCatching { rm.head(webId, URI.create(webId)) }.getOrNull()
            ?.let { inboxFromMetadata(it) }
            ?.let { return it }

        profile?.let { p ->
            extendedProfileDocs(p).forEach { doc ->
                runCatching {
                    rm.read(webId, doc, WebId::class.java).getOrThrow().getInbox()
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    suspend fun resolveInboxOf(targetWebId: String, asWebId: String): URI? {
        val profile = runCatching {
            rm.readPublic(URI.create(targetWebId), WebId::class.java).getOrThrow()
        }.getOrNull()
        profile?.getInbox()?.let { return it }

        runCatching { rm.headPublic(URI.create(targetWebId)) }.getOrNull()
            ?.let { inboxFromMetadata(it) }
            ?.let { return it }

        profile?.let { p ->
            extendedProfileDocs(p).forEach { doc ->
                runCatching {
                    rm.readPublic(doc, WebId::class.java).getOrThrow().getInbox()
                }.getOrNull()?.let { return it }
                runCatching {
                    rm.read(asWebId, doc, WebId::class.java).getOrThrow().getInbox()
                }.getOrNull()?.let { return it }
            }
        }

        // No `{storage}inbox/` fabrication: the inbox is discovered only where the target
        // actually advertises it (ldp:inbox triple or Link header, on the profile or an
        // extended-profile doc). Guessing a URL the target never declared would post
        // notifications into a container that may not exist or belong to the inbox — so,
        // like resolveOwnInbox, return null when nothing is advertised.
        return null
    }

    private fun extendedProfileDocs(profile: WebId): List<URI> =
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct()

    private fun inboxFromMetadata(response: SolidResult<SolidMetadata>): URI? =
        (response as? SolidResult.Success)?.value?.inboxUri
}
