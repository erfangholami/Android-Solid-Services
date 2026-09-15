package com.erfangholami.androidsolidservices.client.internal

/**
 * The package of the retired Android Solid Services app. The SDK never binds to it; it only
 * checks for it so a device that still carries the old host gets a message that names it.
 */
internal const val LEGACY_HOST_PACKAGE_NAME: String = "com.erfangholami.androidsolidservices"

/** Where a user gets the host app when it is not installed. */
internal const val HOST_WEBSITE: String = "https://solidshare.app"
