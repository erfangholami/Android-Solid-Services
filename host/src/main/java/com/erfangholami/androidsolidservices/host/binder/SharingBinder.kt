package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.VerbAccess
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.dispatch.dispatchAcknowledged
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelable
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelableList
import com.erfangholami.androidsolidservices.host.dispatch.requireShareMode
import com.erfangholami.androidsolidservices.host.dispatch.requireShareReceiver
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSharingService
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The `IASSharingService` binder. Every verb needs [VerbAccess.SHARE]: on the resource it
 * names when it names one, otherwise on the whole pod, because the share indexes and the
 * received-shares list belong to the account, not to any one resource.
 */
public class SharingBinder(
    private val sharingManager: SharingManager,
    private val guard: AccessGuard,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IASSharingService.Stub() {

    private fun onResource(webId: String, resourceUri: String) =
        guard.gate(webId, VerbTarget.Resource(resourceUri), VerbAccess.SHARE)

    private fun onPod(webId: String) = guard.gate(webId, VerbTarget.Pod, VerbAccess.SHARE)

    override fun getStoredGivenShares(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
            sharingManager.getStoredGivenShares(webId)
        }
    }

    override fun refreshGivenShares(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
            sharingManager.refreshGivenShares(webId)
        }
    }

    override fun getGivenSharesForResource(webId: String, resourceUri: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onResource(webId, resourceUri)) {
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
        scope.dispatchParcelable(dispatcher, callback, onResource(webId, resourceUri)) {
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
        scope.dispatchParcelable(dispatcher, callback, onResource(webId, resourceUri)) {
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
        scope.dispatchAcknowledged(dispatcher, callback, onResource(webId, resourceUri)) {
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
        scope.dispatchParcelableList(dispatcher, callback, onResource(webId, resourceUri)) {
            sharingManager.purgeGivenShares(
                webId = webId,
                resourceUri = resourceUri,
                includeDescendants = includeDescendants,
                notifyReceivers = notifyReceivers,
            )
        }
    }

    override fun getStoredReceivedShares(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
            sharingManager.getStoredReceivedShares(webId)
        }
    }

    override fun refreshReceivedShares(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
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
        scope.dispatchParcelable(dispatcher, callback, onPod(webId)) {
            sharingManager.addReceivedShare(
                webId,
                resourceUri,
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
        scope.dispatchAcknowledged(dispatcher, callback, onPod(webId)) {
            sharingManager.removeReceivedShare(webId, resourceUri, ownerWebId)
        }
    }

    override fun getAccessGrants(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
            sharingManager.getAccessGrants(webId)
        }
    }

    override fun acceptShareRequest(webId: String, request: ShareRequest, callback: IASSParcelableCallback) {
        scope.dispatchParcelable(dispatcher, callback, onResource(webId, request.resourceUri)) {
            sharingManager.acceptShareRequest(webId, request)
        }
    }

    override fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String?,
        callback: IASSParcelableCallback,
    ) {
        scope.dispatchAcknowledged(dispatcher, callback, onResource(webId, request.resourceUri)) {
            sharingManager.rejectShareRequest(webId, request, reason)
        }
    }

    override fun rebuildGivenIndex(webId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
            sharingManager.rebuildGivenIndex(webId)
        }
    }

    override fun publishCatalogEntry(webId: String, entry: CatalogEntry, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, onResource(webId, entry.resourceUri)) {
            sharingManager.publishCatalogEntry(webId, entry)
        }
    }

    override fun removeCatalogEntry(webId: String, resourceUri: String, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, onResource(webId, resourceUri)) {
            sharingManager.removeCatalogEntry(webId, resourceUri)
        }
    }

    override fun getOwnerCatalog(viewerWebId: String, ownerWebId: String, callback: IASSParcelableListCallback) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(viewerWebId)) {
            sharingManager.getOwnerCatalog(viewerWebId, ownerWebId)
        }
    }

    override fun makePrivate(webId: String, resourceUri: String, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, onResource(webId, resourceUri)) {
            sharingManager.makePrivate(webId, resourceUri)
        }
    }

    override fun repairOwnerControl(webId: String, resourceUri: String, callback: IASSParcelableCallback) {
        scope.dispatchAcknowledged(dispatcher, callback, onResource(webId, resourceUri)) {
            sharingManager.repairOwnerControl(webId, resourceUri)
        }
    }

    override fun syncReceivedShares(
        webId: String,
        notifications: MutableList<ShareNotification>?,
        callback: IASSParcelableListCallback,
    ) {
        scope.dispatchParcelableList(dispatcher, callback, onPod(webId)) {
            sharingManager.syncReceivedShares(webId, notifications.orEmpty())
        }
    }
}
