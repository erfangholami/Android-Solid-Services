package com.erfangholami.androidsolidservices.shared.ipc

import android.os.Bundle
import android.os.Parcel
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every envelope is marshalled through a real [Parcel] before it is read back, because that is
 * the only step that can fail: in-process a Bundle hands the identical object back and would
 * pass any assertion made of it.
 *
 * The sealed [AccessProbe] is the case worth pinning. A Bundle stamps the *runtime* class name,
 * so `AccessProbe.Denied` is written under its own name while the only `CREATOR` lives on the
 * parent — the typed AIDL parameter this envelope replaced never had to resolve that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class IpcEnvelopeTest {

    private fun marshalled(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(null))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `a single parcelable survives the envelope`() {
        val envelope = marshalled(IpcEnvelope.of(SolidRDFResource("https://alice.pod/card")))
        val restored = envelope.parcelable(SolidRDFResource::class.java)

        assertEquals("https://alice.pod/card", restored?.getIdentifier())
    }

    @Test
    fun `a null value is an answer, not a failure`() {
        assertNull(marshalled(IpcEnvelope.of(null)).parcelable(SolidRDFResource::class.java))
    }

    @Test
    fun `a sealed parcelable subtype resolves the creator on its parent`() {
        val envelope = marshalled(IpcEnvelope.of(AccessProbe.Denied))

        assertEquals(AccessProbe.Denied, envelope.parcelable(AccessProbe::class.java))
    }

    @Test
    fun `the other sealed subtype keeps its payload`() {
        val probe = AccessProbe.Accessible(setOf(ShareMode.READ, ShareMode.WRITE), "https://alice.pod/#me")
        val envelope = marshalled(IpcEnvelope.of(probe))

        assertEquals(probe, envelope.parcelable(AccessProbe::class.java))
    }

    @Test
    fun `a list survives the envelope in order`() {
        val values = listOf(
            SolidRDFResource("https://alice.pod/one"),
            SolidRDFResource("https://alice.pod/two"),
        )
        val restored = marshalled(IpcEnvelope.ofList(values)).parcelableList(SolidRDFResource::class.java)

        assertEquals(listOf("https://alice.pod/one", "https://alice.pod/two"), restored.map { it.getIdentifier() })
    }

    @Test
    fun `an absent list reads as empty rather than throwing`() {
        assertTrue(
            marshalled(IpcEnvelope.ofList(null)).parcelableList(SolidRDFResource::class.java).isEmpty(),
        )
        assertTrue(null.parcelableList(SolidRDFResource::class.java).isEmpty())
    }

    @Test
    fun `strings and booleans survive the envelope`() {
        assertEquals(
            "https://alice.pod/new",
            marshalled(IpcEnvelope.ofString("https://alice.pod/new")).stringValue(),
        )
        assertNull(marshalled(IpcEnvelope.ofString(null)).stringValue())
        assertTrue(marshalled(IpcEnvelope.ofBoolean(true)).booleanValue())
    }

    @Test
    fun `an empty envelope reports success without carrying a value`() {
        val envelope = marshalled(IpcEnvelope.empty())

        assertNull(envelope.stringValue())
        assertEquals(false, envelope.booleanValue())
        assertNull(envelope.parcelable(SolidRDFResource::class.java))
    }

    @Test
    fun `a login outcome carries both the flag and the selected WebID`() {
        val envelope = marshalled(IpcEnvelope.ofLogin(granted = true, selectedWebId = "https://alice.pod/#me"))

        assertTrue(envelope.loginGranted())
        assertEquals("https://alice.pod/#me", envelope.stringValue())
    }

    @Test
    fun `an unknown content length reads back as minus one`() {
        assertEquals(-1L, marshalled(IpcEnvelope.empty()).streamContentLength())
        assertEquals("", marshalled(IpcEnvelope.empty()).streamContentType())
    }
}
