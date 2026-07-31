package com.erfangholami.androidsolidservices.services

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.ui.MainActivity

/**
 * The `android.accounts` authenticator behind [SolidAuthorization.ACCOUNT_TYPE].
 *
 * Accounts of this type are **identity only**: one per signed-in WebID, so Solid profiles appear
 * in Settings → Accounts and in the system account chooser. They deliberately carry no
 * credentials — DPoP tokens are proof-of-possession bound to keys in this app's Keystore, so
 * [getAuthToken] refuses rather than pretending; pod access flows exclusively through the AIDL
 * services after the user grants it in the authorize screen.
 *
 * "Add account" (from Settings or the system chooser) routes into [MainActivity], where the real
 * OIDC sign-in lives; the resulting profile is mirrored to an account by the app's sync. Removal
 * from Settings is honoured in reverse: [getAccountRemovalAllowed] signs the profile out via
 * [onAccountRemoval] before agreeing, so the account list and the session list cannot drift.
 */
class SolidAuthenticator(
    private val context: Context,
    private val onAccountRemoval: (webId: String) -> Unit = {},
) : AbstractAccountAuthenticator(context) {

    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle = Bundle().apply {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ADD_ACCOUNT, true)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        putParcelable(AccountManager.KEY_INTENT, intent)
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(
            AccountManager.KEY_ERROR_MESSAGE,
            "Solid tokens are DPoP-bound and never leave Android Solid Services; " +
                "access pods through the client SDK instead.",
        )
    }

    override fun getAccountRemovalAllowed(
        response: AccountAuthenticatorResponse?,
        account: Account?,
    ): Bundle {
        account?.name?.let(onAccountRemoval)
        return Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true) }
    }

    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
    ): Bundle = Bundle()

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: Bundle?,
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }

    override fun getAuthTokenLabel(authTokenType: String?): String = ""

    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle = Bundle()

    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?,
    ): Bundle = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}
