package com.erfangholami.androidsolidservices.shared.model.grant

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/** One target of a grant and the [level] the app holds on it. */
@Parcelize
@Serializable
public data class GrantEntry(
    val target: GrantTarget,
    val level: AccessLevel,
) : Parcelable
