package com.erfangholami.androidsolidservices.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.androidsolidservices.services.PendingLoginRequests
import com.erfangholami.androidsolidservices.ui.theme.ASSAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The legacy authorization surface, drawn over the calling app from the bound service — which is
 * what requires the overlay permission. Kept for installed SDKs that still call `requestLogin`;
 * new integrations launch [AuthorizeActivity] for a result instead and need no permission.
 */
@AndroidEntryPoint
class ProfileSelectionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }

    private val viewModel: ProfileSelectionViewModel by viewModels()

    @Inject
    lateinit var pendingLoginRequests: PendingLoginRequests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val request = requestId?.let { pendingLoginRequests.get(it) }

        if (request == null) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                request.callback.onResult(false, "")
                pendingLoginRequests.remove(requestId)
                finish()
            }
        })

        val callerIcon: Bitmap? = try {
            packageManager.getApplicationIcon(request.callerPackage)
                .toBitmap(config = Bitmap.Config.ARGB_8888)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        setContent {
            ASSAppTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                ProfileSelectionScreen(
                    callerName = request.callerName,
                    callerIcon = callerIcon,
                    profiles = uiState.profiles,
                    onProfileSelected = { selectedWebId ->
                        viewModel.grant(request.callerPackage, request.callerName, selectedWebId)
                        request.callback.onResult(true, selectedWebId)
                        pendingLoginRequests.remove(requestId)
                        finish()
                    },
                    onDismiss = {
                        request.callback.onResult(false, "")
                        pendingLoginRequests.remove(requestId)
                        finish()
                    }
                )
            }
        }
    }
}
