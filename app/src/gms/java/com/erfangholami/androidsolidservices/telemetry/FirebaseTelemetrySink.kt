package com.erfangholami.androidsolidservices.telemetry

import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryNetworkSpan
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetrySink
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetrySpan
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.HttpMetric
import com.google.firebase.perf.metrics.Trace

internal class FirebaseTelemetrySink(
    private val crashlytics: FirebaseCrashlytics,
    private val performance: FirebasePerformance,
) : TelemetrySink {
    override fun startSpan(name: String): TelemetrySpan = FirebaseSpan(performance.newTrace(traceName(name)).apply { start() })

    override fun startNetworkSpan(
        url: String,
        method: String,
    ): TelemetryNetworkSpan = FirebaseNetworkSpan(performance.newHttpMetric(url, httpMethod(method)).apply { start() })

    override fun recordException(
        throwable: Throwable,
        attributes: Map<String, String>,
    ) {
        attributes.forEach { (key, value) -> crashlytics.setCustomKey(attributeKey(key), attributeValue(value)) }
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setKey(
        key: String,
        value: String,
    ) {
        crashlytics.setCustomKey(attributeKey(key), attributeValue(value))
    }
}

private class FirebaseSpan(
    private val trace: Trace,
) : TelemetrySpan {
    private var stopped = false

    override fun putAttribute(
        name: String,
        value: String,
    ) {
        if (!stopped) trace.putAttribute(attributeKey(name), attributeValue(value))
    }

    override fun putMetric(
        name: String,
        value: Long,
    ) {
        if (!stopped) trace.putMetric(attributeKey(name), value)
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        trace.stop()
    }
}

private class FirebaseNetworkSpan(
    private val metric: HttpMetric,
) : TelemetryNetworkSpan {
    private var stopped = false

    override fun setResponseCode(code: Int) {
        if (!stopped) metric.setHttpResponseCode(code)
    }

    override fun setRequestPayloadSize(bytes: Long) {
        if (!stopped) metric.setRequestPayloadSize(bytes)
    }

    override fun setResponsePayloadSize(bytes: Long) {
        if (!stopped) metric.setResponsePayloadSize(bytes)
    }

    override fun setResponseContentType(contentType: String?) {
        if (!stopped && contentType != null) metric.setResponseContentType(contentType)
    }

    override fun putAttribute(
        name: String,
        value: String,
    ) {
        if (!stopped) metric.putAttribute(attributeKey(name), attributeValue(value))
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        metric.stop()
    }
}

private const val MAX_TRACE_NAME_LENGTH = 100
private const val MAX_ATTRIBUTE_KEY_LENGTH = 40
private const val MAX_ATTRIBUTE_VALUE_LENGTH = 100

private fun traceName(name: String): String = name
    .map { if (it.isLetterOrDigit() || it == '_') it else '_' }
    .joinToString("")
    .trimStart('_')
    .take(MAX_TRACE_NAME_LENGTH)
    .ifEmpty { "unnamed" }

private fun attributeKey(name: String): String = name
    .map { if (it.isLetterOrDigit() || it == '_') it else '_' }
    .joinToString("")
    .trimStart('_')
    .take(MAX_ATTRIBUTE_KEY_LENGTH)
    .ifEmpty { "unnamed" }

private fun attributeValue(value: String): String = value.take(MAX_ATTRIBUTE_VALUE_LENGTH)

private fun httpMethod(method: String): String = when (method.uppercase()) {
    FirebasePerformance.HttpMethod.GET -> FirebasePerformance.HttpMethod.GET
    FirebasePerformance.HttpMethod.PUT -> FirebasePerformance.HttpMethod.PUT
    FirebasePerformance.HttpMethod.POST -> FirebasePerformance.HttpMethod.POST
    FirebasePerformance.HttpMethod.DELETE -> FirebasePerformance.HttpMethod.DELETE
    FirebasePerformance.HttpMethod.HEAD -> FirebasePerformance.HttpMethod.HEAD
    FirebasePerformance.HttpMethod.PATCH -> FirebasePerformance.HttpMethod.PATCH
    FirebasePerformance.HttpMethod.OPTIONS -> FirebasePerformance.HttpMethod.OPTIONS
    FirebasePerformance.HttpMethod.TRACE -> FirebasePerformance.HttpMethod.TRACE
    FirebasePerformance.HttpMethod.CONNECT -> FirebasePerformance.HttpMethod.CONNECT
    else -> FirebasePerformance.HttpMethod.GET
}
