package com.erfangholami.androidsolidservices.services.dispatch

import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver

/**
 * Decoders for the enum values AIDL carries as bare integers.
 *
 * The sharing and notification services are exported and the two sides of the boundary ship
 * independently, so these integers are untrusted: a caller built against a newer SDK can name a
 * mode this build has no entry for, and any app on the device can send an arbitrary one. Indexing
 * `ShareMode.entries` directly made that an `ArrayIndexOutOfBoundsException` on a binder thread,
 * inside a coroutine with no handler — which killed the app and left the caller's continuation
 * parked forever.
 *
 * These throw instead, which the dispatch helpers turn into an `onError` naming the bad value.
 */
fun requireShareMode(ordinal: Int): ShareMode =
    ShareMode.fromOrdinal(ordinal)
        ?: throw IllegalArgumentException(
            "Unknown ShareMode ordinal $ordinal — this build knows ${ShareMode.entries}",
        )

fun requireShareReceiver(kind: Int, value: String?): ShareReceiver =
    ShareReceiver.fromKindOrNull(kind, value)
        ?: throw IllegalArgumentException(
            "Unknown ShareReceiver kind $kind, or a missing identifier for it",
        )
