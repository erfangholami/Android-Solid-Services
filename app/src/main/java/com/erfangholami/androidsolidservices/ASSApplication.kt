package com.erfangholami.androidsolidservices

import android.app.Application
import com.erfangholami.androidsolidservices.services.dispatch.CallerAttribution
import com.erfangholami.androidsolidservices.telemetry.installFirebaseTelemetry
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ASSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CallerAttribution.install(this)
        installFirebaseTelemetry(this)
    }
}
