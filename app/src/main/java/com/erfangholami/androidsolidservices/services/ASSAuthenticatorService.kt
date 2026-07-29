package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.RevokeAppAccessUseCase
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.DRAW_OVERLAY_NOT_PERMITTED
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.SOLID_NOT_LOGGED_IN
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode.UNKNOWN
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLoginCallback
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLogoutCallback
import com.erfangholami.androidsolidservices.ui.ProfileSelectionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
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

    @Inject
    lateinit var pendingLoginRequests: PendingLoginRequests

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

        override fun requestLogin(callback: IASSLoginCallback) {
            if (!Settings.canDrawOverlays(this@ASSAuthenticatorService)) {
                callback.onError(
                    DRAW_OVERLAY_NOT_PERMITTED,
                    "Android Solid Services doesn't have permission to draw overlay. Please ask the user to enable it in app settings."
                )
                return
            }
            if (!hasLoggedIn()) {
                callback.onError(SOLID_NOT_LOGGED_IN, "User has not logged in.")
                return
            }

            val callingUid = getCallingUid()
            val packageName = packageManager.getNameForUid(callingUid)
            if (packageName == null) {
                callback.onError(
                    UNKNOWN,
                    "Unable to resolve calling package for uid=$callingUid.",
                )
                return
            }
            val appName = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()

            val requestId = UUID.randomUUID().toString()
            pendingLoginRequests.put(
                requestId,
                PendingLoginRequest(
                    callerPackage = packageName,
                    callerName = appName,
                    callback = callback,
                )
            )

            val intent = Intent(this@ASSAuthenticatorService, ProfileSelectionActivity::class.java).apply {
                putExtra(ProfileSelectionActivity.EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        override fun disconnectFromSolid(webId: String, callback: IASSLogoutCallback) {
            val callingUid = getCallingUid()
            val packageName = packageManager.getNameForUid(callingUid)
            if (packageName == null) {
                callback.onError(
                    UNKNOWN,
                    "Unable to resolve calling package for uid=$callingUid.",
                )
                return
            }
            lifecycleScope.launch {
                revokeAppAccess(packageName, webId)
                callback.onResult(true)
            }
        }
    }
}
