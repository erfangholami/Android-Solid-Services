package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.services.dispatch.dispatchAcknowledged
import com.erfangholami.androidsolidservices.services.dispatch.dispatchParcelable
import com.erfangholami.androidsolidservices.services.dispatch.dispatchParcelableList
import com.erfangholami.androidsolidservices.services.dispatch.requireShareMode
import com.erfangholami.androidsolidservices.services.dispatch.requireShareReceiver
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSharingService
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class ASSSharingService : LifecycleService() {

    @Inject
    lateinit var sharingManager: SharingManager

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private val binder = object : IASSharingService.Stub() {

        override fun getStoredGivenShares(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.getStoredGivenShares(webId)
            }
        }

        override fun refreshGivenShares(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.refreshGivenShares(webId)
            }
        }

        override fun getGivenSharesForResource(
            webId: String,
            resourceUri: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.getGivenSharesForResource(webId, resourceUri)
            }
        }

        override fun createShare(
            webId: String,
            resourceUri: String,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            notifyReceiver: Boolean,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                sharingManager.createShare(
                    webId = webId,
                    resourceUri = resourceUri,
                    mode = requireShareMode(mode),
                    receiver = requireShareReceiver(receiverKind, receiverValue),
                    notifyReceiver = notifyReceiver,
                    resourceType = resourceType,
                    resourceName = resourceName,
                )
            }
        }

        override fun updateShare(
            webId: String,
            resourceUri: String,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                sharingManager.updateShare(
                    webId = webId,
                    resourceUri = resourceUri,
                    mode = requireShareMode(mode),
                    receiver = requireShareReceiver(receiverKind, receiverValue),
                    resourceType = resourceType,
                    resourceName = resourceName,
                )
            }
        }

        override fun revokeShare(
            webId: String,
            resourceUri: String,
            receiverKind: Int,
            receiverValue: String?,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.revokeShare(
                    webId = webId,
                    resourceUri = resourceUri,
                    receiver = requireShareReceiver(receiverKind, receiverValue),
                )
            }
        }

        override fun purgeGivenShares(
            webId: String,
            resourceUri: String,
            includeDescendants: Boolean,
            notifyReceivers: Boolean,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.purgeGivenShares(
                    webId = webId,
                    resourceUri = resourceUri,
                    includeDescendants = includeDescendants,
                    notifyReceivers = notifyReceivers,
                )
            }
        }

        override fun getStoredReceivedShares(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.getStoredReceivedShares(webId)
            }
        }

        override fun refreshReceivedShares(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.refreshReceivedShares(webId)
            }
        }

        override fun addReceivedShare(
            webId: String,
            resourceUri: String,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                sharingManager.addReceivedShare(
                    webId, resourceUri,
                    resourceType = resourceType,
                    resourceName = resourceName,
                )
            }
        }

        override fun removeReceivedShare(
            webId: String,
            resourceUri: String,
            ownerWebId: String,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.removeReceivedShare(webId, resourceUri, ownerWebId)
            }
        }

        override fun getAccessGrants(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.getAccessGrants(webId)
            }
        }

        override fun acceptShareRequest(
            webId: String,
            request: ShareRequest,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchParcelable(ioDispatcher, callback) {
                sharingManager.acceptShareRequest(webId, request)
            }
        }

        override fun rejectShareRequest(
            webId: String,
            request: ShareRequest,
            reason: String?,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.rejectShareRequest(webId, request, reason)
            }
        }

        override fun rebuildGivenIndex(
            webId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.rebuildGivenIndex(webId)
            }
        }

        override fun publishCatalogEntry(
            webId: String,
            entry: CatalogEntry,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.publishCatalogEntry(webId, entry)
            }
        }

        override fun removeCatalogEntry(
            webId: String,
            resourceUri: String,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.removeCatalogEntry(webId, resourceUri)
            }
        }

        override fun getOwnerCatalog(
            viewerWebId: String,
            ownerWebId: String,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.getOwnerCatalog(viewerWebId, ownerWebId)
            }
        }

        override fun makePrivate(
            webId: String,
            resourceUri: String,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.makePrivate(webId, resourceUri)
            }
        }

        override fun repairOwnerControl(
            webId: String,
            resourceUri: String,
            callback: IASSParcelableCallback,
        ) {
            lifecycleScope.dispatchAcknowledged(ioDispatcher, callback) {
                sharingManager.repairOwnerControl(webId, resourceUri)
            }
        }

        override fun syncReceivedShares(
            webId: String,
            notifications: MutableList<ShareNotification>?,
            callback: IASSParcelableListCallback,
        ) {
            lifecycleScope.dispatchParcelableList(ioDispatcher, callback) {
                sharingManager.syncReceivedShares(webId, notifications.orEmpty())
            }
        }
    }
}
