package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.services.dispatch.dispatchAcknowledged
import com.erfangholami.androidsolidservices.services.dispatch.dispatchBoolean
import com.erfangholami.androidsolidservices.services.dispatch.dispatchParcelableList
import com.erfangholami.androidsolidservices.services.dispatch.dispatchString
import com.erfangholami.androidsolidservices.services.dispatch.requireShareMode
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class ASSNotificationsService : LifecycleService() {

    @Inject
    lateinit var notificationsManager: NotificationsManager

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private val binder = object : IASSNotificationsService.Stub() {

        override fun listNotifications(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                notificationsManager.listNotifications(webId)
            }
        }

        override fun listRequests(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                notificationsManager.sendReject(ownerWebId, requesterWebId, resourceUri, reason)
            }
        }

        override fun compactInbox(
            webId: String,
            olderThanIso: String?,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                notificationsManager.compactInbox(webId, olderThanIso)
            }
        }

        override fun ensureInbox(webId: String, callback: IASSParcelableCallback) {
            lifecycleScope.dispatchString(ioDispatcher, callback) {
                notificationsManager.ensureInbox(webId)
            }
        }

        override fun deleteNotification(
            webId: String,
            notificationUri: String,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchBoolean(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
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
}
