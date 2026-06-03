package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A share the user has received — [ownerWebId] has granted the current user [mode]
 * on [resourceUri]. Stored in `{podRoot}/solidshare/shares/received_shares.ttl`.
 */
@Parcelize
public data class ReceivedShare(
    val ownerWebId: String,
    val mode: ShareMode,
    val resourceUri: String,
) : Parcelable
