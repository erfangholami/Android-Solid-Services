package com.erfangholami.androidsolidservices

import android.app.Application
import com.erfangholami.androidsolidservices.domain.usecase.SystemAccountMirror
import com.erfangholami.androidsolidservices.services.dispatch.CallerAttribution
import com.erfangholami.androidsolidservices.telemetry.installTelemetry
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ASSApplication : Application() {

    @Inject
    lateinit var systemAccountMirror: SystemAccountMirror

    override fun onCreate() {
        super.onCreate()
        CallerAttribution.install(this)
        installTelemetry(this)
        systemAccountMirror.start()
    }
}
