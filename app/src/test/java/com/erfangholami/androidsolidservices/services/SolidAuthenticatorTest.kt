package com.erfangholami.androidsolidservices.services

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The authenticator's contract with the system: accounts are identity only. A token handed out
 * here would be a DPoP token separated from the key that makes it valid — worse than useless —
 * and a Settings removal that left the session alive would let the account list lie.
 */
@RunWith(RobolectricTestRunner::class)
class SolidAuthenticatorTest {

    private val authenticator = SolidAuthenticator(ApplicationProvider.getApplicationContext())

    @Test
    fun `getAuthToken refuses rather than inventing a token`() {
        val result = authenticator.getAuthToken(null, account(), "any", null)

        assertEquals(
            AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
            result.getInt(AccountManager.KEY_ERROR_CODE),
        )
        assertTrue(
            result.getString(AccountManager.KEY_ERROR_MESSAGE).orEmpty().contains("DPoP"),
        )
    }

    @Test
    fun `addAccount routes into the app's own sign-in`() {
        val result = authenticator.addAccount(null, SolidAuthorization.ACCOUNT_TYPE, null, null, null)

        val intent = IntentCompat.getParcelableExtra(
            Intent().putExtras(result),
            AccountManager.KEY_INTENT,
            Intent::class.java,
        )
        assertEquals(
            ComponentName(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            intent?.component,
        )
        assertTrue(
            "Settings' Add account must land on the login screen, not the main page",
            intent?.getBooleanExtra(MainActivity.EXTRA_ADD_ACCOUNT, false) == true,
        )
    }

    @Test
    fun `removal from Settings signs the profile out before agreeing`() {
        var loggedOut: String? = null
        val hooked = SolidAuthenticator(ApplicationProvider.getApplicationContext()) { loggedOut = it }

        val result = hooked.getAccountRemovalAllowed(null, account())

        assertEquals("https://alice.pod.example/profile/card#me", loggedOut)
        assertTrue(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
    }

    private fun account() = Account(
        "https://alice.pod.example/profile/card#me",
        SolidAuthorization.ACCOUNT_TYPE,
    )
}
