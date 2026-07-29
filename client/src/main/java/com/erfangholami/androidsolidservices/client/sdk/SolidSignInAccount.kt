package com.erfangholami.androidsolidservices.client.sdk

/**
 * Represents this app's authorized link to a Solid account.
 *
 * Returned by [SolidSignInClient.getAccount] once the user has granted the
 * calling app access to a WebID.
 *
 * @property packageName The calling app's package name.
 * @property webId The WebID the app is authorized for.
 * @property fullAccess Whether the app was granted full access to the account.
 */
public data class SolidSignInAccount(
    val packageName: String,
    val webId: String,
    val fullAccess: Boolean = true
)
