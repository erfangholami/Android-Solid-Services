package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * The decoded contents of a `solidshare://` share deep link.
 *
 * A link always carries the [resourceUri] it points at, and
 * optionally the [ownerWebId] of the sender so the receiver knows who shared
 * it even on the QR / link path (the notification path already carries the
 * owner). [ownerWebId] is null when the link does not encode a sender.
 */
@Parcelize
public data class ParsedShareLink(
    val resourceUri: String,
    val ownerWebId: String?,
) : Parcelable
