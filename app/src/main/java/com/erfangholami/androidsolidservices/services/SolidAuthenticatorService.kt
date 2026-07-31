package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.domain.usecase.LogoutUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts [SolidAuthenticator] for the system's account framework.
 *
 * When the user removes a Solid account in Settings, the framework consults the authenticator in
 * this process — the hook signs the matching profile out, so a Settings removal and an in-app
 * logout end in the same state.
 */
@AndroidEntryPoint
class SolidAuthenticatorService : Service() {

    @Inject
    lateinit var logout: LogoutUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? {
        val authenticator = SolidAuthenticator(this) { webId ->
            scope.launch { logout(webId) }
        }
        return authenticator.iBinder
    }
}
