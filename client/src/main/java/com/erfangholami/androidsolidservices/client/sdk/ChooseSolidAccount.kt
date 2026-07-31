package com.erfangholami.androidsolidservices.client.sdk

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization

/**
 * Opens the **system** account chooser over Android Solid Services' accounts — every signed-in
 * WebID appears there, with "Add account" routing into Android Solid Services' own sign-in.
 *
 * Returns the chosen WebID, or `null` when the user backs out. Two properties come from the OS
 * itself:
 *  - on Android 8+ your app cannot even see these accounts until the user picks one here; the
 *    pick is what grants your app visibility of exactly that account, mediated by the system;
 *  - picking is *identification*, not authorization — it does not grant pod access. Follow up
 *    with [AuthorizeWithSolid], where the user confirms what your app may do as that WebID.
 *
 * [AuthorizeWithSolid] alone already covers the common case (its picker both selects and
 * grants); reach for this contract when you want the platform-native chooser, or its "Add
 * account" entry point.
 *
 * ```kotlin
 * private val choose = registerForActivityResult(ChooseSolidAccount()) { webId ->
 *     if (webId != null) authorize.launch(Unit)
 * }
 * choose.launch(Unit)
 * ```
 */
public class ChooseSolidAccount : ActivityResultContract<Unit, String?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf(SolidAuthorization.ACCOUNT_TYPE),
            null,
            null,
            null,
            null,
        )

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) {
            intent?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        } else {
            null
        }
}
