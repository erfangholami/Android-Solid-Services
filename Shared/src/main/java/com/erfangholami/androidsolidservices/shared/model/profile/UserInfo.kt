package com.erfangholami.androidsolidservices.shared.model.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The OIDC `userinfo` endpoint response for a signed-in Solid account.
 *
 * @property webId The WebID claim returned by the OIDC provider.
 */
@Serializable
public data class UserInfo(
    @SerialName("webid")
    val webId: String,
)
