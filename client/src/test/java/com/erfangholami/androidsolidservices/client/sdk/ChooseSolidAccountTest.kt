package com.erfangholami.androidsolidservices.client.sdk

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The chooser Intent's extras are the entire filter: a wrong or missing account-type array shows
 * the user every account on the device instead of only Solid ones.
 */
@RunWith(RobolectricTestRunner::class)
class ChooseSolidAccountTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val contract = ChooseSolidAccount()

    @Test
    fun `the chooser is filtered to the Solid account type`() {
        val intent = contract.createIntent(context, Unit)

        val types = intent.getStringArrayExtra("allowableAccountTypes")
        assertTrue(
            "the chooser must only offer Solid accounts, got ${types?.toList()}",
            types?.toList() == listOf(SolidAuthorization.ACCOUNT_TYPE),
        )
    }

    @Test
    fun `a pick returns the account name, which is the webId`() {
        val webId = contract.parseResult(
            Activity.RESULT_OK,
            Intent().putExtra(
                AccountManager.KEY_ACCOUNT_NAME,
                "https://alice.pod.example/profile/card#me",
            ),
        )

        assertEquals("https://alice.pod.example/profile/card#me", webId)
    }

    @Test
    fun `backing out returns null`() {
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, null))
    }

    @Test
    fun `a malformed success without a name returns null rather than crashing`() {
        assertNull(contract.parseResult(Activity.RESULT_OK, Intent()))
    }
}
