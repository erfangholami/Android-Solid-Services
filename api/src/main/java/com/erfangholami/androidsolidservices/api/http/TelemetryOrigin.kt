package com.erfangholami.androidsolidservices.api.http

import java.net.URI

internal fun URI.telemetryOrigin(): String {
    val scheme = scheme?.lowercase() ?: "https"
    val host = host?.lowercase() ?: return "$scheme://unknown"
    return if (port > 0) "$scheme://$host:$port" else "$scheme://$host"
}
