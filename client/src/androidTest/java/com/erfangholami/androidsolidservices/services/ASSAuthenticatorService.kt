package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLoginCallback
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLogoutCallback
import java.io.File

/**
 * Stands in for the ASS app's authenticator service.
 *
 * It carries the **production fully-qualified name** on purpose. `ServiceConnector` builds its
 * Intent from a fixed class name and only the package is redirectable, so a fake is reachable only
 * if it answers to the same FQCN — which is what lets the tests drive the SDK's own entry points
 * instead of a hand-built connector.
 *
 * Declared in the instrumentation manifest with `android:process`, so it is hosted in a **separate
 * process** from the tests. That is the point: binder short-circuits same-process calls and passes
 * object references straight through, which would exercise none of the Parcel marshalling this
 * suite exists to cover.
 *
 * Behaviour is driven by the WebID the test passes, so no shared mutable state is needed across
 * a process boundary.
 */
class ASSAuthenticatorService : Service() {

    private val binder = object : IASSAuthenticatorService.Stub() {

        override fun hasLoggedIn(): Boolean {
            CallLog.record(applicationContext, "hasLoggedIn")
            return true
        }

        override fun isAppAuthorized(webId: String?): Boolean {
            CallLog.record(applicationContext, "isAppAuthorized", "webId" to webId)
            return webId == AUTHORIZED_WEB_ID
        }

        override fun requestLogin(callback: IASSLoginCallback?) {
            CallLog.record(applicationContext, "requestLogin")
            callback?.onResult(true, AUTHORIZED_WEB_ID)
        }

        override fun disconnectFromSolid(
            webId: String?,
            callback: IASSLogoutCallback?,
        ) {
            CallLog.record(applicationContext, "disconnectFromSolid", "webId" to webId)
            when (webId) {
                AUTHORIZED_WEB_ID -> callback?.onResult(true)
                DOUBLE_ANSWER_WEB_ID -> {
                    callback?.onResult(true)
                    callback?.onResult(true)
                }

                HANG_ONCE_WEB_ID -> {
                    val marker = File(filesDir, HANG_MARKER)
                    if (!marker.createNewFile()) callback?.onResult(true)
                }

                else -> callback?.onError(ExceptionsErrorCode.SOLID_NOT_LOGGED_IN, "not signed in")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val AUTHORIZED_WEB_ID: String = "https://alice.pod.example/profile/card#me"

        /** No session for this one — calls with it take the error path. */
        const val UNKNOWN_WEB_ID: String = "https://mallory.pod.example/profile/card#me"

        /** Answers the callback twice; a misbehaving service must not crash the caller. */
        const val DOUBLE_ANSWER_WEB_ID: String = "https://double.pod.example/profile/card#me"

        /**
         * Parks the first call forever and answers every one after. The marker file survives a
         * process kill, which is how the death-recovery test gets a hang, a kill, and then a
         * successful retry out of one fake.
         */
        const val HANG_ONCE_WEB_ID: String = "https://hang-once.pod.example/profile/card#me"
        const val HANG_MARKER: String = "hang-once.marker"
    }
}
