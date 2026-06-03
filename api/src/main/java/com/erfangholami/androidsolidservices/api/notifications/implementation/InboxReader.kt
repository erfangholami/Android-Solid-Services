package com.erfangholami.androidsolidservices.api.notifications.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareNotificationRDF
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareRequestRDF
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.AS
import java.net.URI

/**
 * Reads the current user's LDN inbox and decodes items into the two
 * supported event shapes:
 *
 * - `as:Offer` / `as:Accept` / `as:Undo` / `as:Reject` → [ShareNotification]
 * - `interop:AccessRequest` (or legacy `solidshare:AccessRequest`) → [ShareRequest]
 *
 * The inbox is discovered via `ldp:inbox` on the user's WebID profile, falling
 * back to a HEAD on the WebID URL. Container-level 401/403 errors are surfaced
 * as typed [SharingException] variants; per-item parse errors are skipped
 * silently (the inbox can contain arbitrary RDF).
 *
 * Items whose `as:actor` doesn't match the actual owner of the referenced
 * resource are dropped — see [actorMatchesOwner] for the verification strategy.
 *
 * Spec anchors:
 * - LDN: https://www.w3.org/TR/ldn/
 * - Activity Streams 2: https://www.w3.org/TR/activitystreams-core/
 * - Solid Notifications: https://solidproject.org/TR/notifications-protocol
 */
internal class InboxReader(private val rm: SolidResourceManager) {

    private companion object {
        private const val INBOX_LOG_TAG = "InboxReader"
    }

