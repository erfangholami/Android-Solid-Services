package com.erfangholami.androidsolidservices.client.sdk

import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `SolidSharingClient` and `SolidNotificationsClient` flatten enums onto the AIDL boundary as bare
 * integers — `mode.ordinal`, `receiver.kind()`. The ASS app inflates them back on the other side.
 *
 * Nothing checks that the two sides agree. Reordering [ShareMode] would still compile, still run,
 * and silently grant Write where the caller asked for Read; and the two sides ship independently,
 * so an installed third-party app keeps sending the old numbering long after the enum moved. These
 * tests pin the numbering as the wire format it actually is.
 */
class WireContractTest {

    @Test
    fun `ShareMode ordinals are the wire values`() {
        assertEquals("ShareMode.READ moved; every installed caller now grants the wrong mode", 0, ShareMode.READ.ordinal)
        assertEquals(1, ShareMode.APPEND.ordinal)
        assertEquals(2, ShareMode.WRITE.ordinal)
        assertEquals(
            "a mode was added or removed — the ASS app decodes by ordinal",
            3,
            ShareMode.entries.size,
        )
    }

    @Test
    fun `ShareMode survives the ordinal round trip`() {
        ShareMode.entries.forEach { mode ->
            assertEquals(mode, ShareMode.entries[mode.ordinal])
        }
    }

    @Test
    fun `ShareReceiver kind values are the wire values`() {
        assertEquals(0, ShareReceiver.KIND_WEBID)
        assertEquals(1, ShareReceiver.KIND_GROUP)
        assertEquals(2, ShareReceiver.KIND_PUBLIC)
    }

    @Test
    fun `every receiver survives the kind-and-value round trip`() {
        val receivers = listOf(
            ShareReceiver.WebIdReceiver("https://alice.pod.example/profile/card#me"),
            ShareReceiver.GroupReceiver("https://alice.pod.example/contacts/group#team"),
            ShareReceiver.Public,
        )

        receivers.forEach { receiver ->
            assertEquals(
                "$receiver did not survive being flattened for AIDL and rebuilt",
                receiver,
                ShareReceiver.fromKind(receiver.kind(), receiver.value()),
            )
        }
    }

    @Test
    fun `Public carries no value over the wire`() {
        // createShare declares receiverValue @nullable precisely for this case.
        assertNull(ShareReceiver.Public.value())
    }

    @Test
    fun `an unknown kind is rejected rather than silently treated as a WebID`() {
        assertThrows(IllegalStateException::class.java) {
            ShareReceiver.fromKind(99, "https://mallory.example/#me")
        }
    }

    @Test
    fun `a WebID kind with no value is rejected`() {
        // Losing the value in transit must fail loudly; a receiver of "null" would be granted
        // access under a nonsense identity.
        assertThrows(IllegalStateException::class.java) {
            ShareReceiver.fromKind(ShareReceiver.KIND_WEBID, null)
        }
    }

    @Test
    fun `minus one is not a valid mode ordinal`() {
        // recordDecisionRejected sends -1 for "no mode applies". It has to stay outside the range,
        // or a rejection would be recorded as a grant of whatever mode sits at that index.
        assertEquals(
            "-1 must not index into ShareMode",
            true,
            ShareMode.entries.indices.none { it == -1 },
        )
    }
}
