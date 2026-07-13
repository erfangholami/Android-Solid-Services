package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the binary-corruption bug: [NonRDFResource] used to parcel
 * its body as a UTF-8 string, replacing every invalid byte sequence with U+FFFD.
 * A binary resource (image / PDF / .pkpass) sent over AIDL must survive byte-for-byte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NonRDFResourceParcelTest {

    private val uri = "https://alice.pod/photo.png"

    // PNG magic + bytes that are NOT valid UTF-8 (0x89, 0xC0, 0xFF, 0xFE).
    private val binaryBody = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0xC0.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0x00, 0x7F,
    )

    private fun roundTrip(resource: SolidNonRDFResource): SolidNonRDFResource {
        val parcel = Parcel.obtain()
        try {
            resource.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return SolidNonRDFResource.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `binary body survives a parcel round trip byte-for-byte`() {
        val original = SolidNonRDFResource(
            uri,
            "image/png",
            binaryBody.inputStream(),
            SolidHeaders(mapOf("ETag" to listOf("\"v1\""))),
        )
        val restored = roundTrip(original)
        assertArrayEquals(binaryBody, restored.getEntity().use { it.readBytes() })
        assertEquals("image/png", restored.getContentType())
    }

    @Test
    fun `an empty body round-trips to an empty body`() {
        val original = SolidNonRDFResource(
            uri,
            "application/octet-stream",
            ByteArray(0).inputStream(),
            null,
        )
        val restored = roundTrip(original)
        assertArrayEquals(ByteArray(0), restored.getEntity().use { it.readBytes() })
    }
}
