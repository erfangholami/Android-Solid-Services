package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.VerbAccess
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.dispatch.dispatchAcknowledged
import com.erfangholami.androidsolidservices.host.dispatch.dispatchBoolean
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelableList
import com.erfangholami.androidsolidservices.host.dispatch.dispatchString
import com.erfangholami.androidsolidservices.host.dispatch.requireShareMode
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The `IASSNotificationsService` binder. The inbox belongs to the account, so every verb is
 * checked on the whole pod: reads at [VerbAccess.INBOX_READ], deletions at
 * [VerbAccess.INBOX_WRITE], and anything that speaks to another inbox as the user, or records
 * a decision, at [VerbAccess.INBOX_SEND]. The acting account is the first WebID each verb takes.
 */
public class NotificationsBinder(
    private val notificationsManager: NotificationsManager,
    private val guard: AccessGuard,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IASSNotificationsService.Stub() {

    private fun inboxRead(webId: String) = guard.gate(webId, VerbTarget.Pod, VerbAccess.INBOX_READ)

    private fun inboxWrite(webId: String) = guard.gate(webId, VerbTarget.Pod, VerbAccess.INBOX_WRITE)

    private fun inboxSend(webId: String) = guard.gate(webId, VerbTarget.Pod, VerbAccess.INBOX_SEND)

    override fun listNotifications(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, inboxRead(webId)) {
            notificationsManager.listNotifications(webId)
        }
    }

    override fun listRequests(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, inboxRead(webId)) {
            notificationsManager.listRequests(webId)
        }
    }

    override fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: Int,
        resourceType: String?,
        resourceName: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.sendOffer(
                ownerWebId, receiverWebId, resourceUri, requireShareMode(mode),
                resourceType = resourceType, resourceName = resourceName,
            )
        }
    }

    override fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.sendUndo(ownerWebId, receiverWebId, resourceUri)
        }
    }

    override fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: Int,
        summary: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(requesterWebId)) {
            notificationsManager.sendRequest(
                requesterWebId, ownerWebId, resourceUri,
                requireShareMode(requestedMode), summary,
            )
        }
    }

    override fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.sendReject(ownerWebId, requesterWebId, resourceUri, reason)
        }
    }

    override fun compactInbox(webId: String, olderThanIso: String?, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxWrite(webId)) {
            notificationsManager.compactInbox(webId, olderThanIso)
        }
    }

    override fun ensureInbox(webId: String, callback: IASSParcelableCallback) {
        scope.dispatchString(dispatcher, callback, inboxRead(webId)) {
            notificationsManager.ensureInbox(webId)
        }
    }

    override fun deleteNotification(webId: String, notificationUri: String, callback: IASSParcelableCallback) {
        scope.dispatchBoolean(dispatcher, callback, inboxWrite(webId)) {
            notificationsManager.deleteNotification(webId, notificationUri)
        }
    }

    override fun sendUpdate(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: Int,
        resourceType: String?,
        resourceName: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.sendUpdate(
                ownerWebId,
                receiverWebId,
                resourceUri,
                requireShareMode(mode),
                resourceType = resourceType,
                resourceName = resourceName,
            )
        }
    }

    override fun sendAccept(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: Int,
        requestUri: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.sendAccept(
                ownerWebId,
                requesterWebId,
                resourceUri,
                requireShareMode(mode),
                requestUri,
            )
        }
    }

    override fun recordDecisionGranted(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: Int,
        requestUri: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.recordDecisionGranted(
                ownerWebId,
                requesterWebId,
                resourceUri,
                requireShareMode(mode),
                requestUri,
            )
        }
    }

    override fun recordDecisionRejected(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: Int,
        reason: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, inboxSend(ownerWebId)) {
            notificationsManager.recordDecisionRejected(
                ownerWebId,
                requesterWebId,
                resourceUri,
                mode.takeIf { it >= 0 }?.let { requireShareMode(it) },
                reason,
            )
        }
    }
}
