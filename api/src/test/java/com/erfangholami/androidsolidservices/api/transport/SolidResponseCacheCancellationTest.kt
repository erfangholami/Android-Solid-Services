package com.erfangholami.androidsolidservices.api.transport

import com.erfangholami.androidsolidservices.api.transport.SolidRawResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class SolidResponseCacheCancellationTest {

    private val uri = URI.create("https://pod.example/r")

    private fun key() = SolidResponseCache.Key("alice", "GET", uri.toString(), "application/ld+json")

    private fun raw() = SolidRawResponse(200, Headers.Builder().build(), ByteArray(0), uri)

    @Test
    fun `a cancelled follower throws CancellationException instead of busy-spinning`() = runTest {
        val cache = SolidResponseCache()
        val k = key()
        val leaderGate = CompletableDeferred<Unit>()

        val leader = launch {
            cache.cachedRead(k, ttlMillis = 0, uri = uri) {
                leaderGate.await()
                raw()
            }
        }
        runCurrent()

        var followerError: Throwable? = null
        val follower = launch {
            try {
                cache.cachedRead(k, ttlMillis = 0, uri = uri) { raw() }
            } catch (t: Throwable) {
                followerError = t
            }
        }
        runCurrent()

        follower.cancel()
        runCurrent()

        assertTrue(
            "follower should fail with CancellationException, got $followerError",
            followerError is CancellationException,
        )

        leaderGate.complete(Unit)
        leader.join()
    }
}
