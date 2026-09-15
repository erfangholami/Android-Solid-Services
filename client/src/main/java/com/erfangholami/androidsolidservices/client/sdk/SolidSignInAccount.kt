package com.erfangholami.androidsolidservices.client.sdk

import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant

/**
 * This app's authorized link to a Solid account, as the host currently records it.
 *
 * Returned by [SolidSignInClient.getAccount] once the user has granted the calling app access
 * to a WebID. The [grant] is what the user approved on the consent screen, which may be
 * narrower or wider than what the app asked for, and may have been changed by the user since.
 *
 * @property packageName The calling app's package name.
 * @property webId The WebID the app is authorized for.
 * @property grant What the app may do as that WebID: the targets and the level on each.
 */
public data class SolidSignInAccount(
    val packageName: String,
    val webId: String,
    val grant: AppGrant,
)
