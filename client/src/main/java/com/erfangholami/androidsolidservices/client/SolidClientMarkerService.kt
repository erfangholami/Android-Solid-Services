package com.erfangholami.androidsolidservices.client

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * An inert service the SDK declares in every app that depends on it, so the host app can see
 * that app through Android 11 package visibility.
 *
 * It is never bound and never started: `onBind` returns `null`. Its only job is to carry the
 * [com.erfangholami.androidsolidservices.shared.host.SolidHostContract.ACTION_CLIENT_MARKER]
 * intent filter, which a host matches in its own `<queries>` so it can read this app's label and
 * icon from `PackageManager`. Without it a host can only name a granted app by its package, and
 * reports it as uninstalled after either app is updated.
 *
 * Nothing in an app needs to reference this class. It is public because the manifest names it.
 */
public class SolidClientMarkerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}
