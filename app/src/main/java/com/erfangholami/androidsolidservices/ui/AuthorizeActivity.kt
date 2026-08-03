package com.erfangholami.androidsolidservices.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.ui.theme.ASSAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The authorization surface a third-party app launches **for a result** — the overlay-free
 * replacement for the `requestLogin` flow.
 *
 * Being started from the caller's own foreground removes the two constraints that shaped the
 * legacy path: no UI starts from a background service (so no overlay permission), and
 * [getCallingPackage] identifies the caller reliably — Android only reports it for activities
 * genuinely awaiting a result, which is also why launching without one is refused rather than
 * guessed at.
 *
 * The profile list is live, so "Add account" can hand off to Android Solid Services' login and
 * the new account shows up here on return — no need to restart the flow, and no dead end when
 * nothing is signed in yet. Picking an account records the grant and finishes with `RESULT_OK`
 * + the WebID; dismissing finishes with `RESULT_CANCELED`. The Intent protocol is [SolidAuthorization]; the client SDK wraps it
 * as the `AuthorizeWithSolid` ActivityResultContract.
 */
@AndroidEntryPoint
class AuthorizeActivity : ComponentActivity() {

    private val viewModel: ProfileSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callerPackage = callingActivity?.packageName
        if (callerPackage == null) {
            setResult(
                SolidAuthorization.RESULT_ERROR,
                Intent()
                    .putExtra(SolidAuthorization.EXTRA_ERROR_CODE, ExceptionsErrorCode.UNKNOWN)
                    .putExtra(
                        SolidAuthorization.EXTRA_ERROR_MESSAGE,
                        "AuthorizeActivity must be launched for a result — use the client SDK's AuthorizeWithSolid contract.",
                    ),
            )
            finish()
            return
        }

        val callerName = try {
            packageManager
                .getApplicationLabel(packageManager.getApplicationInfo(callerPackage, 0))
                .toString()
        } catch (_: PackageManager.NameNotFoundException) {
            callerPackage
        }
        val callerIcon: Bitmap? = try {
            packageManager.getApplicationIcon(callerPackage).toBitmap(config = Bitmap.Config.ARGB_8888)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        setContent {
            ASSAppTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                ProfileSelectionScreen(
                    callerName = callerName,
                    callerIcon = callerIcon,
                    profiles = uiState.profiles,
                    onProfileSelected = { selectedWebId ->
                        viewModel.grant(callerPackage, callerName, selectedWebId)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(SolidAuthorization.EXTRA_WEB_ID, selectedWebId),
                        )
                        finish()
                    },
                    onDismiss = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onAddAccount = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_ADD_ACCOUNT, true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }
}
