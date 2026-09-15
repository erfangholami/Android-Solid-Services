package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidNotLoggedInException
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import kotlinx.coroutines.flow.Flow

/**
 * Manages sign-in authorization between a third-party app and the host app.
 *
 * Obtain an instance via [Solid.getSignInClient]. All operations require the host app, Solid
 * Share, to be installed on the device; see [Solid.isHostInstalled].
 *
 * Typical flow:
 * 1. Call [getAccount] — `null` means the app holds no grant for that WebID yet.
 * 2. Launch [AuthorizeWithSolid] from your Activity to let the user grant access, optionally
 *    with an [com.erfangholami.androidsolidservices.shared.model.grant.AccessRequest] that says
 *    what the app needs.
 * 3. Use [disconnectFromSolid] to give the grant back when the user signs out.
 *
 * Every operation is a `suspend` function that waits for the IPC binding, so there is no need
 * to collect [authServiceConnectionState] before calling; the flow is there for UI that wants
 * to show the connection.
 */
public class SolidSignInClient private constructor(context: Context) {

    public companion object {
        @Volatile
        private var instance: SolidSignInClient? = null

        /**
         * Returns the application-scoped singleton [SolidSignInClient].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getInstance(context: Context): SolidSignInClient =
            instance ?: synchronized(this) {
                instance ?: SolidSignInClient(context).also { instance = it }
            }

        /**
         * Drops the singleton and releases its binding, so the next [getInstance] builds a fresh
         * client. Exists only so instrumented tests can rebuild the client against a different
         * host; nothing in production calls it.
         */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.connector?.unbind()
                instance = null
            }
        }
    }

    private val appContext: Context = context.applicationContext

    private val connector = ServiceConnector(
        appContext,
        SolidHostContract.ACTION_AUTHENTICATOR_SERVICE,
        IASSAuthenticatorService.Stub::asInterface,
    )

    /**
     * Hot [Flow] of the IPC service connection state.
     * Emits `true` once the bound service connects and `false` if it disconnects.
     */
    public fun authServiceConnectionState(): Flow<Boolean> = connector.connectionState

    /**
     * Returns the [SolidSignInAccount] this app holds for [webId], carrying the grant the user
     * approved, or `null` when the app holds no grant for that account.
     *
     * @throws SolidException.SolidAppNotFoundException if the host app is not installed.
     * @throws SolidException.SolidServiceConnectionException if the IPC service cannot be reached.
     * @throws SolidException.SolidNotLoggedInException if no account is signed in to the host app.
     */
    public suspend fun getAccount(webId: String): SolidSignInAccount? {
        val grant = connector.suspendParcelable(AppGrant::class.java) { service, callback ->
            if (!service.hasLoggedIn()) {
                throw SolidNotLoggedInException("No Solid account is signed in to the host app.")
            }
            service.getAppGrant(webId, callback)
        }
        return grant?.let { SolidSignInAccount(appContext.packageName, webId, it) }
    }

    /**
     * Gives back this app's grant for [webId]. Returns `true` when the host recorded the
     * revocation; the app's next call as that WebID then fails with
     * [SolidException.SolidResourceException.NotPermissionException].
     *
     * @throws SolidException if the host app is not installed or cannot be reached.
     */
    public suspend fun disconnectFromSolid(webId: String): Boolean =
        connector.suspendBoolean { service, callback -> service.disconnectFromSolid(webId, callback) }
}
