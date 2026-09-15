package com.erfangholami.androidsolidservices.host.authorize

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * The app that launched a consent activity, as the screen shows it.
 *
 * Android reports the calling package only for an activity started for a result, which is why
 * [of] answers `null` otherwise and a consent activity refuses to run: a grant recorded for a
 * caller it cannot name would be a grant for whoever asked next.
 */
public class CallerIdentity(
    public val packageName: String,
    public val label: String,
    public val icon: Drawable?,
) {

    public companion object {

        /** The caller of [activity], or `null` when it was not launched for a result. */
        public fun of(activity: Activity): CallerIdentity? {
            val packageName = activity.callingActivity?.packageName ?: activity.callingPackage ?: return null
            return of(activity.packageManager, packageName)
        }

        /** [packageName] as installed; the label falls back to the package name when it is unknown. */
        public fun of(packageManager: PackageManager, packageName: String): CallerIdentity {
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
            val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            return CallerIdentity(packageName, label, icon)
        }
    }
}
