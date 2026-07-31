package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_AUTH_SERVICE
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidAppNotFoundException
import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidNotLoggedInException
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLoginCallback
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLogoutCallback
import kotlinx.coroutines.flow.Flow

/**
 * Manages sign-in authorization between a third-party app and the Android Solid Services app.
 *
 * Obtain an instance via [Solid.getSignInClient]. All operations require the Android Solid
 * Services app to be installed and running on the device.
 *
 * Typical flow:
 * 1. Check [authServiceConnectionState] to confirm the IPC service is connected.
 * 2. Call [getAccount] — if it returns `null`, the app is not yet authorized.
 * 3. Launch [AuthorizeWithSolid] from your Activity to let the user grant access.
 * 4. Use [disconnectFromSolid] to revoke access when the user signs out.
 */
public class SolidSignInClient private constructor(
    context: Context,
    private val applicationInfo: ApplicationInfo,
    private val hasInstalledAndroidSolidServices: () -> Boolean,
) {

    public companion object {
        @Volatile
        private var instance: SolidSignInClient? = null

        /**
         * Returns the application-scoped singleton [SolidSignInClient].
         * @param context Any [Context]; the application context is used internally.
         * @param applicationInfo The calling app's [ApplicationInfo], used to identify the app.
         * @param hasInstalledAndroidSolidServices Returns `true` when the Android Solid Services
         *   app is installed on the device.
         */
        public fun getInstance(
            context: Context,
            applicationInfo: ApplicationInfo,
            hasInstalledAndroidSolidServices: () -> Boolean,
        ): SolidSignInClient =
            instance ?: synchronized(this) {
                instance ?: SolidSignInClient(context, applicationInfo, hasInstalledAndroidSolidServices)
                    .also { instance = it }
            }

        /**
         * Drops the singleton and releases its binding, so the next [getInstance] builds a fresh
         * client. Exists only so instrumented tests can rebuild the client against a different
         * service package or install check; nothing in production calls it.
         */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.connector?.unbind()
                instance = null
            }
        }
    }

    private val connector = ServiceConnector(
        context,
        ANDROID_SOLID_SERVICES_AUTH_SERVICE,
        IASSAuthenticatorService.Stub::asInterface,
    )

    /**
     * Hot [Flow] of the IPC service connection state.
     * Emits `true` once the bound service connects and `false` if it disconnects.
     */
    public fun authServiceConnectionState(): Flow<Boolean> = connector.connectionState

    /**
     * Returns a [SolidSignInAccount] if this app is authorized for [webId], or `null` if not yet
     * granted access.
     * @throws SolidException.SolidAppNotFoundException if the ASS app is not installed.
     * @throws SolidException.SolidServiceConnectionException if the IPC service is not connected.
     * @throws SolidException.SolidNotLoggedInException if no user is logged in.
     */
    @Throws(SolidException::class)
    public fun getAccount(webId: String): SolidSignInAccount? {
        val service = requireLoggedInService()
        return if (service.isAppAuthorized(webId)) {
            SolidSignInAccount(applicationInfo.packageName, webId)
        } else {
            null
        }
    }

    /**
     * Prompts the user to choose a Solid account and grant access.
     *
     * A profile-picker dialog is shown inside the ASS app. The result is delivered
     * asynchronously to [callBack]:
     * - `(selectedWebId, null)` — user granted access; [selectedWebId] is the chosen account
     * - `(null, null)` — user dismissed without granting
     * - `(null, error)` — an error occurred (e.g. overlay permission missing)
     *
     * Use the returned `selectedWebId` for all subsequent [SolidResourceClient] and
     * [SolidContactsDataModule] calls.
     *
     * @throws SolidException if the ASS app is not installed, not connected, or no user is logged in.
     */
    @Deprecated(
        "Launch AuthorizeWithSolid from your Activity instead: the picker is then started from " +
            "your own foreground, so Android Solid Services needs no overlay permission and " +
            "SolidServicesDrawPermissionDeniedException cannot happen.",
    )
    @Throws(SolidException::class)
    public fun requestLogin(callBack: (String?, SolidException?) -> Unit) {
        requireLoggedInService().requestLogin(object : IASSLoginCallback.Stub() {
            override fun onResult(granted: Boolean, selectedWebId: String) {
                callBack(if (granted) selectedWebId else null, null)
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                callBack(null, handleSolidException(errorCode, errorMessage))
            }
        })
    }

    /**
     * Revokes this app's access to [webId]'s Solid pod. [callBack] receives `true` on success and
     * `false` on failure.
     *
     * @throws SolidException if the ASS app is not installed, not connected, or no user is logged in.
     */
    @Throws(SolidException::class)
    public fun disconnectFromSolid(webId: String, callBack: (Boolean) -> Unit) {
        requireLoggedInService().disconnectFromSolid(webId, object : IASSLogoutCallback.Stub() {
            override fun onResult(granted: Boolean) {
                callBack(granted)
            }

            override fun onError(errorCode: Int, errorMessage: String?) {
                callBack(false)
            }
        })
    }

    private fun requireLoggedInService(): IASSAuthenticatorService {
        if (!hasInstalledAndroidSolidServices()) {
            throw SolidAppNotFoundException("Please install Android Solid Services app on your device.")
        }
        val service = connector.require()
        if (!service.hasLoggedIn()) {
            throw SolidNotLoggedInException("Please login to your Solid account in Android Solid Services app.")
        }
        return service
    }
}
