package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.IASSBooleanCallback
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.IASSStringCallback
import com.erfangholami.androidsolidservices.shared.IASSUnitCallback
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSShareNotificationListCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSShareRequestListCallback

/**
 * Stands in for the ASS app's notifications service, hosted in `:fakeass`.
 *
 * It carries the **production fully-qualified name** on purpose: `ServiceConnector` builds its
 * Intent from a fixed class name and only the package is redirectable, so a fake is reachable
 * only if it answers to the same FQCN.
 *
 * Several methods here take two WebIDs in a row — owner and receiver, requester and owner — in an
 * order that differs between them. Swapping the pair compiles and runs; only the recorded arguments
 * catch it, which is what these fakes exist for.
 */
class ASSNotificationsService : Service() {

    private val binder = object : IASSNotificationsService.Stub() {

        override fun listNotifications(
            webId: String?,
            callback: IASSShareNotificationListCallback?,
        ) {
            record("listNotifications", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(mutableListOf(Fixtures.SHARE_NOTIFICATION))
        }

        override fun listRequests(webId: String?, callback: IASSShareRequestListCallback?) {
            record("listRequests", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(mutableListOf(Fixtures.SHARE_REQUEST))
        }

        override fun sendOffer(
            ownerWebId: String?,
            receiverWebId: String?,
            resourceUri: String?,
            mode: Int,
            callback: IASSUnitCallback?,
        ) {
            record(
                "sendOffer",
                "ownerWebId" to ownerWebId,
                "receiverWebId" to receiverWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
            )
            callback.unit(ownerWebId)
        }

        override fun sendUndo(
            ownerWebId: String?,
            receiverWebId: String?,
            resourceUri: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "sendUndo",
                "ownerWebId" to ownerWebId,
                "receiverWebId" to receiverWebId,
                "resourceUri" to resourceUri,
            )
            callback.unit(ownerWebId)
        }

        override fun sendRequest(
            requesterWebId: String?,
            ownerWebId: String?,
            resourceUri: String?,
            requestedMode: Int,
            summary: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "sendRequest",
                "requesterWebId" to requesterWebId,
                "ownerWebId" to ownerWebId,
                "resourceUri" to resourceUri,
                "requestedMode" to requestedMode,
                "summary" to summary,
            )
            callback.unit(requesterWebId)
        }

        override fun sendReject(
            ownerWebId: String?,
            requesterWebId: String?,
            resourceUri: String?,
            reason: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "sendReject",
                "ownerWebId" to ownerWebId,
                "requesterWebId" to requesterWebId,
                "resourceUri" to resourceUri,
                "reason" to reason,
            )
            callback.unit(ownerWebId)
        }

        override fun compactInbox(
            webId: String?,
            olderThanIso: String?,
            callback: IASSUnitCallback?,
        ) {
            record("compactInbox", "webId" to webId, "olderThanIso" to olderThanIso)
            callback.unit(webId)
        }

        override fun ensureInbox(webId: String?, callback: IASSStringCallback?) {
            record("ensureInbox", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(Fixtures.INBOX)
        }

        override fun deleteNotification(
            webId: String?,
            notificationUri: String?,
            callback: IASSBooleanCallback?,
        ) {
            record("deleteNotification", "webId" to webId, "notificationUri" to notificationUri)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(true)
        }

        override fun sendUpdate(
            ownerWebId: String?,
            receiverWebId: String?,
            resourceUri: String?,
            mode: Int,
            callback: IASSUnitCallback?,
        ) {
            record(
                "sendUpdate",
                "ownerWebId" to ownerWebId,
                "receiverWebId" to receiverWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
            )
            callback.unit(ownerWebId)
        }

        override fun sendAccept(
            ownerWebId: String?,
            requesterWebId: String?,
            resourceUri: String?,
            mode: Int,
            requestUri: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "sendAccept",
                "ownerWebId" to ownerWebId,
                "requesterWebId" to requesterWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "requestUri" to requestUri,
            )
            callback.unit(ownerWebId)
        }

        override fun recordDecisionGranted(
            ownerWebId: String?,
            requesterWebId: String?,
            resourceUri: String?,
            mode: Int,
            requestUri: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "recordDecisionGranted",
                "ownerWebId" to ownerWebId,
                "requesterWebId" to requesterWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "requestUri" to requestUri,
            )
            callback.unit(ownerWebId)
        }

        override fun recordDecisionRejected(
            ownerWebId: String?,
            requesterWebId: String?,
            resourceUri: String?,
            mode: Int,
            reason: String?,
            callback: IASSUnitCallback?,
        ) {
            record(
                "recordDecisionRejected",
                "ownerWebId" to ownerWebId,
                "requesterWebId" to requesterWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "reason" to reason,
            )
            callback.unit(ownerWebId)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun record(method: String, vararg args: Pair<String, Any?>) =
        CallLog.record(applicationContext, method, *args)

    private fun failing(webId: String?) = webId == Fixtures.FAILING_WEB_ID

    private fun IASSUnitCallback?.unit(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE) else this?.onResult()
    }

    companion object {
        const val ERROR_CODE: Int = ExceptionsErrorCode.NO_INBOX
    }
}
