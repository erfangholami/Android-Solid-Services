package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.domain.repository.SharingRepository
import com.erfangholami.androidsolidservices.services.dispatch.dispatchNetwork
import com.erfangholami.androidsolidservices.services.dispatch.dispatchUnit
import com.erfangholami.androidsolidservices.shared.IASSharingService
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSAccessGrantListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSCatalogEntryListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSGivenShareCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSGivenShareListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSReceivedShareCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSReceivedShareListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class ASSSharingService : LifecycleService() {

    @Inject
    lateinit var sharingRepository: SharingRepository

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
            callback: IASSGivenShareListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.getStoredGivenShares(webId)
            }
        }

        override fun refreshGivenShares(
            webId: String,
            callback: IASSGivenShareListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.refreshGivenShares(webId)
            }
        }

        override fun getGivenSharesForResource(
            webId: String,
            resourceUri: String,
            callback: IASSGivenShareListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.getGivenSharesForResource(webId, resourceUri)
            }
        }

        override fun createShare(
            webId: String,
            resourceUri: String,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            notifyReceiver: Boolean,
            callback: IASSGivenShareCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.createShare(
                    webId = webId,
                    resourceUri = resourceUri,
                    mode = ShareMode.entries[mode],
                    receiver = ShareReceiver.fromKind(receiverKind, receiverValue),
                    notifyReceiver = notifyReceiver,
                )
            }
        }

        override fun updateShare(
            webId: String,
            resourceUri: String,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            callback: IASSGivenShareCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.updateShare(
                    webId = webId,
                    resourceUri = resourceUri,
                    mode = ShareMode.entries[mode],
                    receiver = ShareReceiver.fromKind(receiverKind, receiverValue),
                )
            }
        }

        override fun revokeShare(
            webId: String,
            resourceUri: String,
            receiverKind: Int,
            receiverValue: String?,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.revokeShare(
                    webId = webId,
                    resourceUri = resourceUri,
                    receiver = ShareReceiver.fromKind(receiverKind, receiverValue),
                )
            }
        }

        override fun getStoredReceivedShares(
            webId: String,
            callback: IASSReceivedShareListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.getStoredReceivedShares(webId)
            }
        }

        override fun refreshReceivedShares(
            webId: String,
            callback: IASSReceivedShareListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.refreshReceivedShares(webId)
            }
        }

        override fun addReceivedShare(
            webId: String,
            resourceUri: String,
            callback: IASSReceivedShareCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.addReceivedShare(webId, resourceUri)
            }
        }

        override fun removeReceivedShare(
            webId: String,
            resourceUri: String,
            ownerWebId: String,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.removeReceivedShare(webId, resourceUri, ownerWebId)
            }
        }

        override fun getAccessGrants(
            webId: String,
            callback: IASSAccessGrantListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.getAccessGrants(webId)
            }
        }

        override fun acceptShareRequest(
            webId: String,
            request: ShareRequest,
            callback: IASSGivenShareCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.acceptShareRequest(webId, request)
            }
        }

        override fun rejectShareRequest(
            webId: String,
            request: ShareRequest,
            reason: String?,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.rejectShareRequest(webId, request, reason)
            }
        }

        override fun rebuildGivenIndex(
            webId: String,
            callback: IASSGivenShareListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.rebuildGivenIndex(webId)
            }
        }

        override fun publishCatalogEntry(
            webId: String,
            entry: CatalogEntry,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.publishCatalogEntry(webId, entry)
            }
        }

        override fun removeCatalogEntry(
            webId: String,
            resourceUri: String,
            callback: IASSUnitCallback,
        ) {
            lifecycleScope.dispatchUnit(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.removeCatalogEntry(webId, resourceUri)
            }
        }

        override fun getOwnerCatalog(
            viewerWebId: String,
            ownerWebId: String,
            callback: IASSCatalogEntryListCallback,
        ) {
            lifecycleScope.dispatchNetwork(ioDispatcher, callback::onError, callback::onResult) {
                sharingRepository.getOwnerCatalog(viewerWebId, ownerWebId)
            }
        }
    }
}
