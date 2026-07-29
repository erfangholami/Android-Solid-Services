package com.erfangholami.androidsolidservices.shared.telemetry

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingSpan : TelemetrySpan {
    val attributes = mutableMapOf<String, String>()
    val metrics = mutableMapOf<String, Long>()
    var stops = 0

    override fun putAttribute(
        name: String,
        value: String,
    ) {
        attributes[name] = value
    }

    override fun putMetric(
        name: String,
        value: Long,
    ) {
        metrics[name] = value
    }

    override fun stop() {
        stops++
    }
}

private class RecordingSink : TelemetrySink {
    val spans = mutableMapOf<String, RecordingSpan>()
    val exceptions = mutableListOf<Pair<Throwable, Map<String, String>>>()
    val logs = mutableListOf<String>()
    val keys = mutableMapOf<String, String>()

    override fun startSpan(name: String): TelemetrySpan = RecordingSpan().also { spans[name] = it }

    override fun startNetworkSpan(
        url: String,
        method: String,
    ): TelemetryNetworkSpan = throw UnsupportedOperationException("unused")

    override fun recordException(
        throwable: Throwable,
        attributes: Map<String, String>,
    ) {
        exceptions += throwable to attributes
    }

    override fun log(message: String) {
        logs += message
    }

    override fun setKey(
        key: String,
        value: String,
    ) {
        keys[key] = value
    }
}

/** Fails on every call, standing in for a sink whose backend is broken or misconfigured. */
private object HostileSink : TelemetrySink {
    override fun startSpan(name: String): TelemetrySpan = error("boom")

    override fun startNetworkSpan(
        url: String,
        method: String,
    ): TelemetryNetworkSpan = error("boom")

    override fun recordException(
        throwable: Throwable,
        attributes: Map<String, String>,
    ): Unit = error("boom")

    override fun log(message: String): Unit = error("boom")

    override fun setKey(
        key: String,
        value: String,
    ): Unit = error("boom")
}

class TelemetryTest {

    @After
    fun tearDown() {
        Telemetry.uninstall()
    }

    @Test
    fun `nothing is installed by default`() {
        assertFalse(Telemetry.isInstalled)
    }

    @Test
    fun `signals are discarded until a sink is installed`() {
        Telemetry.log("dropped")
        Telemetry.setKey("k", "v")
        Telemetry.recordException(IllegalStateException("dropped"))
        val span = Telemetry.startSpan("dropped")
        span.putAttribute("a", "b")
        span.stop()
        assertFalse(Telemetry.isInstalled)
    }

    @Test
    fun `an installed sink receives the signals`() {
        val sink = RecordingSink()
        Telemetry.install(sink)

        assertTrue(Telemetry.isInstalled)
        Telemetry.log("breadcrumb")
        Telemetry.setKey(TelemetryAttribute.CALLING_APP, "com.example.app")
        Telemetry.recordException(IllegalStateException("bang"), "k" to "v")

        assertEquals(listOf("breadcrumb"), sink.logs)
        assertEquals("com.example.app", sink.keys[TelemetryAttribute.CALLING_APP])
        assertEquals(1, sink.exceptions.size)
        assertEquals(mapOf("k" to "v"), sink.exceptions.single().second)
    }

    @Test
    fun `uninstall goes quiet again`() {
        val sink = RecordingSink()
        Telemetry.install(sink)
        Telemetry.uninstall()

        Telemetry.log("after uninstall")

        assertFalse(Telemetry.isInstalled)
        assertTrue(sink.logs.isEmpty())
    }

    @Test
    fun `a sink that throws never reaches the caller`() {
        Telemetry.install(HostileSink)

        Telemetry.log("x")
        Telemetry.setKey("k", "v")
        Telemetry.recordException(IllegalStateException("caller's problem"))
        Telemetry.recordException(IllegalStateException("caller's problem"), "k" to "v")
    }

    @Test
    fun `a throwing sink still yields usable spans`() {
        Telemetry.install(HostileSink)

        val span = Telemetry.startSpan("s")
        assertNotNull(span)
        span.putAttribute("a", "b")
        span.putMetric("m", 1)
        span.stop()

        val network = Telemetry.startNetworkSpan("https://pod.example", "GET")
        assertNotNull(network)
        network.setResponseCode(200)
        network.setRequestPayloadSize(1)
        network.setResponsePayloadSize(2)
        network.setResponseContentType("text/turtle")
        network.putAttribute("a", "b")
        network.stop()
    }

    @Test
    fun `traced marks success and stops the span`() {
        val sink = RecordingSink()
        Telemetry.install(sink)

        val result = traced("work") { 42 }

        assertEquals(42, result)
        val span = sink.spans.getValue("work")
        assertEquals(TelemetryAttribute.OUTCOME_SUCCESS, span.attributes[TelemetryAttribute.OUTCOME])
        assertEquals(1, span.stops)
    }

    @Test
    fun `traced marks failure, reports it and rethrows`() {
        val sink = RecordingSink()
        Telemetry.install(sink)
        val boom = IllegalStateException("boom")

        val thrown = runCatching { traced<Unit>("work") { throw boom } }.exceptionOrNull()

        assertEquals(boom, thrown)
        val span = sink.spans.getValue("work")
        assertEquals(TelemetryAttribute.OUTCOME_ERROR, span.attributes[TelemetryAttribute.OUTCOME])
        assertEquals(1, span.stops)
        assertEquals(boom, sink.exceptions.single().first)
        assertEquals("work", sink.exceptions.single().second[TelemetryAttribute.SPAN])
    }

    @Test
    fun `traced still runs and rethrows when the sink is hostile`() {
        Telemetry.install(HostileSink)

        assertEquals(7, traced("work") { 7 })
        val boom = IllegalStateException("boom")
        assertEquals(boom, runCatching { traced<Unit>("work") { throw boom } }.exceptionOrNull())
    }

    @Test
    fun `the most recently installed sink wins`() {
        val first = RecordingSink()
        val second = RecordingSink()
        Telemetry.install(first)
        Telemetry.install(second)

        Telemetry.log("only second")

        assertTrue(first.logs.isEmpty())
        assertEquals(listOf("only second"), second.logs)
    }
}
