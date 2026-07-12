package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import java.net.URI

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

    suspend fun discover(rm: SolidResourceManager, webId: String): URI? {
        profileStorage(rm, webId)?.let { return it }
        return walkUpForStorage(rm, webId)
    }

    private suspend fun profileStorage(rm: SolidResourceManager, webId: String): URI? {
        val profile = rm.read(webId, URI.create(webId), WebId::class.java).getOrNull() ?: return null
        profile.getStorages().firstOrNull()?.let { return it }
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct().forEach { doc ->
            rm.read(webId, doc, WebId::class.java).getOrNull()
                ?.getStorages()?.firstOrNull()?.let { return it }
        }
        return null
    }

    private suspend fun walkUpForStorage(rm: SolidResourceManager, webId: String): URI? {
        var current: URI? = documentOf(webId)
        var guard = 0
        while (current != null && guard++ < MAX_ANCESTOR_WALK) {
            val isStorage = rm.head(webId, current).getOrNull()?.isStorage == true
            if (isStorage) return ensureTrailingSlash(current)
            val parent = parentContainerOf(current)
            if (parent == current) break
            current = parent
        }
        return null
    }

    private fun documentOf(webId: String): URI = URI.create(webId.substringBefore('#'))

    private fun ensureTrailingSlash(uri: URI): URI {
        val s = uri.toString()
        return if (s.endsWith("/")) uri else URI.create("$s/")
    }

    private fun parentContainerOf(uri: URI): URI? {
        val str = uri.toString()
        val trimmed = if (str.endsWith("/")) str.dropLast(1) else str
        val schemeEnd = str.indexOf("://")
        val lastSlash = trimmed.lastIndexOf('/')
        if (schemeEnd < 0 || lastSlash <= schemeEnd + 2) return null
        return runCatching { URI.create(trimmed.substring(0, lastSlash + 1)) }.getOrNull()
    }
}
