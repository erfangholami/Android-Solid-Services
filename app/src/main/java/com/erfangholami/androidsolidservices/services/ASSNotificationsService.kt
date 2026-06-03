package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.domain.repository.NotificationsRepository
import com.erfangholami.androidsolidservices.services.dispatch.dispatchNetwork
import com.erfangholami.androidsolidservices.services.dispatch.dispatchUnit
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSShareNotificationListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSShareRequestListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class ASSNotificationsService : LifecycleService() {

    @Inject
    lateinit var notificationsRepository: NotificationsRepository

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
                notificationsRepository.listNotifications(webId)
            }
        }

        override fun listRequests(
            webId: String,
            callback: IASSShareRequestListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                notificationsRepository.listRequests(webId)
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
                notificationsRepository.sendOffer(
                    ownerWebId, receiverWebId, resourceUri, ShareMode.entries[mode],
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
                notificationsRepository.sendUndo(ownerWebId, receiverWebId, resourceUri)
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
                notificationsRepository.sendRequest(
                    requesterWebId, ownerWebId, resourceUri,
                    ShareMode.entries[requestedMode], summary,
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
                notificationsRepository.sendReject(ownerWebId, requesterWebId, resourceUri, reason)
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
                notificationsRepository.compactInbox(webId, olderThanIso)
            }
        }
    }
}