    suspend fun resolveOwnInbox(webId: String): URI? {
        val profile = runCatching {
            rm.read(webId, URI.create(webId), WebId::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                INBOX_LOG_TAG,
                "resolveOwnInbox: profile read failed for $webId; trying HEAD-link fallback.",
                t,
            )
        }.getOrNull()
        profile?.getInbox()?.let { return it }

        runCatching {
            when (val r = rm.head(webId, URI.create(webId))) {
                is SolidNetworkResponse.Success -> r.data.inboxUri
                else -> null
            }
        }.onFailure { t ->
            Log.w(
                INBOX_LOG_TAG,
                "resolveOwnInbox: HEAD fallback failed for $webId; trying extended profile.",
                t,
            )
        }.getOrNull()?.let { return it }

        profile?.let { p ->
            extendedProfileDocs(p).forEach { doc ->
                runCatching {
                    rm.read(webId, doc, WebId::class.java).getOrThrow().getInbox()
                }.onFailure { t ->
                    Log.w(
                        INBOX_LOG_TAG,
                        "resolveOwnInbox: extended profile $doc unreadable; skipping.",
                        t
                    )
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun extendedProfileDocs(profile: WebId): List<URI> =
        (profile.getPrimaryTopicDocuments() + profile.getRelatedResources()).distinct()

    suspend fun listNotifications(webId: String): List<ShareNotification> {
        val items = listInboxItems(webId)
        val profileCache = HashMap<String, WebId?>()
        return items.mapNotNull { itemUri ->
            runCatching { parseAsNotification(webId, itemUri, profileCache) }
                .onFailure { t ->
                    Log.w(
                        INBOX_LOG_TAG,
                        "listNotifications: parse failed for $itemUri; skipping.",
                        t,
                    )
                }
                .getOrNull()
        }
    }

    suspend fun listRequests(webId: String): List<ShareRequest> {
        val items = listInboxItems(webId)
        val profileCache = HashMap<String, WebId?>()
        return items.mapNotNull { itemUri ->
            runCatching { parseAsRequest(webId, itemUri, profileCache) }
                .onFailure { t ->
                    Log.w(
                        INBOX_LOG_TAG,
                        "listRequests: parse failed for $itemUri; skipping.",
                        t,
                    )
                }
                .getOrNull()
        }
    }

    /**
     * Parses [itemUri] as either a [ShareNotification] or a [ShareRequest].
     * Returns a typed result so a push channel (when added) can route to
     * the right listener method.
     */
    suspend fun parseInboxItem(webId: String, itemUri: URI): InboxItem? {
        val profileCache = HashMap<String, WebId?>()
        val asNotification = runCatching { parseAsNotification(webId, itemUri, profileCache) }
            .onFailure { t ->
                Log.w(
                    INBOX_LOG_TAG,
                    "parseInboxItem: notification parse failed for $itemUri; trying request shape.",
                    t,
                )
            }
            .getOrNull()
        if (asNotification != null) return InboxItem.Notification(asNotification)
        val asRequest = runCatching { parseAsRequest(webId, itemUri, profileCache) }
            .onFailure { t ->
                Log.w(
                    INBOX_LOG_TAG,
                    "parseInboxItem: request parse failed for $itemUri; returning null.",
                    t,
                )
            }
            .getOrNull()
        if (asRequest != null) return InboxItem.Request(asRequest)
        return null
    }

    private suspend fun listInboxItems(webId: String): List<URI> {
        val inboxUri = resolveOwnInbox(webId) ?: return emptyList()
        val container = when (
            val r = rm.read(webId, inboxUri, SolidContainer::class.java)
        ) {
            is SolidNetworkResponse.Success -> r.data
            is SolidNetworkResponse.Error -> when (r.errorCode) {
                401 -> throw SharingException.InboxUnauthorized(inboxUri.toString())
                403 -> throw SharingException.InboxForbidden(inboxUri.toString())
                else -> return emptyList()
            }

            is SolidNetworkResponse.Exception -> return emptyList()
        }
        return container.getContained().mapNotNull { ref ->
            runCatching { URI.create(ref.identifier) }
                .onFailure { t ->
                    Log.w(
                        INBOX_LOG_TAG,
                        "listInboxItems: malformed item URI '${ref.identifier}'; skipping.",
                        t,
                    )
                }
                .getOrNull()
        }
    }

    private suspend fun parseAsNotification(
        webId: String,
        itemUri: URI,
        profileCache: MutableMap<String, WebId?>,
    ): ShareNotification? {
        val rdf = rm.read(webId, itemUri, ShareNotificationRDF::class.java).getOrThrow()
        val type = when (rdf.activityType()) {
            AS.OFFER -> ShareNotificationType.OFFER
            AS.ACCEPT -> ShareNotificationType.ACCEPTED
            AS.UNDO -> ShareNotificationType.UNDO
            AS.REJECT -> ShareNotificationType.REJECT
            else -> return null
        }
        val actor = rdf.actor() ?: return null
        val obj = rdf.activityObject() ?: return null
        val mode = ShareMode.strongest(rdf.aclModes())
            ?: rdf.mode()?.let { name ->
                ShareMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }

        if (type != ShareNotificationType.REJECT &&
            !actorMatchesOwner(
                webId,
                claimedActor = actor,
                resourceUri = obj,
                profileCache = profileCache
            )
        ) {
            return null
        }

        return ShareNotification(
            notificationUri = itemUri.toString(),
            type = type,
            ownerWebId = actor,
            resourceUri = obj,
            mode = mode,
            summary = rdf.summary(),
            publishedAt = rdf.published(),
        )
    }

    private suspend fun parseAsRequest(
        webId: String,
        itemUri: URI,
        profileCache: MutableMap<String, WebId?>,
    ): ShareRequest? {
        val rdf = rm.read(webId, itemUri, ShareRequestRDF::class.java).getOrThrow()
        if (rdf.requestSubject() == null) return null
        val actor = rdf.actor() ?: return null
        val obj = rdf.activityObject() ?: return null
        val mode = (
                ShareMode.strongest(rdf.aclModes())
                    ?: rdf.requestedMode()?.let { name ->
                        ShareMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    }
                ) ?: return null

        if (!resourceBelongsToReader(
                webId,
                resourceUri = obj,
                profileCache = profileCache
            )
        ) return null

        return ShareRequest(
            requestUri = itemUri.toString(),
            requesterWebId = actor,
            resourceUri = obj,
            requestedMode = mode,
            summary = rdf.summary(),
            publishedAt = rdf.published(),
        )
    }

    private suspend fun actorMatchesOwner(
        readerWebId: String,
        claimedActor: String,
        resourceUri: String,
        profileCache: MutableMap<String, WebId?>,
    ): Boolean {
        val resource = runCatching { encodeUriString(resourceUri) }
            .onFailure { t ->
                Log.w(INBOX_LOG_TAG, "actorMatchesOwner: malformed resource '$resourceUri'.", t)
            }
            .getOrNull() ?: return false

        val ownerFromHeaders = runCatching {
            (rm.head(readerWebId, resource) as? SolidNetworkResponse.Success)?.data?.ownerUri
        }.onFailure { t ->
            Log.w(
                INBOX_LOG_TAG,
                "actorMatchesOwner: HEAD failed for $resource; trying declared-storage check.",
                t,
            )
        }.getOrNull()
        if (ownerFromHeaders != null) {
            return IriUtils.sameIri(ownerFromHeaders.toString(), claimedActor)
        }

        if (resourceUnderOwnedStorage(
                readerWebId,
                ownerWebId = claimedActor,
                resource = resource,
                profileCache = profileCache,
            )
        ) {
            return true
        }

        val actorUri = runCatching { URI.create(claimedActor) }
            .onFailure { t ->
                Log.w(INBOX_LOG_TAG, "actorMatchesOwner: malformed actor '$claimedActor'.", t)
            }
            .getOrNull() ?: return false
        return resource.host != null && resource.host == actorUri.host
    }

    private suspend fun resourceBelongsToReader(
        readerWebId: String,
        resourceUri: String,
        profileCache: MutableMap<String, WebId?>,
    ): Boolean {
        val resource = runCatching { encodeUriString(resourceUri) }
            .onFailure { t ->
                Log.w(
                    INBOX_LOG_TAG,
                    "resourceBelongsToReader: malformed resource '$resourceUri'.",
                    t
                )
            }
            .getOrNull() ?: return false

        if (resourceUnderOwnedStorage(
                readerWebId,
                ownerWebId = readerWebId,
                resource = resource,
                profileCache = profileCache,
            )
        ) {
            return true
        }

        val reader = runCatching { URI.create(readerWebId) }
            .onFailure { t ->
                Log.w(INBOX_LOG_TAG, "resourceBelongsToReader: malformed webId '$readerWebId'.", t)
            }
            .getOrNull() ?: return false
        return resource.host != null && resource.host == reader.host
    }

    private suspend fun resourceUnderOwnedStorage(
        viaWebId: String,
        ownerWebId: String,
        resource: URI,
        profileCache: MutableMap<String, WebId?>,
    ): Boolean {
        val ownerUri = runCatching { URI.create(ownerWebId) }
            .onFailure { t ->
                Log.w(INBOX_LOG_TAG, "resourceUnderOwnedStorage: malformed WebID '$ownerWebId'.", t)
            }
            .getOrNull() ?: return false

        val profile =
            readProfileCached(viaWebId, ownerWebId, ownerUri, profileCache) ?: return false

        val canonicalResource = IriUtils.canonical(resource.toString())
        return profile.getStorages().any { storage ->
            val root = IriUtils.canonical(IriUtils.toContainerIri(storagePathRoot(storage)))
            canonicalResource.startsWith(root) && sameSite(ownerUri.host, storage.host)
        }
    }

    private suspend fun readProfileCached(
        viaWebId: String,
        webId: String,
        webIdUri: URI,
        cache: MutableMap<String, WebId?>,
    ): WebId? {
        val key = IriUtils.canonical(webId)
        if (cache.containsKey(key)) return cache[key]
        val profile = runCatching {
            rm.read(viaWebId, webIdUri, WebId::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                INBOX_LOG_TAG,
                "readProfileCached: could not read WebID profile $webId; skipping storage check.",
                t,
            )
        }.getOrNull()
        cache[key] = profile
        return profile
    }

    private fun storagePathRoot(storage: URI): String {
        val scheme = storage.scheme
        val authority = storage.authority
        if (scheme == null || authority == null) return storage.toString()
        return "$scheme://$authority${storage.path ?: ""}"
    }

    private fun sameSite(hostA: String?, hostB: String?): Boolean {
        if (hostA.isNullOrBlank() || hostB.isNullOrBlank()) return false
        val a = hostA.lowercase()
        val b = hostB.lowercase()
        return a == b || registrableSuffix(a) == registrableSuffix(b)
    }

    private fun registrableSuffix(host: String): String {
        val labels = host.split('.').filter { it.isNotEmpty() }
        return if (labels.size <= 2) labels.joinToString(".") else labels.takeLast(2)
            .joinToString(".")
    }

    /** Typed wrapper around an inbox item that has been parsed once. */
    sealed class InboxItem {
        data class Notification(val value: ShareNotification) : InboxItem()
        data class Request(val value: ShareRequest) : InboxItem()
    }
}
