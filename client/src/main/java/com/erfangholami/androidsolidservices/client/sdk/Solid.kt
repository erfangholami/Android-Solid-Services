package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.erfangholami.androidsolidservices.client.internal.HOST_WEBSITE
import com.erfangholami.androidsolidservices.client.internal.HostResolver
import com.erfangholami.androidsolidservices.client.sdk.Solid.Companion.getContactsDataModule
import com.erfangholami.androidsolidservices.client.sdk.Solid.Companion.getResourceClient
import com.erfangholami.androidsolidservices.client.sdk.Solid.Companion.getSignInClient
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract

/**
 * Entry point for the Android Solid Services client SDK.
 *
 * Third-party apps use this class to obtain the SDK clients:
 * - [getSignInClient] — authenticate with Solid and manage authorization
 * - [getResourceClient] — read, create, update and delete Solid pod resources
 * - [getContactsDataModule] — access the Solid Contacts data module
 * - [getTicketsDataModule] — access the wallet (tickets) data module
 * - [getSharingClient] — create, list and revoke shares of pod resources
 * - [getNotificationsClient] — read and send share-notification inbox messages
 *
 * All clients are singletons per application process and communicate with the host app, Solid
 * Share, via AIDL bound services. The host owns the accounts and the tokens; your app receives
 * results and never handles a credential.
 *
 * **Prerequisites:** Solid Share must be installed ([isHostInstalled]; offer [hostInstallIntent]
 * when it is not) and the user must have granted your app access to an account through
 * [AuthorizeWithSolid] before calling resource, module, sharing or notification operations.
 *
 * ### Typical setup
 * ```kotlin
 * val authorize = registerForActivityResult(AuthorizeWithSolid()) { result -> ... }
 * authorize.launch(Unit)
 * ```
 */
public class Solid {

    public companion object {

        /** The package name of the host app, Solid Share. */
        public const val HOST_PACKAGE_NAME: String = SolidHostContract.HOST_PACKAGE_NAME

        /**
         * `true` when the host app is installed. Every client call needs it; without it they
         * fail with [SolidException.SolidAppNotFoundException].
         */
        public fun isHostInstalled(context: Context): Boolean = HostResolver.installedHost(context) != null

        /**
         * An Intent that takes the user to install the host app: the device's app store when
         * one is present, otherwise the Solid Share website.
         */
        public fun hostInstallIntent(context: Context): Intent {
            val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HOST_PACKAGE_NAME"))
            return if (store.resolveActivity(context.packageManager) != null) {
                store
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(HOST_WEBSITE))
            }
        }

        /**
         * Returns the [SolidSignInClient] singleton.
         *
         * Use this client to read the grant your app holds and to give it back.
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getSignInClient(context: Context): SolidSignInClient = SolidSignInClient.getInstance(context)

        /**
         * Returns the [SolidResourceClient] singleton.
         *
         * Use this client to read, create, update and delete resources on the user's Solid pod.
         * Requires a grant obtained through [AuthorizeWithSolid].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getResourceClient(context: Context): SolidResourceClient = SolidResourceClient.getInstance(context)

        /**
         * Returns the [SolidContactsDataModule] singleton.
         *
         * Use this module to manage address books, contacts and groups stored on the user's
         * Solid pod using the Solid Contacts specification.
         * Requires a grant obtained through [AuthorizeWithSolid].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getContactsDataModule(context: Context): SolidContactsDataModule =
            SolidContactsDataModule.getInstance(context)

        /**
         * Returns the [SolidTicketsDataModule] singleton.
         *
         * Use this module to manage the user's wallet — `schema:Ticket` resources stored on
         * their Solid pod (event tickets, boarding passes, passes imported from `.pkpass`).
         * Requires a grant obtained through [AuthorizeWithSolid].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getTicketsDataModule(context: Context): SolidTicketsDataModule =
            SolidTicketsDataModule.getInstance(context)

        /**
         * Returns the [SolidSharingClient] singleton.
         *
         * Use this client to create, list, and revoke shares of pod resources.
         * Requires a grant at Full access obtained through [AuthorizeWithSolid].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getSharingClient(context: Context): SolidSharingClient = SolidSharingClient.getInstance(context)

        /**
         * Returns the [SolidNotificationsClient] singleton.
         *
         * Use this client to read and send share-notification inbox messages (LDN).
         * Requires a grant obtained through [AuthorizeWithSolid]; sending needs Full access.
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getNotificationsClient(context: Context): SolidNotificationsClient =
            SolidNotificationsClient.getInstance(context)
    }
}
