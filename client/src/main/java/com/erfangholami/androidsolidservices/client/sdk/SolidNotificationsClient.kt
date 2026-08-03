package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_NOTIFICATIONS_SERVICE
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import kotlinx.coroutines.flow.Flow

/**
 * Client SDK for the sharing notification inbox, built on Linked Data
 * Notifications (LDN). Lets an app read incoming share notifications and
 * access requests from the user's inbox, and send the matching outgoing
 * notifications.
 *
 * This layer is pull-only: there is no push subscription. The expected usage:
 *  - call [listNotifications] / [listRequests] from a
 *    `PeriodicWorkRequest(15.minutes)` worker for background sync;
 *  - call them again on user-initiated refresh (pull-to-refresh, tab open).
 *
 * Calls are delegated over IPC to the Android Solid Services app. Obtain an
 * instance via [Solid.getNotificationsClient]; collect [connectionState] and wait
 * for `true` before issuing calls. All operations are `suspend` functions and
 * throw [SolidException] on failure.
 */
public class SolidNotificationsClient private constructor(context: Context) {

    public companion object {
        @Volatile
        private var instance: SolidNotificationsClient? = null

        public fun getInstance(context: Context): SolidNotificationsClient =
            instance ?: synchronized(this) {
                instance ?: SolidNotificationsClient(context).also { instance = it }
            }

        /**
         * Drops the singleton and releases its binding, so the next [getInstance] builds a fresh
         * client. Exists only for instrumented tests; nothing in production calls it.
         */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.connector?.unbind()
                instance = null
            }
        }
    }

    private val connector = ServiceConnector(
        context,
        ANDROID_SOLID_SERVICES_NOTIFICATIONS_SERVICE,
        IASSNotificationsService.Stub::asInterface,
    )

    /** Hot [Flow] of the IPC service connection state; emits `true` once connected. */
    public fun connectionState(): Flow<Boolean> = connector.connectionState

    /** Returns the share notifications currently in [webId]'s inbox (offers, accepts, withdrawals, rejections). */
    public suspend fun listNotifications(webId: String): List<ShareNotification> =
        connector.suspendParcelableList(ShareNotification::class.java) { service, cb ->
            service.listNotifications(webId, cb)
        }

    /** Returns the access requests in [webId]'s inbox — others asking for access to this user's resources. */
    public suspend fun listRequests(webId: String): List<ShareRequest> =
        connector.suspendParcelableList(ShareRequest::class.java) { service, cb ->
            service.listRequests(webId, cb)
        }

    /**
     * Notifies [receiverWebId] that [ownerWebId] has granted them [mode] access to [resourceUri].
     * [resourceType] / [resourceName] describe the object of a typed (entity) share — the
     * entity's RDF class IRI and human title; both `null` for plain resource shares.
     */
    public suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
        resourceType: String? = null,
        resourceName: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.sendOffer(
            ownerWebId, receiverWebId, resourceUri, mode.ordinal,
            resourceType, resourceName, cb,
        )
    }

    /** Notifies [receiverWebId] that their access to [resourceUri] has been withdrawn. */
    public suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): Unit = connector.suspendUnit { service, cb ->
        service.sendUndo(ownerWebId, receiverWebId, resourceUri, cb)
    }

    /** Asks [ownerWebId] for [requestedMode] access to [resourceUri] on behalf of [requesterWebId], with an optional [summary] message. */
    public suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.sendRequest(
            requesterWebId, ownerWebId, resourceUri,
            requestedMode.ordinal, summary, cb,
        )
    }

    /** Declines [requesterWebId]'s access request for [resourceUri], with an optional [reason]. */
    public suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.sendReject(ownerWebId, requesterWebId, resourceUri, reason, cb)
    }

    /**
     * Deletes Offer/Undo pairs that cancel out and (optionally) items
     * older than [olderThanIso]. The IPC layer doesn't propagate the
     * delete count back; callers needing it should drive the manager
     * in-process.
     */
    public suspend fun compactInbox(
        webId: String,
        olderThanIso: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.compactInbox(webId, olderThanIso, cb)
    }

    /**
     * Ensures the user has an LDN inbox, creating and advertising it if absent.
     * @return the inbox URI.
     */
    public suspend fun ensureInbox(webId: String): String =
        connector.suspendString { service, cb -> service.ensureInbox(webId, cb) }

    /** Deletes a single message from the inbox. */
    public suspend fun deleteNotification(webId: String, notificationUri: String): Boolean =
        connector.suspendBoolean { service, cb -> service.deleteNotification(webId, notificationUri, cb) }

    /**
     * Tells a receiver their access level changed. This is an `as:Update` — deliberately not a
     * re-Offer, so the receiver sees "X updated your access" rather than a fresh share.
     * [resourceType] / [resourceName] behave as on [sendOffer].
     */
    public suspend fun sendUpdate(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
        resourceType: String? = null,
        resourceName: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.sendUpdate(
            ownerWebId, receiverWebId, resourceUri, mode.ordinal,
            resourceType, resourceName, cb,
        )
    }

    /** Tells a requester that their access request was granted. */
    public suspend fun sendAccept(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode,
        requestUri: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.sendAccept(
            ownerWebId, requesterWebId, resourceUri, mode.ordinal, requestUri, cb,
        )
    }

    /**
     * Leaves a read-only record in the owner's OWN inbox that they granted a request — the
     * "you approved sharing with X" row they see in their own history.
     */
    public suspend fun recordDecisionGranted(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode,
        requestUri: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.recordDecisionGranted(
            ownerWebId, requesterWebId, resourceUri, mode.ordinal, requestUri, cb,
        )
    }

    /** Leaves a read-only record in the owner's OWN inbox that they declined a request. */
    public suspend fun recordDecisionRejected(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode? = null,
        reason: String? = null,
    ): Unit = connector.suspendUnit { service, cb ->
        service.recordDecisionRejected(
            ownerWebId, requesterWebId, resourceUri, mode?.ordinal ?: -1, reason, cb,
        )
    }
}
