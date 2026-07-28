package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata

internal class InboxDiscovery(private val rm: SolidResourceManager) {

    suspend fun resolveOwnInbox(webId: String): String? {
        val profile = runCatching {
            rm.read(webId, webId, WebId::class.java).getOrThrow()
        }.getOrNull()
        profile?.getInbox()?.let { return it }

        runCatching { rm.head(webId, webId) }.getOrNull()
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

    suspend fun resolveInboxOf(targetWebId: String, asWebId: String): String? {
        val profile = runCatching {
            rm.readPublic(targetWebId, WebId::class.java).getOrThrow()
        }.getOrNull()
        profile?.getInbox()?.let { return it }

        runCatching { rm.headPublic(targetWebId) }.getOrNull()
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

        return null
    }

    private fun extendedProfileDocs(profile: WebId): List<String> =
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct()

    private fun inboxFromMetadata(response: SolidResult<SolidMetadata>): String? =
        (response as? SolidResult.Success)?.value?.inboxUri
}
