package com.erfangholami.androidsolidservices.client.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_AUTHORIZE_ACTIVITY
import com.erfangholami.androidsolidservices.client.internal.SdkTarget
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization

/**
 * The outcome of an [AuthorizeWithSolid] launch.
 */
public sealed class SolidSignInResult {

    /**
     * The user picked an account and granted this app access to it. Use [webId] for every
     * subsequent SDK call.
     */
    public data class Authorized(val webId: String) : SolidSignInResult()

    /** The user dismissed the picker without granting anything. Not an error. */
    public data object Dismissed : SolidSignInResult()

    /** The flow could not run; [exception] is the same typed hierarchy the rest of the SDK throws. */
    public data class Failed(val exception: SolidException) : SolidSignInResult()
}

/**
 * Signs the user in by launching Android Solid Services' account picker **from your own
 * activity**, the way a system account chooser works.
 *
 * This is the successor to [SolidSignInClient.requestLogin]: because your app launches the
 * screen, nothing is drawn from a background service — Android Solid Services needs **no
 * overlay permission**, and the whole
 * [SolidException.SolidServicesDrawPermissionDeniedException] failure mode disappears. The
 * picker also stays live while the user hops into Android Solid Services to sign in for the
 * first time.
 *
 * ```kotlin
 * private val authorize = registerForActivityResult(AuthorizeWithSolid()) { result ->
 *     when (result) {
 *         is SolidSignInResult.Authorized -> onSignedIn(result.webId)
 *         SolidSignInResult.Dismissed -> Unit
 *         is SolidSignInResult.Failed -> show(result.exception)
 *     }
 * }
 *
 * SignInButton(onClick = { authorize.launch(Unit) })
 * ```
 */
public class AuthorizeWithSolid : ActivityResultContract<Unit, SolidSignInResult>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent().setClassName(SdkTarget.servicePackageName, ANDROID_SOLID_SERVICES_AUTHORIZE_ACTIVITY)

    override fun parseResult(resultCode: Int, intent: Intent?): SolidSignInResult = when (resultCode) {
        Activity.RESULT_OK ->
            intent?.getStringExtra(SolidAuthorization.EXTRA_WEB_ID)
                ?.let { SolidSignInResult.Authorized(it) }
                ?: SolidSignInResult.Failed(
                    SolidException.SolidResourceException.UnknownException(
                        "Android Solid Services reported success but returned no WebID.",
                    ),
                )

        SolidAuthorization.RESULT_ERROR -> SolidSignInResult.Failed(
            handleSolidException(
                intent?.getIntExtra(SolidAuthorization.EXTRA_ERROR_CODE, ExceptionsErrorCode.UNKNOWN)
                    ?: ExceptionsErrorCode.UNKNOWN,
                intent?.getStringExtra(SolidAuthorization.EXTRA_ERROR_MESSAGE).orEmpty(),
            ),
        )

        else -> SolidSignInResult.Dismissed
    }
}
