package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A share the user has given (created) — the user grants [receiver] access of [mode]
 * on [resourceUri]. Stored in `{podRoot}/solidshare/shares/given_shares.ttl`.
 */
@Parcelize
public data class GivenShare(
    val receiver: ShareReceiver,
    val mode: ShareMode,
    val resourceUri: String,
) : Parcelable
