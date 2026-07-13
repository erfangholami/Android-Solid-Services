package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId

/**
 * Discovers a user's Solid storage (pod) root.
 *
 * The Solid Protocol offers two discovery mechanisms; this tries both, most
 * authoritative first:
 *  1. the `pim:storage` triple in the WebID profile (and, failing that, in the
 *     linked extended-profile documents);
 *  2. walking up from the WebID document, issuing `HEAD` on each ancestor container
 *     and returning the first that advertises itself as a storage via
 *     `Link: rel="type" <http://www.w3.org/ns/pim/space#Storage>`.
 *
 * Returns `null` only when neither the profile nor the container hierarchy reveals a
 * storage. Callers that require one should surface a typed error rather than index
 * into an empty `WebId.getStorages()` (which throws) — this helper replaces that
 * crash-prone `getStorages()[0]` pattern.
 */
internal object StorageDiscovery {

    private const val MAX_ANCESTOR_WALK = 32

    suspend fun discover(rm: SolidResourceManager, webId: String): String? {
        profileStorage(rm, webId)?.let { return it }
        return walkUpForStorage(rm, webId)
    }

    private suspend fun profileStorage(rm: SolidResourceManager, webId: String): String? {
        val profile = rm.read(webId, webId, WebId::class.java).getOrNull() ?: return null
        profile.getStorages().firstOrNull()?.let { return it }
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct().forEach { doc ->
            rm.read(webId, doc, WebId::class.java).getOrNull()
                ?.getStorages()?.firstOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun walkUpForStorage(rm: SolidResourceManager, webId: String): String? {
        var current: String? = documentOf(webId)
        var guard = 0
        while (current != null && guard++ < MAX_ANCESTOR_WALK) {
            val uri = current
            val isStorage = rm.head(webId, uri).getOrNull()?.isStorage == true
            if (isStorage) return ensureTrailingSlash(uri)
            val parent = parentContainerOf(uri)
            if (parent == uri) break
            current = parent
        }
        return null
    }

    private fun documentOf(webId: String): String = webId.substringBefore('#')

    private fun ensureTrailingSlash(uri: String): String =
        if (uri.endsWith("/")) uri else "$uri/"

    private fun parentContainerOf(uri: String): String? {
        val trimmed = if (uri.endsWith("/")) uri.dropLast(1) else uri
        val schemeEnd = uri.indexOf("://")
        val lastSlash = trimmed.lastIndexOf('/')
        if (schemeEnd < 0 || lastSlash <= schemeEnd + 2) return null
        return trimmed.substring(0, lastSlash + 1)
    }
}
