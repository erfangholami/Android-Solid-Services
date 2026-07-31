package com.erfangholami.androidsolidservices.ui

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.navigation.compose.rememberNavController
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.ui.navigation.ASSAppNavHost
import com.erfangholami.androidsolidservices.ui.theme.ASSAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Set by the account authenticator's "Add account" Intent: land on the login screen
         * directly, not on whatever the startup routing would pick.
         */
        const val EXTRA_ADD_ACCOUNT = "com.erfangholami.androidsolidservices.extra.ADD_ACCOUNT"
    }

    /**
     * Present only when the system's account framework started this activity. Whoever asked is
     * blocked on it, so every exit path has to answer exactly once — success, or cancellation.
     */
    private var authenticatorResponse: AccountAuthenticatorResponse? = null
    private var responded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startAtLogin = intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false)
        authenticatorResponse = IntentCompat.getParcelableExtra(
            intent,
            AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
            AccountAuthenticatorResponse::class.java,
        )?.also { it.onRequestContinued() }

        if (startAtLogin) {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() = completeAddAccount(webId = null)
                },
            )
        }

        setContent {
            val navController = rememberNavController()
            ASSAppTheme {
                ASSAppNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    navController = navController,
                    startAtLogin = startAtLogin,
                    onAddAccountComplete = ::completeAddAccount,
                )
            }
        }
    }

    /**
     * Answers the account framework and closes, which returns the user to wherever they tapped
     * "Add account" — Settings, or another app's account chooser. Finishing without answering
     * would leave that caller waiting on a future nobody completes.
     */
    private fun completeAddAccount(webId: String?) {
        if (!responded) {
            responded = true
            val response = authenticatorResponse
            when {
                response == null -> Unit

                webId != null -> response.onResult(
                    Bundle().apply {
                        putString(AccountManager.KEY_ACCOUNT_NAME, webId)
                        putString(AccountManager.KEY_ACCOUNT_TYPE, SolidAuthorization.ACCOUNT_TYPE)
                    },
                )

                else -> response.onError(AccountManager.ERROR_CODE_CANCELED, "canceled")
            }
            if (webId != null) {
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(AccountManager.KEY_ACCOUNT_NAME, webId)
                        .putExtra(AccountManager.KEY_ACCOUNT_TYPE, SolidAuthorization.ACCOUNT_TYPE),
                )
            }
        }
        finish()
    }
}
