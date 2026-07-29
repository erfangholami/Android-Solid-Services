package com.erfangholami.androidsolidservices.api.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.URI

class TelemetryOriginTest {

    @Test
    fun `the container path is dropped`() {
        assertEquals(
            "https://alice.pod.example",
            URI("https://alice.pod.example/private/health/notes.ttl").telemetryOrigin(),
        )
    }

    @Test
    fun `the query is dropped`() {
        assertEquals(
            "https://pod.example",
            URI("https://pod.example/inbox/?since=2026-01-01&token=secret").telemetryOrigin(),
        )
    }

    @Test
    fun `the fragment is dropped`() {
        assertEquals(
            "https://pod.example",
            URI("https://pod.example/profile/card#me").telemetryOrigin(),
        )
    }

    @Test
    fun `userinfo never survives`() {
        val origin = URI("https://alice:secret@pod.example/private/x").telemetryOrigin()
        assertEquals("https://pod.example", origin)
        assertFalse(origin.contains("alice"))
        assertFalse(origin.contains("secret"))
    }

    @Test
    fun `a non-default port is kept so providers stay distinguishable`() {
        assertEquals(
            "https://pod.example:8443",
            URI("https://pod.example:8443/storage/x.ttl").telemetryOrigin(),
        )
    }

    @Test
    fun `scheme and host are lowercased so one provider is one bucket`() {
        assertEquals(
            "https://pod.example",
            URI("HTTPS://POD.EXAMPLE/Storage/X.ttl").telemetryOrigin(),
        )
    }

    @Test
    fun `a hostless uri degrades to unknown rather than leaking the rest`() {
        val origin = URI("mailto:alice@pod.example").telemetryOrigin()
        assertEquals("mailto://unknown", origin)
        assertFalse(origin.contains("alice"))
    }

    @Test
    fun `no path segment of a deep pod uri appears anywhere in the output`() {
        val origin = URI("https://pod.example/private/2026/health/cardiology/report.ttl").telemetryOrigin()
        listOf("private", "2026", "health", "cardiology", "report").forEach {
            assertFalse("segment '$it' leaked into '$origin'", origin.contains(it))
        }
    }
}
