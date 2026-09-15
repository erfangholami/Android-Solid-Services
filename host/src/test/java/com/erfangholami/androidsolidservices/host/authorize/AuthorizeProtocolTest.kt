package com.erfangholami.androidsolidservices.host.authorize

import android.app.Activity
import android.content.Intent
import android.os.Parcel
import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.entry
import com.erfangholami.androidsolidservices.host.testing.grant
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AccessRequest
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantEntry
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.model.grant.RequestedTarget
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AuthorizeProtocolTest {

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

    @Test
    fun `no intent and no extra both mean the default request`() {
        assertEquals(AccessRequest.DEFAULT, AuthorizeProtocol.requestFrom(null))
        assertEquals(AccessRequest.DEFAULT, AuthorizeProtocol.requestFrom(Intent()))
    }

    @Test
    fun `a request sent by the SDK is read back after crossing a parcel`() {
        val request = AccessRequest(
            level = AccessLevel.FULL,
            targets = listOf(RequestedTarget.Path("notes/"), RequestedTarget.Module(DataModuleId.CONTACTS)),
            reason = "Notes live in your pod.",
        )
        val intent = Intent().putExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST, request).marshalled()

        assertEquals(request, AuthorizeProtocol.requestFrom(intent))
    }

    @Test
    fun `entries resolve each path against every storage root and keep modules and the pod`() {
        val request = AccessRequest(
            level = AccessLevel.EDIT,
            targets = listOf(
                RequestedTarget.Path("notes/"),
                RequestedTarget.Module(DataModuleId.TICKETS),
                RequestedTarget.Pod,
                RequestedTarget.Path("../escape/"),
                RequestedTarget.Path("notes/"),
            ),
        )

        val entries = AuthorizeProtocol.entriesFor(request, listOf("https://alice.pod/", "https://alice.other/"))

        assertEquals(
            listOf(
                GrantEntry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.EDIT),
                GrantEntry(GrantTarget.Resource("https://alice.other/notes/"), AccessLevel.EDIT),
                GrantEntry(GrantTarget.Module(DataModuleId.TICKETS), AccessLevel.EDIT),
                GrantEntry(GrantTarget.Pod, AccessLevel.EDIT),
            ),
            entries,
        )
    }

    @Test
    fun `a granted result carries the WebID and the grant`() {
        val approved = grant(entry(GrantTarget.Pod, AccessLevel.EDIT))

        val result = AuthorizeProtocol.grantedResult(ALICE, approved)
        val data = result.data!!.marshalled()
        data.setExtrasClassLoader(AppGrant::class.java.classLoader)

        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(ALICE, data.getStringExtra(SolidAuthorization.EXTRA_WEB_ID))
        assertEquals(approved, data.getParcelableExtra(SolidAuthorization.EXTRA_GRANT, AppGrant::class.java))
    }

    @Test
    fun `a cancelled result carries nothing`() {
        val result = AuthorizeProtocol.cancelledResult()

        assertEquals(Activity.RESULT_CANCELED, result.resultCode)
        assertNull(result.data)
    }

    @Test
    fun `an error result carries the code and the message`() {
        val result = AuthorizeProtocol.errorResult(ExceptionsErrorCode.SOLID_NOT_LOGGED_IN, "no session")
        val data = result.data!!.marshalled()

        assertEquals(SolidAuthorization.RESULT_ERROR, result.resultCode)
        assertEquals(ExceptionsErrorCode.SOLID_NOT_LOGGED_IN, data.getIntExtra(SolidAuthorization.EXTRA_ERROR_CODE, -1))
        assertEquals("no session", data.getStringExtra(SolidAuthorization.EXTRA_ERROR_MESSAGE))
    }
}
