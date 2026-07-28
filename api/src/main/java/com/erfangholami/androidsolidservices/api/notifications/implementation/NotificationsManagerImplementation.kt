package com.erfangholami.androidsolidservices.api.notifications.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.notifications.ShareNotificationProfile
import com.erfangholami.androidsolidservices.api.notifications.SolidShareNotificationProfile
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

internal class NotificationsManagerImplementation private constructor(
    private val rm: SolidResourceManager,
    profile: ShareNotificationProfile,
) : NotificationsManager {

    private val provisioner = InboxProvisioner(rm)
    private val discovery = InboxDiscovery(rm)
    private val transport = NotificationTransportImplementation.create(rm, discovery)
    internal val inboxReader = InboxReader(rm, discovery, profile)
    internal val inboxNotifier = InboxNotifier(transport, discovery, profile)

    companion object {
        private const val NOTIFS_LOG_TAG = "NotificationsManager"

        @Volatile
        private var INSTANCE: NotificationsManager? = null

        internal fun resetForTest() {
            INSTANCE = null
        }

        fun getInstance(
            authenticator: Authenticator,
            profile: ShareNotificationProfile = SolidShareNotificationProfile,
        ): NotificationsManager =
            getInstance(SolidResourceManager.getInstance(authenticator), profile)

        fun getInstance(
            resourceManager: SolidResourceManager,
            profile: ShareNotificationProfile = SolidShareNotificationProfile,
        ): NotificationsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationsManagerImplementation(resourceManager, profile).also {
                    INSTANCE = it
                }
            }
    }

    override suspend fun listNotifications(
        webId: String,
    ): SolidResult<List<ShareNotification>> = wrap {
        inboxReader.listNotifications(webId)
    }

    override suspend fun listRequests(
        webId: String,
    ): SolidResult<List<ShareRequest>> = wrap {
        inboxReader.listRequests(webId)
    }

    override suspend fun compactInbox(
        webId: String,
        olderThanIso: String?,
    ): SolidResult<Int> = wrap {
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
            runCatching { rm.delete(webId, uri.toString()) }
                .onSuccess { r ->
                    when (r) {
                        is SolidResult.Success -> if (r.value) deleted++
                        is SolidResult.Failure -> Log.w(
                            NOTIFS_LOG_TAG,
                            "compactInbox: delete failed for $uri (${r.error.code}: ${r.error.message}).",
                            r.error.cause,
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
    ): SolidResult<Boolean> = wrap {
        when (val r = rm.delete(webId, encodeUriString(notificationUri).toString())) {
            is SolidResult.Success -> r.value
            is SolidResult.Failure -> {
                val status = r.error.httpStatus
                if (status != null) {
                    throw SharingException.NotificationDelivery(notificationUri, status)
                }
                throw r.error.asException()
            }
        }
    }

    override suspend fun ensureInbox(webId: String): SolidResult<String> = wrap {
        discovery.resolveOwnInbox(webId)?.let { existingInbox ->
            ensurePublicAppend(webId, existingInbox)
            return@wrap existingInbox
        }

        val podRoot = provisioner.podRoot(webId)
        val inboxUri = "${podRoot}inbox/"
        provisioner.ensureContainer(webId, inboxUri)
        provisioner.grantPublicAppend(webId, inboxUri)
        advertiseInbox(webId, inboxUri)
        inboxUri
    }

    private suspend fun ensurePublicAppend(webId: String, inboxUri: String) {
        runCatching {
            provisioner.grantPublicAppend(webId, inboxUri)
        }.onFailure { t ->
            Log.w(
                NOTIFS_LOG_TAG,
                "ensureInbox: existing inbox $inboxUri could not be granted public acl:Append; " +
                        "other users may be unable to deliver share notifications to this account.",
                t,
            )
        }
    }

    override suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidResult<Unit> = wrap {
        inboxNotifier.postOffer(
            ownerWebId, receiverWebId, encodeUriString(resourceUri), mode,
        ).requireSuccess(receiverWebId)
    }

    override suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): SolidResult<Unit> = wrap {
        inboxNotifier.postUndo(
            ownerWebId, receiverWebId, encodeUriString(resourceUri),
        ).requireSuccess(receiverWebId)
    }

    override suspend fun sendUpdate(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidResult<Unit> = wrap {
        inboxNotifier.postUpdate(
            ownerWebId, receiverWebId, encodeUriString(resourceUri), mode,
        ).requireSuccess(receiverWebId)
    }

    override suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String?,
    ): SolidResult<Unit> = wrap {
        inboxNotifier.postRequest(
            requesterWebId, ownerWebId, encodeUriString(resourceUri), requestedMode, summary,
        ).requireSuccess(ownerWebId)
    }

    override suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String?,
    ): SolidResult<Unit> = wrap {
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
    ): SolidResult<Unit> = wrap {
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
    ): SolidResult<Unit> = wrap {
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
    ): SolidResult<Unit> = wrap {
        inboxNotifier.postDecisionRejected(
            ownerWebId, requesterWebId, encodeUriString(resourceUri), mode, reason,
        ).requireSuccess(ownerWebId)
    }

    private fun InboxPostResult.requireSuccess(targetWebId: String) {
        when (this) {
            is InboxPostResult.Success -> Unit
            is InboxPostResult.NoInbox -> throw SharingException.NoInbox(this.targetWebId)
            is InboxPostResult.Unauthorized ->
                throw SharingException.InboxUnauthorized(inboxUri)

            is InboxPostResult.Forbidden ->
                throw SharingException.InboxForbidden(inboxUri)

            is InboxPostResult.HttpError ->
                throw SharingException.NotificationDelivery(inboxUri, statusCode)

            is InboxPostResult.NetworkError ->
                throw SharingException.NotificationDelivery(inboxUri, statusCode = null)
        }
        @Suppress("UNUSED_VARIABLE") val unused = targetWebId
    }

    private suspend fun advertiseInbox(webId: String, inboxUri: String) {
        val profile = rm.read(webId, webId, WebId::class.java).getOrThrow()
        val targets = (
                profile.getPrimaryTopicDocuments() +
                        profile.getRelatedResources() +
                        webId
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

    private suspend fun patchInboxInto(webId: String, doc: String, inboxUri: String): Boolean {
        val existing = rm.read(webId, doc, WebId::class.java).getOrThrow()
        if (existing.getInbox() != null) return true
        val patch = N3Patch.build { insert(webId, LDP.INBOX, inboxUri) }
        return rm.patch(webId, doc, patch, ifMatch = existing.getHeaders().getETag()) is
                SolidResult.Success
    }

    private suspend fun <T> wrap(block: suspend () -> T): SolidResult<T> =
        withContext(Dispatchers.IO) {
            try {
                SolidResult.Success(block())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                SolidResult.Failure(SolidError.fromThrowable(e))
            }
        }
}
