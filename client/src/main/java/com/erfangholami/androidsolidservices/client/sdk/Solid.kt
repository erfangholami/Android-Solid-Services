package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.hasInstalledAndroidSolidServices
import com.erfangholami.androidsolidservices.client.sdk.Solid.Companion.getContactsDataModule
import com.erfangholami.androidsolidservices.client.sdk.Solid.Companion.getResourceClient
import com.erfangholami.androidsolidservices.client.sdk.Solid.Companion.getSignInClient

/**
 * Entry point for the Android Solid Services client SDK.
 *
 * Third-party apps use this class to obtain the SDK clients:
 * - [getSignInClient] — authenticate with Solid and manage authorization
 * - [getResourceClient] — read, create, update and delete Solid pod resources
 * - [getContactsDataModule] — access the Solid Contacts data module
 * - [getSharingClient] — create, list and revoke shares of pod resources
 * - [getNotificationsClient] — read and send share-notification inbox messages
 *
 * All clients are singletons per application process and communicate with the
 * Android Solid Services app via AIDL bound services.
 *
 * **Prerequisites:** The Android Solid Services app must be installed and the user must
 * have logged in before calling resource or contacts operations.
 *
 * ### Typical setup (in `Application.onCreate` or a Hilt module):
 * ```kotlin
 * val signIn = Solid.getSignInClient(context)
 * signIn.requestLogin { selectedWebId, _ -> ... }
 * ```
 */
public class Solid {

    public companion object {

        /**
         * Returns the [SolidSignInClient] singleton.
         *
         * Use this client to request login authorization, check sign-in status, and
         * disconnect from Solid.
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getSignInClient(context: Context): SolidSignInClient {
            return SolidSignInClient.getInstance(
                context,
                context.applicationInfo,
                hasInstalledAndroidSolidServices = {
                    hasInstalledAndroidSolidServices(context)
                }
            )
        }

        /**
         * Returns the [SolidResourceClient] singleton.
         *
         * Use this client to read, create, update and delete resources on the user's Solid pod.
         * Requires the user to be signed in via [getSignInClient].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getResourceClient(context: Context): SolidResourceClient {
            return SolidResourceClient.getInstance(
                context,
                hasInstalledAndroidSolidServices = {
                    hasInstalledAndroidSolidServices(context)
                }
            )
        }

        /**
         * Returns the [SolidContactsDataModule] singleton.
         *
         * Use this module to manage address books, contacts and groups stored on the user's
         * Solid pod using the Solid Contacts specification.
         * Requires the user to be signed in via [getSignInClient].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getContactsDataModule(context: Context): SolidContactsDataModule {
            return SolidContactsDataModule.getInstance(
                context,
            )
        }

        /**
         * Returns the [SolidSharingClient] singleton.
         *
         * Use this client to create, list, and revoke shares of pod resources.
         * Requires the user to be signed in via [getSignInClient].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getSharingClient(context: Context): SolidSharingClient {
            return SolidSharingClient.getInstance(context)
        }

        /**
         * Returns the [SolidNotificationsClient] singleton.
         *
         * Use this client to read and send share-notification inbox messages (LDN).
         * Requires the user to be signed in via [getSignInClient].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getNotificationsClient(context: Context): SolidNotificationsClient {
            return SolidNotificationsClient.getInstance(context)
        }
    }
}
