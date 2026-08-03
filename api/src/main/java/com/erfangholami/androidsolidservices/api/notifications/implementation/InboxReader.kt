package com.erfangholami.androidsolidservices.api.notifications.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.notifications.ShareNotificationProfile
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareNotificationRDF
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ShareRequestRDF
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.result.SolidResultException
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.AS
import java.net.URI

internal class InboxReader(
    private val rm: SolidResourceManager,
    private val discovery: InboxDiscovery,
    private val profile: ShareNotificationProfile,
) {

    private companion object {
        private const val INBOX_LOG_TAG = "InboxReader"
    }

    suspend fun listNotifications(webId: String): List<ShareNotification> {
        val items = listInboxItems(webId)
        val profileCache = HashMap<String, WebId?>()
        return items.mapNotNull { itemUri ->
            runCatching { parseAsNotification(webId, itemUri, profileCache) }
                .onFailure { t -> reportSkippedItem("listNotifications", itemUri, t) }
                .getOrNull()
        }
    }

    suspend fun listRequests(webId: String): List<ShareRequest> {
        val items = listInboxItems(webId)
        val profileCache = HashMap<String, WebId?>()
        return items.mapNotNull { itemUri ->
            runCatching { parseAsRequest(webId, itemUri, profileCache) }
                .onFailure { t -> reportSkippedItem("listRequests", itemUri, t) }
                .getOrNull()
        }
    }

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
        val inboxUri = discovery.resolveOwnInbox(webId) ?: return emptyList()
        val container = when (
            val r = rm.read(webId, inboxUri, SolidContainer::class.java)
        ) {
            is SolidResult.Success -> r.value
            is SolidResult.Failure -> when (r.error.code) {
                SolidErrorCode.UNAUTHORIZED ->
                    throw SharingException.InboxUnauthorized(inboxUri)

                SolidErrorCode.FORBIDDEN ->
                    throw SharingException.InboxForbidden(inboxUri)

                else -> return emptyList()
            }
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

    private fun reportSkippedItem(
        operation: String,
        itemUri: URI,
        failure: Throwable,
    ) {
        if (failure is kotlinx.coroutines.CancellationException) throw failure
        if (failure is SolidResultException) {
            Log.i(
                INBOX_LOG_TAG,
                "$operation: $itemUri is not a readable notification (${failure.message}); skipping.",
            )
            return
        }
        Log.w(INBOX_LOG_TAG, "$operation: parse failed for $itemUri; skipping.", failure)
    }

    private suspend fun parseAsNotification(
        webId: String,
        itemUri: URI,
        profileCache: MutableMap<String, WebId?>,
    ): ShareNotification? {
        val rdf = rm.read(webId, itemUri.toString(), ShareNotificationRDF::class.java).getOrThrow()
        val rawType = rdf.activityType()
        val actor = rdf.actor() ?: return null
        val obj = rdf.activityObject() ?: return null

        val isOwnDecision = (rawType == AS.ACCEPT || rawType == AS.REJECT) &&
                IriUtils.sameIri(actor, webId)
        val type = when {
            isOwnDecision && rawType == AS.ACCEPT -> ShareNotificationType.DECISION_GRANTED
            isOwnDecision && rawType == AS.REJECT -> ShareNotificationType.DECISION_REJECTED
            rawType == AS.OFFER -> ShareNotificationType.OFFER
            rawType == AS.UPDATE -> ShareNotificationType.UPDATED
            rawType == AS.ACCEPT -> ShareNotificationType.ACCEPTED
            rawType == AS.UNDO -> ShareNotificationType.UNDO
            rawType == AS.REJECT -> ShareNotificationType.REJECT
            else -> return null
        }
        val mode = ShareMode.strongest(rdf.aclModes())
            ?: rdf.mode(profile.vocabulary)?.let { name ->
                ShareMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }

        val skipOwnershipGate = isOwnDecision || type == ShareNotificationType.REJECT
        if (!skipOwnershipGate &&
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
            targetWebId = rdf.target(),
            resourceType = rdf.objectType(),
            resourceName = rdf.objectName(),
        )
    }

    private suspend fun parseAsRequest(
        webId: String,
        itemUri: URI,
        profileCache: MutableMap<String, WebId?>,
    ): ShareRequest? {
        val rdf = rm.read(webId, itemUri.toString(), ShareRequestRDF::class.java).getOrThrow()
        if (rdf.requestSubject() == null) return null
        val actor = rdf.actor() ?: return null
        val obj = rdf.activityObject() ?: return null
        val mode = (
                ShareMode.strongest(rdf.aclModes())
                    ?: rdf.requestedMode(profile.vocabulary)?.let { name ->
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
            (rm.head(readerWebId, resource.toString()) as? SolidResult.Success)?.value?.ownerUri
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

        return resourceUnderOwnedStorage(
            readerWebId,
            ownerWebId = claimedActor,
            resource = resource,
            profileCache = profileCache,
        )
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
            val storageUri = runCatching { URI.create(storage) }
                .onFailure { t ->
                    Log.w(INBOX_LOG_TAG, "resourceUnderOwnedStorage: malformed pim:storage '$storage'.", t)
                }
                .getOrNull() ?: return@any false
            val root = IriUtils.canonical(IriUtils.toContainerIri(storagePathRoot(storageUri)))
            canonicalResource.startsWith(root) &&
                    sameHost(resource.host, storageUri.host) &&
                    sameSite(ownerUri.host, storageUri.host)
        }
    }

    private fun sameHost(hostA: String?, hostB: String?): Boolean =
        !hostA.isNullOrBlank() && !hostB.isNullOrBlank() &&
                hostA.equals(hostB, ignoreCase = true)

    private suspend fun readProfileCached(
        viaWebId: String,
        webId: String,
        webIdUri: URI,
        cache: MutableMap<String, WebId?>,
    ): WebId? {
        val key = IriUtils.canonical(webId)
        if (cache.containsKey(key)) return cache[key]
        val profile =
            (rm.readPublic(webIdUri.toString(), WebId::class.java) as? SolidResult.Success)?.value
                ?: runCatching {
                    rm.read(viaWebId, webIdUri.toString(), WebId::class.java).getOrThrow()
                }.onFailure { t ->
                    Log.w(
                        INBOX_LOG_TAG,
                        "readProfileCached: could not read WebID profile $webId anonymously or " +
                                "authenticated; skipping storage check.",
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

    sealed class InboxItem {
        data class Notification(val value: ShareNotification) : InboxItem()
        data class Request(val value: ShareRequest) : InboxItem()
    }
}
