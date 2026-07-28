package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.pm.PackageManager

internal fun hasInstalledAndroidSolidServices(context: Context): Boolean {
    try {
        context.packageManager.getPackageInfo(ANDROID_SOLID_SERVICES_PACKAGE_NAME, 0)
        return true
    } catch (_: PackageManager.NameNotFoundException) {
    }
    return false
}