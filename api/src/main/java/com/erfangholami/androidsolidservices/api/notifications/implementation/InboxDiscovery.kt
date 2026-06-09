package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
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

        profile?.getStorages()?.firstOrNull()?.let { storage ->
            val root = storage.toString().let { if (it.endsWith("/")) it else "$it/" }
            return URI.create("${root}inbox/")
        }
        return null
    }

    private fun extendedProfileDocs(profile: WebId): List<URI> =
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct()

    private fun inboxFromMetadata(response: SolidNetworkResponse<SolidMetadata>): URI? =
        (response as? SolidNetworkResponse.Success)?.data?.inboxUri
}
