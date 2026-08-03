package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSharingService
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

/**
 * Stands in for the ASS app's sharing service, hosted in `:fakeass`.
 *
 * It carries the **production fully-qualified name** on purpose: `ServiceConnector` builds its
 * Intent from a fixed class name and only the package is redirectable, so a fake is reachable
 * only if it answers to the same FQCN.
 *
 * The methods here carry the SDK's riskiest arguments: [ShareMode] and [ShareReceiver] are
 * flattened to bare integers by the client, so `mode`, `receiverKind` and `receiverValue` are
 * recorded verbatim for the tests to check against what was asked for.
 */
class ASSSharingService : Service() {

    private val binder = object : IASSharingService.Stub() {

        override fun getStoredGivenShares(webId: String?, callback: IASSParcelableListCallback?) {
            record("getStoredGivenShares", "webId" to webId)
            callback.givenList(webId)
        }

        override fun refreshGivenShares(webId: String?, callback: IASSParcelableListCallback?) {
            record("refreshGivenShares", "webId" to webId)
            callback.givenList(webId)
        }

        override fun getGivenSharesForResource(
            webId: String?,
            resourceUri: String?,
            callback: IASSParcelableListCallback?,
        ) {
            record("getGivenSharesForResource", "webId" to webId, "resourceUri" to resourceUri)
            callback.givenList(webId)
        }

        override fun createShare(
            webId: String?,
            resourceUri: String?,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            notifyReceiver: Boolean,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "receiverKind" to receiverKind,
                "receiverValue" to receiverValue,
                "notifyReceiver" to notifyReceiver,
                "resourceType" to resourceType,
                "resourceName" to resourceName,
            )
            callback.given(webId)
        }

        override fun updateShare(
            webId: String?,
            resourceUri: String?,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "updateShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "receiverKind" to receiverKind,
                "receiverValue" to receiverValue,
                "resourceType" to resourceType,
                "resourceName" to resourceName,
            )
            callback.given(webId)
        }

        override fun revokeShare(
            webId: String?,
            resourceUri: String?,
            receiverKind: Int,
            receiverValue: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "revokeShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "receiverKind" to receiverKind,
                "receiverValue" to receiverValue,
            )
            callback.unit(webId)
        }

        override fun purgeGivenShares(
            webId: String?,
            resourceUri: String?,
            includeDescendants: Boolean,
            notifyReceivers: Boolean,
            callback: IASSParcelableListCallback?,
        ) {
            record(
                "purgeGivenShares",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "includeDescendants" to includeDescendants,
                "notifyReceivers" to notifyReceivers,
            )
            callback.givenList(webId)
        }

        override fun getStoredReceivedShares(
            webId: String?,
            callback: IASSParcelableListCallback?,
        ) {
            record("getStoredReceivedShares", "webId" to webId)
            callback.receivedList(webId)
        }

        override fun refreshReceivedShares(
            webId: String?,
            callback: IASSParcelableListCallback?,
        ) {
            record("refreshReceivedShares", "webId" to webId)
            callback.receivedList(webId)
        }

        override fun addReceivedShare(
            webId: String?,
            resourceUri: String?,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "addReceivedShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "resourceType" to resourceType,
                "resourceName" to resourceName,
            )
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.RECEIVED_SHARE))
        }

        override fun removeReceivedShare(
            webId: String?,
            resourceUri: String?,
            ownerWebId: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "removeReceivedShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "ownerWebId" to ownerWebId,
            )
            callback.unit(webId)
        }

        override fun getAccessGrants(webId: String?, callback: IASSParcelableListCallback?) {
            record("getAccessGrants", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofList(listOf(Fixtures.ACCESS_GRANT)))
        }

        override fun acceptShareRequest(
            webId: String?,
            request: ShareRequest?,
            callback: IASSParcelableCallback?,
        ) {
            record("acceptShareRequest", "webId" to webId, "request" to request)
            callback.given(webId)
        }

        override fun rejectShareRequest(
            webId: String?,
            request: ShareRequest?,
            reason: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "rejectShareRequest",
                "webId" to webId,
                "request" to request,
                "reason" to reason,
            )
            callback.unit(webId)
        }

        override fun rebuildGivenIndex(webId: String?, callback: IASSParcelableListCallback?) {
            record("rebuildGivenIndex", "webId" to webId)
            callback.givenList(webId)
        }

        override fun publishCatalogEntry(
            webId: String?,
            entry: CatalogEntry?,
            callback: IASSParcelableCallback?,
        ) {
            record("publishCatalogEntry", "webId" to webId, "entry" to entry)
            callback.unit(webId)
        }

        override fun removeCatalogEntry(
            webId: String?,
            resourceUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("removeCatalogEntry", "webId" to webId, "resourceUri" to resourceUri)
            callback.unit(webId)
        }

        override fun getOwnerCatalog(
            viewerWebId: String?,
            ownerWebId: String?,
            callback: IASSParcelableListCallback?,
        ) {
            record("getOwnerCatalog", "viewerWebId" to viewerWebId, "ownerWebId" to ownerWebId)
            if (failing(viewerWebId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofList(listOf(Fixtures.CATALOG_ENTRY)))
        }

        override fun makePrivate(
            webId: String?,
            resourceUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("makePrivate", "webId" to webId, "resourceUri" to resourceUri)
            callback.unit(webId)
        }

        override fun repairOwnerControl(
            webId: String?,
            resourceUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("repairOwnerControl", "webId" to webId, "resourceUri" to resourceUri)
            callback.unit(webId)
        }

        override fun syncReceivedShares(
            webId: String?,
            notifications: MutableList<ShareNotification>?,
            callback: IASSParcelableListCallback?,
        ) {
            record("syncReceivedShares", "webId" to webId, "notifications" to notifications)
            callback.receivedList(webId)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun record(method: String, vararg args: Pair<String, Any?>) =
        CallLog.record(applicationContext, method, *args)

    private fun failing(webId: String?) = webId == Fixtures.FAILING_WEB_ID

    private fun IASSParcelableListCallback?.givenList(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.ofList(listOf(Fixtures.GIVEN_SHARE)))
    }

    private fun IASSParcelableListCallback?.receivedList(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.ofList(listOf(Fixtures.RECEIVED_SHARE)))
    }

    private fun IASSParcelableCallback?.given(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.GIVEN_SHARE))
    }

    private fun IASSParcelableCallback?.unit(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.empty())
    }

    companion object {
        const val ERROR_CODE: Int = ExceptionsErrorCode.ACCESS_DENIED
    }
}
