package com.erfangholami.androidsolidservices.telemetry

import android.content.Context

/**
 * Installs no telemetry sink.
 *
 * The FOSS build carries no monitoring dependency, so [com.erfangholami.androidsolidservices
 * .shared.telemetry.Telemetry] keeps its no-op sink and the libraries emit nothing. F-Droid rejects
 * apps containing proprietary analytics, and its Tracking anti-feature additionally covers reporting
 * that is not opt-in and off by default — so any future sink here has to be consent-gated.
 */
fun installTelemetry(context: Context) = Unit
