package com.erfangholami.androidsolidservices.shared.model.grant

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * What an app asks the user for when it launches the host's consent screen.
 *
 * The screen starts from this request. The user may narrow it or widen it, and the grant that
 * comes back is what they approved, not what was asked. An app that passes no request asks for
 * [DEFAULT]: the whole pod at [AccessLevel.EDIT]. Sharing and notifications need
 * [AccessLevel.FULL], so an app that uses them asks for it explicitly.
 *
 * @property level The level asked for on every target.
 * @property targets What the app wants to reach; never empty.
 * @property reason One sentence, shown verbatim on the consent screen, that says why.
 */
@Parcelize
@Serializable
public data class AccessRequest(
    val level: AccessLevel = AccessLevel.EDIT,
    val targets: List<RequestedTarget> = listOf(RequestedTarget.Pod),
    val reason: String? = null,
) : Parcelable {

    public companion object {

        /** The whole pod at [AccessLevel.EDIT], with no reason. */
        public val DEFAULT: AccessRequest = AccessRequest()
    }
}
