package com.erfangholami.androidsolidservices.client.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import com.erfangholami.androidsolidservices.client.internal.HostResolver
import com.erfangholami.androidsolidservices.client.internal.HostTarget
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.model.grant.AccessRequest
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode

/**
 * The outcome of an [AuthorizeWithSolid] launch.
 */
public sealed class SolidSignInResult {

    /**
     * The user picked an account and granted this app access to it. Use [webId] for every
     * subsequent SDK call. [grant] is what the user approved, which may be narrower or wider
     * than the [AccessRequest] that was sent; an app that needs a scope the user withheld can
     * explain why and launch the contract again.
     */
    public data class Authorized(val webId: String, val grant: AppGrant) : SolidSignInResult()

    /** The user dismissed the picker without granting anything. Not an error. */
    public data object Dismissed : SolidSignInResult()

    /** The flow could not run; [exception] is the same typed hierarchy the rest of the SDK throws. */
    public data class Failed(val exception: SolidException) : SolidSignInResult()
}

/**
 * Signs the user in by launching the host app's consent screen **from your own activity**, the
 * way a system account chooser works.
 *
 * The screen shows which app is asking, lets the user pick an account, and shows what the app
 * asks for: [request], or [AccessRequest.DEFAULT] (the whole pod at Edit) when none is given.
 * The user may narrow or widen it before approving, and the result carries what they approved.
 * Ask for the least you need: an app that keeps its data in one folder asks for that folder,
 * and only an app that shares or sends notifications asks for Full access.
 *
 * Because your app launches the screen, nothing is drawn from a background service, so the host
 * needs **no overlay permission**. The picker also stays live while the user hops into the host
 * app to sign in for the first time. Check [Solid.isHostInstalled] before launching: with no
 * host installed there is nothing to launch, and Android throws `ActivityNotFoundException`.
 *
 * ```kotlin
 * private val authorize = registerForActivityResult(
 *     AuthorizeWithSolid(
 *         AccessRequest(
 *             level = AccessLevel.EDIT,
 *             targets = listOf(RequestedTarget.Path("notes/")),
 *             reason = "Notes are kept in your pod under notes/.",
 *         ),
 *     ),
 * ) { result ->
 *     when (result) {
 *         is SolidSignInResult.Authorized -> onSignedIn(result.webId, result.grant)
 *         SolidSignInResult.Dismissed -> Unit
 *         is SolidSignInResult.Failed -> show(result.exception)
 *     }
 * }
 *
 * SignInButton(onClick = { authorize.launch(Unit) })
 * ```
 */
public class AuthorizeWithSolid(
    private val request: AccessRequest? = null,
) : ActivityResultContract<Unit, SolidSignInResult>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        val host = HostResolver.installedHost(context) ?: HostTarget(SolidHostContract.HOST_PACKAGE_NAME)
        return host.authorizeIntent().apply {
            request?.let { putExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST, it) }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): SolidSignInResult = when (resultCode) {
        Activity.RESULT_OK -> {
            val webId = intent?.getStringExtra(SolidAuthorization.EXTRA_WEB_ID)
            val grant = intent?.let(::grantFrom)
            if (webId != null && grant != null) {
                SolidSignInResult.Authorized(webId, grant)
            } else {
                SolidSignInResult.Failed(
                    SolidException.SolidResourceException.UnknownException(
                        "The host app reported success but returned no WebID or no grant.",
                    ),
                )
            }
        }

        SolidAuthorization.RESULT_ERROR -> SolidSignInResult.Failed(
            handleSolidException(
                intent?.getIntExtra(SolidAuthorization.EXTRA_ERROR_CODE, ExceptionsErrorCode.UNKNOWN)
                    ?: ExceptionsErrorCode.UNKNOWN,
                intent?.getStringExtra(SolidAuthorization.EXTRA_ERROR_MESSAGE).orEmpty(),
            ),
        )

        else -> SolidSignInResult.Dismissed
    }

    private fun grantFrom(intent: Intent): AppGrant? {
        intent.setExtrasClassLoader(AppGrant::class.java.classLoader)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SolidAuthorization.EXTRA_GRANT, AppGrant::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(SolidAuthorization.EXTRA_GRANT)
        }
    }
}
