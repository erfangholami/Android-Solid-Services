package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.erfangholami.androidsolidservices.client.sdk.SolidException
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract

/** The one app the SDK talks to, and the Intents that reach its services and its consent screen. */
internal class HostTarget(val packageName: String) {

    fun serviceIntent(action: String): Intent = Intent(action).setPackage(packageName)

    fun authorizeIntent(): Intent = Intent(SolidHostContract.ACTION_AUTHORIZE).setPackage(packageName)
}

/**
 * Finds the host app on the device.
 *
 * Only Solid Share is a host. The retired Android Solid Services app is looked for as well, but
 * only so a device that still carries it gets a message naming it rather than a bare "not
 * installed". The two hooks exist for the instrumented suite, which hosts fakes in its own APK,
 * and for the one test that needs the host to be absent whatever the device has.
 */
internal object HostResolver {

    @Volatile
    var overrideHostPackage: String? = null

    @Volatile
    var forceAbsent: Boolean = false

    fun installedHost(context: Context): HostTarget? {
        if (forceAbsent) return null
        overrideHostPackage?.let { return HostTarget(it) }
        val host = HostTarget(SolidHostContract.HOST_PACKAGE_NAME)
        return host.takeIf { isInstalled(context, it.packageName) }
    }

    fun deprecatedHostPresent(context: Context): Boolean =
        !forceAbsent && isInstalled(context, LEGACY_HOST_PACKAGE_NAME)

    fun requireHost(
        context: Context,
        resolve: (Context) -> HostTarget? = ::installedHost,
    ): HostTarget = resolve(context) ?: throw SolidException.SolidAppNotFoundException(missingHostMessage(context))

    fun missingHostMessage(context: Context): String =
        if (deprecatedHostPresent(context)) MESSAGE_DEPRECATED_HOST else MESSAGE_NO_HOST

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    const val MESSAGE_NO_HOST: String =
        "Solid Share is not installed. Install it to sign in to Solid on this device."

    const val MESSAGE_DEPRECATED_HOST: String =
        "Android Solid Services is no longer supported by this version of the SDK. " +
            "Install Solid Share, which hosts Solid for other apps from 0.8.0 on."
}
