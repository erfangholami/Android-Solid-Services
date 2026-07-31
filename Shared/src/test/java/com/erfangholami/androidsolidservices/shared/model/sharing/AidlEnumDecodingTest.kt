package com.erfangholami.androidsolidservices.shared.model.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The sharing and notification services carry [ShareMode] and [ShareReceiver] across AIDL as bare
 * integers, and both services are exported. That makes these integers untrusted input: any app on
 * the device can send an arbitrary one, and a caller built against a newer SDK legitimately names
 * values this build has no entry for.
 *
 * Decoding by indexing `entries` raised on a binder thread inside a coroutine with no handler,
 * which killed the app and left the caller's continuation parked forever. These pin the
 * non-throwing decoders that replaced it.
 */
class AidlEnumDecodingTest {

    @Test
    fun `every mode decodes from its own ordinal`() {
        ShareMode.entries.forEach { mode ->
            assertEquals(mode, ShareMode.fromOrdinal(mode.ordinal))
        }
    }

    @Test
    fun `an out-of-range ordinal decodes to null rather than throwing`() {
        listOf(-1, ShareMode.entries.size, 99, Int.MAX_VALUE, Int.MIN_VALUE).forEach { ordinal ->
            assertNull("ordinal $ordinal must not resolve to a mode", ShareMode.fromOrdinal(ordinal))
        }
    }

    @Test
    fun `every receiver decodes from its own kind and value`() {
        listOf(
            ShareReceiver.WebIdReceiver("https://alice.pod.example/profile/card#me"),
            ShareReceiver.GroupReceiver("https://alice.pod.example/contacts/group#team"),
            ShareReceiver.Public,
        ).forEach { receiver ->
            assertEquals(receiver, ShareReceiver.fromKindOrNull(receiver.kind(), receiver.value()))
        }
    }

    @Test
    fun `an unknown receiver kind decodes to null rather than throwing`() {
        listOf(-1, 3, 99, Int.MAX_VALUE).forEach { kind ->
            assertNull(ShareReceiver.fromKindOrNull(kind, "https://mallory.example/#me"))
        }
    }

    @Test
    fun `an identified receiver with no value decodes to null`() {
        // Silently building a receiver named "null" would grant access under a nonsense identity.
        assertNull(ShareReceiver.fromKindOrNull(ShareReceiver.KIND_WEBID, null))
        assertNull(ShareReceiver.fromKindOrNull(ShareReceiver.KIND_GROUP, null))
    }

    @Test
    fun `public needs no value`() {
        assertEquals(ShareReceiver.Public, ShareReceiver.fromKindOrNull(ShareReceiver.KIND_PUBLIC, null))
    }
}
