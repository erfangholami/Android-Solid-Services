package com.erfangholami.androidsolidservices.telemetry

import android.content.Context
import android.util.Log
import com.erfangholami.androidsolidservices.BuildConfig
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

private const val TAG = "Telemetry"

fun installTelemetry(context: Context) {
    val firebaseApp = runCatching { FirebaseApp.getInstance() }.getOrNull()
    if (firebaseApp == null) {
        Log.w(TAG, "Firebase default app failed to initialise — telemetry stays disabled")
        return
    }

    val crashlytics = FirebaseCrashlytics.getInstance()
    val performance = FirebasePerformance.getInstance()

    crashlytics.setCrashlyticsCollectionEnabled(BuildConfig.TELEMETRY_ENABLED)
    performance.isPerformanceCollectionEnabled = BuildConfig.TELEMETRY_ENABLED

    if (!BuildConfig.TELEMETRY_ENABLED) {
        Log.i(TAG, "Telemetry collection is disabled for this build type")
        return
    }

    crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)

    Telemetry.install(FirebaseTelemetrySink(crashlytics, performance))
    Log.i(TAG, "Firebase Crashlytics and Performance Monitoring installed")
}
