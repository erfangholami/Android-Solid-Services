package com.erfangholami.androidsolidservices.shared.model.grant

import android.content.Intent
import android.os.Parcel
import com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A grant travels three ways: as a Parcelable inside the authorize result, as an Intent extra
 * from the SDK to the consent screen, and as JSON in the host's store. Each one goes through a
 * real [Parcel] or the real serializer here, because in-process a Bundle would hand the same
 * object back. The JSON shape is pinned verbatim: a host persists it, so it is a contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class GrantModelRoundTripTest {

    private val grant = AppGrant(
        packageName = "com.example.notes",
        webId = "https://alice.pod/profile/card#me",
        appLabel = "Notes",
        entries = listOf(
            GrantEntry(GrantTarget.Pod, AccessLevel.EDIT),
            GrantEntry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.FULL),
            GrantEntry(GrantTarget.Module(DataModuleId.CONTACTS), AccessLevel.VIEW),
        ),
        grantedAt = "2026-09-14T10:00:00Z",
    )

    private val request = AccessRequest(
        level = AccessLevel.FULL,
        targets = listOf(
            RequestedTarget.Path("notes/"),
            RequestedTarget.Module(DataModuleId.CONTACTS),
            RequestedTarget.Pod,
        ),
        reason = "Notes are kept in your pod.",
    )

    @Test
    fun `a grant survives a parcel with its sealed targets`() {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(grant, 0)
            parcel.setDataPosition(0)
            val restored = parcel.readParcelable(AppGrant::class.java.classLoader, AppGrant::class.java)

            assertEquals(grant, restored)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `a request survives the authorize Intent`() {
        val sent = Intent().putExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST, request)
        val parcel = Parcel.obtain()
        try {
            sent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val received = Intent.CREATOR.createFromParcel(parcel)
            received.setExtrasClassLoader(AccessRequest::class.java.classLoader)

            assertEquals(
                request,
                received.getParcelableExtra(SolidAuthorization.EXTRA_ACCESS_REQUEST, AccessRequest::class.java),
            )
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `a grant round-trips through JSON in the pinned shape`() {
        val json = Json.encodeToString(AppGrant.serializer(), grant)

        assertEquals(
            """{"packageName":"com.example.notes","webId":"https://alice.pod/profile/card#me","appLabel":"Notes",""" +
                """"entries":[{"target":{"type":"pod"},"level":"EDIT"},""" +
                """{"target":{"type":"resource","uri":"https://alice.pod/notes/"},"level":"FULL"},""" +
                """{"target":{"type":"module","id":"contacts"},"level":"VIEW"}],"grantedAt":"2026-09-14T10:00:00Z"}""",
            json,
        )
        assertEquals(grant, Json.decodeFromString(AppGrant.serializer(), json))
    }

    @Test
    fun `a request round-trips through JSON`() {
        val json = Json.encodeToString(AccessRequest.serializer(), request)

        assertEquals(request, Json.decodeFromString(AccessRequest.serializer(), json))
    }

    @Test
    fun `an empty request asks for the whole pod at Edit`() {
        assertEquals(AccessLevel.EDIT, AccessRequest.DEFAULT.level)
        assertEquals(listOf(RequestedTarget.Pod), AccessRequest.DEFAULT.targets)
        assertEquals(null, AccessRequest.DEFAULT.reason)
        assertEquals(AccessRequest.DEFAULT, AccessRequest())
    }
}
