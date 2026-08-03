package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.IASSNotificationsService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope

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
            callback: IASSParcelableListCallback?,
        ) {
            record("listNotifications", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofList(listOf(Fixtures.SHARE_NOTIFICATION)))
        }

        override fun listRequests(webId: String?, callback: IASSParcelableListCallback?) {
            record("listRequests", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofList(listOf(Fixtures.SHARE_REQUEST)))
        }

        override fun sendOffer(
            ownerWebId: String?,
            receiverWebId: String?,
            resourceUri: String?,
            mode: Int,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "sendOffer",
                "ownerWebId" to ownerWebId,
                "receiverWebId" to receiverWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "resourceType" to resourceType,
                "resourceName" to resourceName,
            )
            callback.unit(ownerWebId)
        }

        override fun sendUndo(
            ownerWebId: String?,
            receiverWebId: String?,
            resourceUri: String?,
            callback: IASSParcelableCallback?,
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
            callback: IASSParcelableCallback?,
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
            callback: IASSParcelableCallback?,
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
            callback: IASSParcelableCallback?,
        ) {
            record("compactInbox", "webId" to webId, "olderThanIso" to olderThanIso)
            callback.unit(webId)
        }

        override fun ensureInbox(webId: String?, callback: IASSParcelableCallback?) {
            record("ensureInbox", "webId" to webId)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofString(Fixtures.INBOX))
        }

        override fun deleteNotification(
            webId: String?,
            notificationUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("deleteNotification", "webId" to webId, "notificationUri" to notificationUri)
            if (failing(webId)) callback?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.ofBoolean(true))
        }

        override fun sendUpdate(
            ownerWebId: String?,
            receiverWebId: String?,
            resourceUri: String?,
            mode: Int,
            resourceType: String?,
            resourceName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "sendUpdate",
                "ownerWebId" to ownerWebId,
                "receiverWebId" to receiverWebId,
                "resourceUri" to resourceUri,
                "mode" to mode,
                "resourceType" to resourceType,
                "resourceName" to resourceName,
            )
            callback.unit(ownerWebId)
        }

        override fun sendAccept(
            ownerWebId: String?,
            requesterWebId: String?,
            resourceUri: String?,
            mode: Int,
            requestUri: String?,
            callback: IASSParcelableCallback?,
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
            callback: IASSParcelableCallback?,
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
            callback: IASSParcelableCallback?,
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

    private fun IASSParcelableCallback?.unit(webId: String?) {
        if (failing(webId)) this?.onError(ERROR_CODE, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.empty())
    }

    companion object {
        const val ERROR_CODE: Int = ExceptionsErrorCode.NO_INBOX
    }
}
