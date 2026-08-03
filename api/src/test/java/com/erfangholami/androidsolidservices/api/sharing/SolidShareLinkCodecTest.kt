package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.sharing.implementation.SolidShareLinkCodec
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SolidShareLinkCodecTest {

    private val resource = "https://alice.pod/tickets/u1/"
    private val owner = "https://alice.pod/profile/card#me"

    @Test
    fun `a typed deep link round-trips resource, owner and type`() {
        val link = SolidShareLinkCodec.deepLink(resource, owner, Schema.TICKET)

        val parsed = requireNotNull(SolidShareLinkCodec.parse(link))
        assertEquals(resource, parsed.resourceUri)
        assertEquals(owner, parsed.ownerWebId)
        assertEquals(Schema.TICKET, parsed.resourceType)
    }

    @Test
    fun `an untyped deep link parses with a null type`() {
        val link = SolidShareLinkCodec.deepLink(resource, owner, null)

        val parsed = requireNotNull(SolidShareLinkCodec.parse(link))
        assertEquals(resource, parsed.resourceUri)
        assertNull(parsed.resourceType)
    }

    @Test
    fun `a legacy custom-scheme link still parses`() {
        val parsed = SolidShareLinkCodec.parse(
            "solidshare://share?resource=" + android.net.Uri.encode(resource),
        )

        assertEquals(resource, requireNotNull(parsed).resourceUri)
        assertNull(parsed.ownerWebId)
        assertNull(parsed.resourceType)
    }
}
