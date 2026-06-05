package com.erfangholami.androidsolidservices.api.notifications.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.implementation.ReceivedAccess
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerHelper
import com.erfangholami.androidsolidservices.api.sharing.implementation.nowIsoDateTime
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

internal class NotificationsManagerImplementation : NotificationsManager {

    companion object {
        private const val NOTIFS_LOG_TAG = "NotificationsManager"

        @Volatile
        private var INSTANCE: NotificationsManager? = null

        fun getInstance(authenticator: Authenticator): NotificationsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationsManagerImplementation(authenticator).also {
                    INSTANCE = it
                }
            }

        fun getInstance(resourceManager: SolidResourceManager): NotificationsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationsManagerImplementation(resourceManager).also {
                    INSTANCE = it
                }
            }
    }

    private val rm: SolidResourceManager
    private val helper: SharingManagerHelper
    internal val inboxReader: InboxReader
    internal val inboxNotifier: InboxNotifier

    private constructor(authenticator: Authenticator) {
        this.rm = SolidResourceManager.getInstance(authenticator)
        this.helper = SharingManagerHelper.getInstance(rm)
        this.inboxReader = InboxReader(rm)
        this.inboxNotifier = InboxNotifier(rm)
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.rm = resourceManager
        this.helper = SharingManagerHelper.getInstance(rm)
        this.inboxReader = InboxReader(rm)
        this.inboxNotifier = InboxNotifier(rm)
    }

    override suspend fun listNotifications(
        webId: String,
    ): SolidNetworkResponse<List<ShareNotification>> = wrap {
        val notifications = inboxReader.listNotifications(webId)
        syncReceivedSharesFor(webId, notifications)
        notifications
    }

    override suspend fun listRequests(
        webId: String,
    ): SolidNetworkResponse<List<ShareRequest>> = wrap {
        inboxReader.listRequests(webId)
    }

    override suspend fun compactInbox(
        webId: String,
        olderThanIso: String?,
    ): SolidNetworkResponse<Int> = wrap {
        val notifications = inboxReader.listNotifications(webId)
        val requests = inboxReader.listRequests(webId)
        val toDelete = mutableSetOf<URI>()

        val byOwnerResource = notifications.groupBy {
            it.ownerWebId to it.resourceUri
        }
        byOwnerResource.forEach { (_, items) ->
            val undos = items.filter { it.type == ShareNotificationType.UNDO }
            val offers = items.filter { it.type == ShareNotificationType.OFFER }
            if (undos.isNotEmpty() && offers.isNotEmpty()) {
                items.mapNotNull {
                    runCatching { URI.create(it.notificationUri) }
                        .onFailure { t ->
                            Log.w(
                                NOTIFS_LOG_TAG,
                                "compactInbox: malformed notification URI '${it.notificationUri}'; skipping.",
                                t,
                            )
                        }
                        .getOrNull()
                }.forEach { toDelete.add(it) }
            }
        }

        if (olderThanIso != null) {
            val cutoff = runCatching { java.time.Instant.parse(olderThanIso) }
                .onFailure { t ->
                    Log.w(
                        NOTIFS_LOG_TAG,
                        "compactInbox: olderThanIso='$olderThanIso' is not a valid Instant; skipping age cutoff.",
                        t,
                    )
                }
                .getOrNull()
            if (cutoff != null) {
                fun publishedBefore(p: String?): Boolean = p != null &&
                        runCatching { java.time.Instant.parse(p) }
                            .onFailure { t ->
                                Log.w(
                                    NOTIFS_LOG_TAG,
                                    "compactInbox: malformed publishedAt='$p'; treating as not-before-cutoff.",
                                    t,
                                )
                            }
                            .getOrNull()
                            ?.isBefore(cutoff) == true
                notifications.filter { publishedBefore(it.publishedAt) }.forEach {
                    runCatching { URI.create(it.notificationUri) }
                        .onFailure { t ->
                            Log.w(
                                NOTIFS_LOG_TAG,
                                "compactInbox: malformed notification URI '${it.notificationUri}'; skipping.",
                                t,
                            )
                        }
                        .getOrNull()
                        ?.let(toDelete::add)
                }
                requests.filter { publishedBefore(it.publishedAt) }.forEach {
                    runCatching { URI.create(it.requestUri) }
                        .onFailure { t ->
                            Log.w(
                                NOTIFS_LOG_TAG,
                                "compactInbox: malformed request URI '${it.requestUri}'; skipping.",
                                t,
                            )
                        }
                        .getOrNull()
                        ?.let(toDelete::add)
                }
            }
        }

        var deleted = 0
        toDelete.forEach { uri ->
            runCatching { rm.delete(webId, uri) }
                .onSuccess { r ->
                    when (r) {
                        is SolidNetworkResponse.Success -> if (r.data) deleted++
                        is SolidNetworkResponse.Error -> Log.w(
                            NOTIFS_LOG_TAG,
                            "compactInbox: delete failed for $uri (${r.errorCode}: ${r.errorMessage}).",
                        )

                        is SolidNetworkResponse.Exception -> Log.w(
                            NOTIFS_LOG_TAG,
                            "compactInbox: delete threw for $uri.",
                            r.exception,
                        )
                    }
                }
                .onFailure { t ->
                    Log.w(NOTIFS_LOG_TAG, "compactInbox: delete threw for $uri.", t)
                }
        }
        deleted
    }

    override suspend fun deleteNotification(
        webId: String,
        notificationUri: String,
    ): SolidNetworkResponse<Boolean> = wrap {
        when (val r = rm.delete(webId, encodeUriString(notificationUri))) {
            is SolidNetworkResponse.Success -> r.data
            is SolidNetworkResponse.Error ->
                throw SharingException.NotificationDelivery(notificationUri, r.errorCode)

            is SolidNetworkResponse.Exception -> throw r.exception
        }
    }

    override suspend fun ensureInbox(webId: String): SolidNetworkResponse<String> = wrap {
        inboxReader.resolveOwnInbox(webId)?.let { return@wrap it.toString() }

        val podRoot = helper.getPodRoot(webId)
        val inboxUri = URI.create("${podRoot}inbox/")
        helper.ensureInboxContainer(webId, inboxUri)
        helper.grantAccess(webId, inboxUri, ShareMode.APPEND, ShareReceiver.Public)
        advertiseInbox(webId, inboxUri)
        inboxUri.toString()
    }

    override suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postOffer(
            ownerWebId, receiverWebId, encodeUriString(resourceUri), mode,
        ).requireSuccess(receiverWebId)
    }

    override suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postUndo(
            ownerWebId, receiverWebId, encodeUriString(resourceUri),
        ).requireSuccess(receiverWebId)
    }

    override suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String?,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postRequest(
            requesterWebId, ownerWebId, encodeUriString(resourceUri), requestedMode, summary,
        ).requireSuccess(ownerWebId)
    }

    override suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String?,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postReject(
            ownerWebId, requesterWebId, encodeUriString(resourceUri), reason,
        ).requireSuccess(requesterWebId)
    }

    override suspend fun sendAccept(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode,
        requestUri: String?,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postAccept(
            ownerWebId, requesterWebId, encodeUriString(resourceUri), mode, requestUri,
        ).requireSuccess(requesterWebId)
    }

    override suspend fun recordDecisionGranted(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode,
        requestUri: String?,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postDecisionGranted(
            ownerWebId, requesterWebId, encodeUriString(resourceUri), mode, requestUri,
        ).requireSuccess(ownerWebId)
    }

    override suspend fun recordDecisionRejected(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode?,
        reason: String?,
    ): SolidNetworkResponse<Unit> = wrap {
        inboxNotifier.postDecisionRejected(
            ownerWebId, requesterWebId, encodeUriString(resourceUri), mode, reason,
        ).requireSuccess(ownerWebId)
    }

    private fun InboxPostResult.requireSuccess(targetWebId: String) {
        when (this) {
            is InboxPostResult.Success -> Unit
            is InboxPostResult.NoInbox -> throw SharingException.NoInbox(this.targetWebId)
            is InboxPostResult.Unauthorized ->
                throw SharingException.InboxUnauthorized(inboxUri.toString())

            is InboxPostResult.Forbidden ->
                throw SharingException.InboxForbidden(inboxUri.toString())

            is InboxPostResult.HttpError ->
                throw SharingException.NotificationDelivery(inboxUri.toString(), statusCode)

            is InboxPostResult.NetworkError ->
                throw SharingException.NotificationDelivery(inboxUri.toString(), statusCode = null)
        }
        @Suppress("UNUSED_VARIABLE") val unused = targetWebId
    }

    private suspend fun syncReceivedSharesFor(
        webId: String,
        notifications: List<ShareNotification>,
    ) {
        if (notifications.isEmpty()) return
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        notifications.forEach { n ->
            when (n.type) {
                ShareNotificationType.OFFER, ShareNotificationType.ACCEPTED -> runCatching {
                    val resourceUri = encodeUriString(n.resourceUri)
                    val access = helper.probeReceivedAccess(webId, resourceUri)
                    if (access !is ReceivedAccess.Granted) return@runCatching
                    val ownerWebId = access.owner ?: n.ownerWebId
                    helper.replaceReceivedShare(
                        webId, podRoot,
                        ReceivedShare(
                            ownerWebId = ownerWebId,
                            mode = access.mode,
                            resourceUri = resourceUri.toString(),
                            addedAt = n.publishedAt ?: nowIsoDateTime(),
                        ),
                    )
                }.onFailure { t ->
                    Log.w(
                        NOTIFS_LOG_TAG,
                        "syncReceivedSharesFor: OFFER sync failed for ${n.resourceUri}; " +
                                "received-index may be stale until next refreshReceivedShares.",
                        t,
                    )
                }

                ShareNotificationType.UNDO -> runCatching {
                    helper.removeReceivedShare(
                        webId, podRoot, encodeUriString(n.resourceUri).toString(), n.ownerWebId,
                    )
                }.onFailure { t ->
                    Log.w(
                        NOTIFS_LOG_TAG,
                        "syncReceivedSharesFor: UNDO sync failed for ${n.resourceUri}; " +
                                "received-index may still list this share.",
                        t,
                    )
                }

                ShareNotificationType.REJECT,
                ShareNotificationType.DECISION_GRANTED,
                ShareNotificationType.DECISION_REJECTED,
                    -> Unit
            }
        }
    }

    private suspend fun advertiseInbox(webId: String, inboxUri: URI) {
        val profile = rm.read(webId, URI.create(webId), WebId::class.java).getOrThrow()
        val targets = (
                profile.getPrimaryTopicDocuments() +
                        profile.getRelatedResources() +
                        URI.create(webId)
                ).distinct()
        for (doc in targets) {
            val advertised = runCatching { patchInboxInto(webId, doc, inboxUri) }
                .onFailure { t ->
                    Log.w(
                        NOTIFS_LOG_TAG,
                        "ensureInbox: could not advertise ldp:inbox in $doc; trying next profile document.",
                        t,
                    )
                }
                .getOrDefault(false)
            if (advertised) return
        }
        Log.w(
            NOTIFS_LOG_TAG,
            "ensureInbox: inbox created at $inboxUri but no writable profile document accepted " +
                    "the ldp:inbox triple. Other apps reading only the bare WebID won't discover it.",
        )
    }

    private suspend fun patchInboxInto(webId: String, doc: URI, inboxUri: URI): Boolean {
        val existing = rm.read(webId, doc, WebId::class.java).getOrThrow()
        if (existing.getInbox() != null) return true
        val patch = N3Patch.build { insert(webId, LDP.INBOX, inboxUri.toString()) }
        return rm.patch(webId, doc, patch, ifMatch = existing.getHeaders().getETag()) is
                SolidNetworkResponse.Success
    }

    private suspend fun <T> wrap(block: suspend () -> T): SolidNetworkResponse<T> =
        withContext(Dispatchers.IO) {
            try {
                SolidNetworkResponse.Success(block())
            } catch (e: Exception) {
                SolidNetworkResponse.Exception(e)
            }
        }
}
