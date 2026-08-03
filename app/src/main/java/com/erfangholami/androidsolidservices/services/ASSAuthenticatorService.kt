package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.RevokeAppAccessUseCase
import com.erfangholami.androidsolidservices.services.dispatch.dispatchBoolean
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.DRAW_OVERLAY_NOT_PERMITTED
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.UNKNOWN
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@AndroidEntryPoint
class ASSAuthenticatorService : LifecycleService(), SavedStateRegistryOwner {

    private val registryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = registryController.savedStateRegistry

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var accessGrantRepository: AccessGrantRepository

    @Inject
    lateinit var revokeAppAccess: RevokeAppAccessUseCase

    override fun onCreate() {
        super.onCreate()
        registryController.performAttach()
        registryController.performRestore(null)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private val binder = object : IASSAuthenticatorService.Stub() {
        override fun hasLoggedIn(): Boolean {
            return authRepository.isUserAuthorized()
        }

        override fun isAppAuthorized(webId: String): Boolean {
            val packageName = packageManager.getNameForUid(getCallingUid()) ?: return false
            return accessGrantRepository.hasAccessGrant(packageName, webId)
        }

        /**
         * Answers with an error and never shows a picker.
         *
         * Showing one meant starting an activity from this service, which Android permits only
         * with the overlay permission the app no longer requests. The method stays on the AIDL
         * interface so installed apps keep their transaction numbering, and reports the failure
         * immediately rather than leaving the caller waiting on a callback that cannot arrive.
         */
        override fun requestLogin(callback: IASSParcelableCallback) {
            callback.onError(
                DRAW_OVERLAY_NOT_PERMITTED,
                "requestLogin is no longer supported. Launch the AuthorizeWithSolid contract " +
                    "from your own Activity instead — it starts the account picker in your " +
                    "foreground and returns the chosen WebID as an activity result.",
            )
        }

        override fun disconnectFromSolid(webId: String, callback: IASSParcelableCallback) {
            val callingUid = getCallingUid()
            val packageName = packageManager.getNameForUid(callingUid)
            if (packageName == null) {
                callback.onError(
                    UNKNOWN,
                    "Unable to resolve calling package for uid=$callingUid.",
                )
                return
            }
            lifecycleScope.dispatchBoolean(Dispatchers.IO, callback) {
                revokeAppAccess(packageName, webId)
                SolidResult.Success(true)
            }
        }
    }
}
