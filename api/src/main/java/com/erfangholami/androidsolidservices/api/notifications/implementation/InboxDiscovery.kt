package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * An inbox found for the caller's own WebID, and the extended-profile document that advertises
 * it — `null` when the WebID document itself, or a `Link` header on it, carried the link.
 */
internal data class OwnInbox(val uri: String, val advertisedIn: String?)

internal class InboxDiscovery(private val rm: SolidResourceManager) {

    suspend fun resolveOwnInbox(webId: String): String? = resolveOwnInboxDetailed(webId)?.uri

    suspend fun resolveOwnInboxDetailed(webId: String): OwnInbox? {
        val profile = runCatching {
            rm.read(webId, webId, WebId::class.java).getOrThrow()
        }.getOrNull()
        profile?.getInbox()?.let { return OwnInbox(it, advertisedIn = null) }

        runCatching { rm.head(webId, webId) }.getOrNull()
            ?.let { inboxFromMetadata(it) }
            ?.let { return OwnInbox(it, advertisedIn = null) }

        profile?.let { p ->
            extendedProfileDocs(p).forEach { doc ->
                runCatching {
                    rm.read(webId, doc, WebId::class.java).getOrThrow().getInbox()
                }.getOrNull()?.let { return OwnInbox(it, advertisedIn = doc) }
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

    /**
     * The inbox this library provisions for every account, `{storage}inbox/`, derived from the
     * target's public `pim:storage`. This is a guess, not a discovery: a WebID whose document is
     * read-only (Inrupt's `id.inrupt.com`) can only advertise its inbox in an extended profile,
     * and until that profile is public nobody else can read the link. The caller must treat a
     * failed POST to the guess as "no inbox", never as a refusal by an advertised inbox.
     */
    suspend fun guessInboxOf(targetWebId: String): String? {
        val profile = runCatching {
            rm.readPublic(targetWebId, WebId::class.java).getOrThrow()
        }.getOrNull() ?: return null
        val storage = profile.getStorages().firstOrNull() ?: return null
        return (if (storage.endsWith("/")) storage else "$storage/") + "inbox/"
    }

    private fun extendedProfileDocs(profile: WebId): List<String> =
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct()

    private fun inboxFromMetadata(response: SolidResult<SolidMetadata>): String? =
        (response as? SolidResult.Success)?.value?.inboxUri
}
