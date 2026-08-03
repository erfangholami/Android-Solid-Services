package com.erfangholami.androidsolidservices.client.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_PACKAGE_NAME
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The contract is the SDK's half of the [SolidAuthorization] Intent protocol; the ASS app holds
 * the other half. Neither side is compiler-checked against the other, and the two ship
 * independently — so the target component and every result branch get pinned here.
 */
@RunWith(RobolectricTestRunner::class)
class AuthorizeWithSolidTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val contract = AuthorizeWithSolid()

    @Test
    fun `the intent targets the authorize activity in the ASS app`() {
        val intent = contract.createIntent(context, Unit)

        assertEquals(ANDROID_SOLID_SERVICES_PACKAGE_NAME, intent.component?.packageName)
        assertEquals(
            "com.erfangholami.androidsolidservices.ui.AuthorizeActivity",
            intent.component?.className,
        )
    }

    @Test
    fun `a granted pick arrives as Authorized with its webId`() {
        val result = contract.parseResult(
            Activity.RESULT_OK,
            Intent().putExtra(
                SolidAuthorization.EXTRA_WEB_ID,
                "https://alice.pod.example/profile/card#me",
            ),
        )

        assertEquals(
            SolidSignInResult.Authorized("https://alice.pod.example/profile/card#me"),
            result,
        )
    }

    @Test
    fun `success without a webId is a Failed result, not a crash or a fake grant`() {
        val result = contract.parseResult(Activity.RESULT_OK, Intent())

        assertTrue(result is SolidSignInResult.Failed)
    }

    @Test
    fun `a dismissal arrives as Dismissed`() {
        assertEquals(
            SolidSignInResult.Dismissed,
            contract.parseResult(Activity.RESULT_CANCELED, null),
        )
    }

    @Test
    fun `an error result maps through the shared exception codes`() {
        val result = contract.parseResult(
            SolidAuthorization.RESULT_ERROR,
            Intent()
                .putExtra(SolidAuthorization.EXTRA_ERROR_CODE, ExceptionsErrorCode.SOLID_NOT_LOGGED_IN)
                .putExtra(SolidAuthorization.EXTRA_ERROR_MESSAGE, "no session"),
        )

        val failed = result as SolidSignInResult.Failed
        assertTrue(failed.exception is SolidException.SolidNotLoggedInException)
        assertEquals("no session", failed.exception.message)
    }

    @Test
    fun `an error result without extras still fails typed`() {
        val result = contract.parseResult(SolidAuthorization.RESULT_ERROR, null)

        assertTrue(
            (result as SolidSignInResult.Failed).exception
                is SolidException.SolidResourceException.UnknownException,
        )
    }
}
