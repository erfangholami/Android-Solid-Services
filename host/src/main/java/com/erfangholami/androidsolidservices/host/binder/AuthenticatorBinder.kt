package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.host.HostSession
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.dispatch.deliverError
import com.erfangholami.androidsolidservices.host.dispatch.dispatchBoolean
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelable
import com.erfangholami.androidsolidservices.host.grant.AppGrantStore
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The `IASSAuthenticatorService` binder: sign-in state, the calling app's own grant, and
 * revocation of that grant by the app itself.
 *
 * The two synchronous verbs block the binder thread for one local read each: `hasLoggedIn`
 * waits for the account store to load, bounded by [readyTimeoutMillis]; `isAppAuthorized` reads
 * the grant store. Neither touches the network.
 */
public class AuthenticatorBinder(
    private val session: HostSession,
    private val grants: AppGrantStore,
    private val guard: AccessGuard,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readyTimeoutMillis: Long = DEFAULT_READY_TIMEOUT_MILLIS,
) : IASSAuthenticatorService.Stub() {

    override fun hasLoggedIn(): Boolean = runBlocking {
        withTimeoutOrNull(readyTimeoutMillis) { session.hasLoggedIn() } ?: false
    }

    override fun isAppAuthorized(webId: String): Boolean {
        val caller = guard.caller() ?: return false
        return runBlocking { grants.get(caller, webId) != null }
    }

    override fun getAppGrant(webId: String, callback: IASSParcelableCallback) {
        val caller = guard.caller()
        scope.dispatchParcelable(dispatcher, callback) {
            SolidResult.Success(caller?.let { grants.get(it, webId) })
        }
    }

    override fun disconnectFromSolid(webId: String, callback: IASSParcelableCallback) {
        val caller = guard.caller()
        if (caller == null) {
            callback.deliverError(ExceptionsErrorCode.UNKNOWN, "Unable to resolve the calling package.")
            return
        }
        scope.dispatchBoolean(dispatcher, callback) {
            grants.revoke(caller, webId)
            SolidResult.Success(true)
        }
    }

    public companion object {
        public const val DEFAULT_READY_TIMEOUT_MILLIS: Long = 10_000L
    }
}
