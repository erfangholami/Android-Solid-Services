package com.erfangholami.androidsolidservices.client.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.client.internal.HostResolver
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AccessRequest
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantEntry
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.model.grant.RequestedTarget
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The contract is the SDK's half of the [SolidAuthorization] Intent protocol; the host app holds
 * the other half. Neither side is compiler-checked against the other, and the two ship
 * independently — so the target, the request extra and every result branch get pinned here.
 */
@RunWith(RobolectricTestRunner::class)
class AuthorizeWithSolidTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val webId = "https://alice.pod.example/profile/card#me"
    private val grant = AppGrant(
        packageName = "com.example.notes",
        webId = webId,
        appLabel = "Notes",
        entries = listOf(GrantEntry(GrantTarget.Resource("https://alice.pod.example/notes/"), AccessLevel.EDIT)),
        grantedAt = "2026-09-14T10:00:00Z",
    )

    private fun Intent.marshalled(): Intent {
        val parcel = Parcel.obtain()
        try {
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    @After
    fun tearDown() {
        HostResolver.overrideHostPackage = null
    }

    @Test
    fun `the intent launches the authorize action inside the host package`() {
        val intent = AuthorizeWithSolid().createIntent(context, Unit)

        assertEquals(SolidHostContract.ACTION_AUTHORIZE, intent.action)
        assertEquals(SolidHostContract.HOST_PACKAGE_NAME, intent.`package`)
        assertNull(intent.component)
        assertNull(intent.getParcelableExtra<AccessRequest>(SolidAuthorization.EXTRA_ACCESS_REQUEST))
    }

    @Test
    fun `the intent follows the resolved host`() {
        HostResolver.overrideHostPackage = "com.example.fakes"

        assertEquals("com.example.fakes", AuthorizeWithSolid().createIntent(context, Unit).`package`)
    }

    @Test
    fun `a request travels as an extra and survives a parcel`() {
        val request = AccessRequest(
            level = AccessLevel.FULL,
            targets = listOf(RequestedTarget.Path("notes/"), RequestedTarget.Pod),
            reason = "Notes are kept in your pod.",
        )

        val intent = AuthorizeWithSolid(request).createIntent(context, Unit).marshalled()
        intent.setExtrasClassLoader(AccessRequest::class.java.classLoader)

        assertEquals(request, intent.getParcelableExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST, AccessRequest::class.java))
    }

    @Test
    fun `a granted pick arrives as Authorized with its webId and grant`() {
        val result = AuthorizeWithSolid().parseResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(SolidAuthorization.EXTRA_WEB_ID, webId)
                .putExtra(SolidAuthorization.EXTRA_GRANT, grant)
                .marshalled(),
        )

        assertEquals(SolidSignInResult.Authorized(webId, grant), result)
    }

    @Test
    fun `success without a webId or without a grant is a Failed result, not a fake grant`() {
        val noGrant = AuthorizeWithSolid().parseResult(
            Activity.RESULT_OK,
            Intent().putExtra(SolidAuthorization.EXTRA_WEB_ID, webId),
        )
        val noWebId = AuthorizeWithSolid().parseResult(
            Activity.RESULT_OK,
            Intent().putExtra(SolidAuthorization.EXTRA_GRANT, grant),
        )

        assertTrue(noGrant is SolidSignInResult.Failed)
        assertTrue(noWebId is SolidSignInResult.Failed)
        assertTrue(AuthorizeWithSolid().parseResult(Activity.RESULT_OK, null) is SolidSignInResult.Failed)
    }

    @Test
    fun `a dismissal arrives as Dismissed`() {
        assertEquals(
            SolidSignInResult.Dismissed,
            AuthorizeWithSolid().parseResult(Activity.RESULT_CANCELED, null),
        )
    }

    @Test
    fun `an error result maps through the shared exception codes`() {
        val result = AuthorizeWithSolid().parseResult(
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
        val result = AuthorizeWithSolid().parseResult(SolidAuthorization.RESULT_ERROR, null)

        assertTrue(
            (result as SolidSignInResult.Failed).exception
                is SolidException.SolidResourceException.UnknownException,
        )
    }
}
