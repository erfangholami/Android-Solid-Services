package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.services.dispatch.dispatchNetwork
import com.erfangholami.androidsolidservices.services.dispatch.dispatchUnit
import com.erfangholami.androidsolidservices.services.dispatch.requireShareMode
import com.erfangholami.androidsolidservices.shared.IASSBooleanCallback
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.IASSStringCallback
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSShareNotificationListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSShareRequestListCallback
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
            callback: IASSShareNotificationListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.listNotifications(webId)
            }
        }

        override fun listRequests(
            webId: String,
            callback: IASSShareRequestListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.listRequests(webId)
            }
        }

        override fun sendOffer(
            ownerWebId: String,
            receiverWebId: String,
            resourceUri: String,
            mode: Int,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.sendOffer(
                    ownerWebId, receiverWebId, resourceUri, requireShareMode(mode),
                )
            }
        }

        override fun sendUndo(
            ownerWebId: String,
            receiverWebId: String,
            resourceUri: String,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.sendUndo(ownerWebId, receiverWebId, resourceUri)
            }
        }

        override fun sendRequest(
            requesterWebId: String,
            ownerWebId: String,
            resourceUri: String,
            requestedMode: Int,
            summary: String?,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
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
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.sendReject(ownerWebId, requesterWebId, resourceUri, reason)
            }
        }

        override fun compactInbox(
            webId: String,
            olderThanIso: String?,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchNetwork(
                ioDispatcher,
                callback::onError,
                { callback.onResult() }) {
                notificationsManager.compactInbox(webId, olderThanIso)
            }
        }

        override fun ensureInbox(webId: String, callback: IASSStringCallback) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.ensureInbox(webId)
            }
        }

        override fun deleteNotification(
            webId: String,
            notificationUri: String,
            callback: IASSBooleanCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                notificationsManager.deleteNotification(webId, notificationUri)
            }
        }

        override fun sendUpdate(
            ownerWebId: String,
            receiverWebId: String,
            resourceUri: String,
            mode: Int,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, { callback.onResult() }) {
                notificationsManager.sendUpdate(
                    ownerWebId,
                    receiverWebId,
                    resourceUri,
                    requireShareMode(mode),
                )
            }
        }

        override fun sendAccept(
            ownerWebId: String,
            requesterWebId: String,
            resourceUri: String,
            mode: Int,
            requestUri: String?,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, { callback.onResult() }) {
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
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, { callback.onResult() }) {
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
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, { callback.onResult() }) {
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
