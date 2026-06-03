package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.pm.PackageManager

/** Returns `true` if the Android Solid Services app is installed on the device. */
internal fun hasInstalledAndroidSolidServices(context: Context): Boolean {
    try {
        context.packageManager.getPackageInfo(ANDROID_SOLID_SERVICES_PACKAGE_NAME, 0)
        return true
    } catch (_: PackageManager.NameNotFoundException) {
    }
    return false
}