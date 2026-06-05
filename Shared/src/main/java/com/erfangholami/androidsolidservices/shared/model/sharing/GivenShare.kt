package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A share the user has given (created) — the user grants [receiver] access of [mode]
 * on [resourceUri]. Stored in `{podRoot}/solidshare/shares/given_shares.ttl`.
 *
 * [createdAt] is the ISO-8601 instant the share was created (`dcterms:created`
 * on the index record), or `null` for legacy rows written before timestamps
 * existed or rows reconstructed from a pod ACL scan (where the moment is
 * unknown). Several modes for the same `(receiver, resourceUri)` pair share the
 * one record, hence the one timestamp.
 */
@Parcelize
public data class GivenShare(
    val receiver: ShareReceiver,
    val mode: ShareMode,
    val resourceUri: String,
    val createdAt: String? = null,
) : Parcelable
