package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.IASSharingService
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSAccessGrantListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSCatalogEntryListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSGivenShareCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSGivenShareListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSReceivedShareCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSReceivedShareListCallback
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

        override fun getStoredGivenShares(webId: String?, callback: IASSGivenShareListCallback?) {
            record("getStoredGivenShares", "webId" to webId)
            callback.givenList(webId)
        }

        override fun refreshGivenShares(webId: String?, callback: IASSGivenShareListCallback?) {
            record("refreshGivenShares", "webId" to webId)
            callback.givenList(webId)
        }

        override fun getGivenSharesForResource(
            webId: String?,
            resourceUri: String?,
            callback: IASSGivenShareListCallback?,
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
            callback: IASSGivenShareCallback?,
        ) {
            record(
                "createShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "receiverKind" to receiverKind,
                "receiverValue" to receiverValue,
                "notifyReceiver" to notifyReceiver,
            )
            callback.given(webId)
        }

        override fun updateShare(
            webId: String?,
            resourceUri: String?,
            mode: Int,
            receiverKind: Int,
            receiverValue: String?,
            callback: IASSGivenShareCallback?,
        ) {
            record(
                "updateShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "receiverKind" to receiverKind,
                "receiverValue" to receiverValue,
            )
            callback.given(webId)
        }

        override fun revokeShare(
            webId: String?,
            resourceUri: String?,
            receiverKind: Int,
            receiverValue: String?,
            callback: IASSUnitCallback?,
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

        override fun getStoredReceivedShares(
            webId: String?,
            callback: IASSReceivedShareListCallback?,
        ) {
            record("getStoredReceivedShares", "webId" to webId)
            callback.receivedList(webId)
        }

        override fun refreshReceivedShares(
            webId: String?,
            callback: IASSReceivedShareListCallback?,
        ) {
            record("refreshReceivedShares", "webId" to webId)
            callback.receivedList(webId)
        }

        override fun addReceivedShare(
            webId: String?,
            resourceUri: String?,
            callback: IASSReceivedShareCallback?,
        ) {
            record("addReceivedShare", "webId" to webId, "resourceUri" to resourceUri)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(Fixtures.RECEIVED_SHARE)
        }

        override fun removeReceivedShare(
            webId: String?,
            resourceUri: String?,
            ownerWebId: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "removeReceivedShare",
                "webId" to webId,
                "resourceUri" to resourceUri,
                "ownerWebId" to ownerWebId,
            )
            callback.unit(webId)
        }

        override fun getAccessGrants(webId: String?, callback: IASSAccessGrantListCallback?) {
            record("getAccessGrants", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(mutableListOf(Fixtures.ACCESS_GRANT))
        }

        override fun acceptShareRequest(
            webId: String?,
            request: ShareRequest?,
            callback: IASSGivenShareCallback?,
        ) {
            record("acceptShareRequest", "webId" to webId, "request" to request)
            callback.given(webId)
        }

        override fun rejectShareRequest(
            webId: String?,
            request: ShareRequest?,
            reason: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "rejectShareRequest",
                "webId" to webId,
                "request" to request,
                "reason" to reason,
            )
            callback.unit(webId)
        }

        override fun rebuildGivenIndex(webId: String?, callback: IASSGivenShareListCallback?) {
            record("rebuildGivenIndex", "webId" to webId)
            callback.givenList(webId)
        }

        override fun publishCatalogEntry(
            webId: String?,
            entry: CatalogEntry?,
            callback: IASSUnitCallback?,
        ) {
            record("publishCatalogEntry", "webId" to webId, "entry" to entry)
            callback.unit(webId)
        }

        override fun removeCatalogEntry(
            webId: String?,
            resourceUri: String?,
            callback: IASSUnitCallback?,
        ) {
            record("removeCatalogEntry", "webId" to webId, "resourceUri" to resourceUri)
            callback.unit(webId)
        }

        override fun getOwnerCatalog(
            viewerWebId: String?,
            ownerWebId: String?,
            callback: IASSCatalogEntryListCallback?,
        ) {
            record("getOwnerCatalog", "viewerWebId" to viewerWebId, "ownerWebId" to ownerWebId)
            if (failing(viewerWebId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(mutableListOf(Fixtures.CATALOG_ENTRY))
        }

        override fun makePrivate(
            webId: String?,
            resourceUri: String?,
            callback: IASSUnitCallback?,
        ) {
            record("makePrivate", "webId" to webId, "resourceUri" to resourceUri)
            callback.unit(webId)
        }

        override fun repairOwnerControl(
            webId: String?,
            resourceUri: String?,
            callback: IASSUnitCallback?,
        ) {
            record("repairOwnerControl", "webId" to webId, "resourceUri" to resourceUri)
            callback.unit(webId)
        }

        override fun syncReceivedShares(
            webId: String?,
            notifications: MutableList<ShareNotification>?,
            callback: IASSReceivedShareListCallback?,
        ) {
            record("syncReceivedShares", "webId" to webId, "notifications" to notifications)
            callback.receivedList(webId)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun record(method: String, vararg args: Pair<String, Any?>) =
        CallLog.record(applicationContext, method, *args)

    private fun failing(webId: String?) = webId == Fixtures.FAILING_WEB_ID

    private fun IASSGivenShareListCallback?.givenList(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(mutableListOf(Fixtures.GIVEN_SHARE))
    }

    private fun IASSReceivedShareListCallback?.receivedList(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(mutableListOf(Fixtures.RECEIVED_SHARE))
    }

    private fun IASSGivenShareCallback?.given(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(Fixtures.GIVEN_SHARE)
    }

    private fun IASSUnitCallback?.unit(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE) else this?.onResult()
    }

    companion object {
        const val ERROR_CODE: Int = ExceptionsErrorCode.ACCESS_DENIED
    }
}
