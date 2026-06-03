package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.http.SolidRawResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class SolidResponseCacheTest {

    private val uri = URI.create("https://pod.example/r")

    private fun raw(
        status: Int,
        body: String = "",
        etag: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): SolidRawResponse {
        val builder = Headers.Builder()
        if (etag != null) builder.add("ETag", etag)
        extraHeaders.forEach { (name, value) -> builder.add(name, value) }
        return SolidRawResponse(status, builder.build(), body.toByteArray(), uri)
    }

    private fun key(
        principal: String = "alice",
        method: String = "GET",
        url: String = uri.toString(),
        accept: String = "application/ld+json",
    ) = SolidResponseCache.Key(principal, method, url, accept)

    @Test
    fun freshReadServesFromMemoryWithoutNetwork() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)
        val k = key()

        val first = cache.cachedRead(k, ttlMillis = 5_000, uri = uri) {
            fetches.incrementAndGet(); raw(200, "v1", etag = "e1")
        }
        val second = cache.cachedRead(k, ttlMillis = 5_000, uri = uri) {
            fetches.incrementAndGet(); raw(200, "SHOULD-NOT-RUN", etag = "e2")
        }

        assertEquals(1, fetches.get())
        assertEquals("v1", first.body)
        assertEquals("v1", second.body)
    }

    @Test
    fun expiredReadRevalidatesAndServesCachedBodyOn304() = runBlocking {
        var now = 0L
        val cache = SolidResponseCache(clock = { now })
        val k = key()
        var sentConditional: Map<String, String> = emptyMap()

        cache.cachedRead(k, 5_000, uri) { raw(200, "v1", etag = "e1") }

        now = 5_000L * 1_000_000L + 1
        val revalidated = cache.cachedRead(k, 5_000, uri) { cond ->
            sentConditional = cond
            raw(304)
        }

        assertEquals("e1", sentConditional["If-None-Match"])
        assertEquals("v1", revalidated.body)
        assertEquals(200, revalidated.statusCode)
    }

    @Test
    fun changedResourceReplacesCachedBody() = runBlocking {
        var now = 0L
        val cache = SolidResponseCache(clock = { now })
        val k = key()

        cache.cachedRead(k, 5_000, uri) { raw(200, "v1", etag = "e1") }
        now = 5_000L * 1_000_000L + 1
        val updated = cache.cachedRead(k, 5_000, uri) { raw(200, "v2", etag = "e2") }

        assertEquals("v2", updated.body)
        now += 1
        val cached = cache.cachedRead(k, 5_000, uri) { raw(200, "SHOULD-NOT-RUN") }
        assertEquals("v2", cached.body)
    }

    @Test
    fun invalidateForcesRefetch() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)
        val k = key()

        cache.cachedRead(k, 5_000, uri) { fetches.incrementAndGet(); raw(200, "v1", etag = "e1") }
        cache.invalidate(k.url)
        cache.cachedRead(k, 5_000, uri) { fetches.incrementAndGet(); raw(200, "v1", etag = "e1") }

        assertEquals(2, fetches.get())
    }

    @Test
    fun invalidateWithParentDropsContainerListing() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val container = "https://pod.example/a/"
        val child = "https://pod.example/a/b.ttl"
        val containerKey = key(url = container)
        val childKey = key(url = child)

        cache.cachedRead(containerKey, 5_000, URI.create(container)) { raw(200, "listing", etag = "c1") }
        cache.cachedRead(childKey, 5_000, URI.create(child)) { raw(200, "child", etag = "k1") }
        assertEquals(2, cache.size())

        cache.invalidateWithParent(child)
        assertEquals(0, cache.size())
    }

    @Test
    fun differentPrincipalsDoNotShareEntries() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)

        cache.cachedRead(key(principal = "alice"), 5_000, uri) { fetches.incrementAndGet(); raw(200, "a", etag = "e1") }
        cache.cachedRead(key(principal = "bob"), 5_000, uri) { fetches.incrementAndGet(); raw(200, "b", etag = "e1") }

        assertEquals(2, fetches.get())
    }

    @Test
    fun lruEvictsEldestWhenOverCount() = runBlocking {
        val cache = SolidResponseCache(maxEntries = 2, clock = { 0L })
        cache.cachedRead(key(url = "https://pod.example/1"), 5_000, uri) { raw(200, "1", etag = "1") }
        cache.cachedRead(key(url = "https://pod.example/2"), 5_000, uri) { raw(200, "2", etag = "2") }
        cache.cachedRead(key(url = "https://pod.example/3"), 5_000, uri) { raw(200, "3", etag = "3") }

        assertEquals(2, cache.size())
        val fetchedAgain = AtomicInteger(0)
        cache.cachedRead(key(url = "https://pod.example/1"), 5_000, uri) {
            fetchedAgain.incrementAndGet(); raw(200, "1", etag = "1")
        }
        assertEquals(1, fetchedAgain.get())
    }

    @Test
    fun noStoreResponsesAreNotCached() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)
        val k = key()

        cache.cachedRead(k, 5_000, uri) {
            fetches.incrementAndGet()
            raw(200, "v1", etag = "e1", extraHeaders = mapOf("Cache-Control" to "no-store"))
        }
        cache.cachedRead(k, 5_000, uri) {
            fetches.incrementAndGet(); raw(200, "v1", etag = "e1")
        }

        assertEquals(2, fetches.get())
    }

    @Test
    fun oversizeBodiesAreNotCached() = runBlocking {
        val cache = SolidResponseCache(maxEntryBytes = 8, clock = { 0L })
        val fetches = AtomicInteger(0)
        val k = key()

        cache.cachedRead(k, 5_000, uri) { fetches.incrementAndGet(); raw(200, "0123456789", etag = "e1") }
        cache.cachedRead(k, 5_000, uri) { fetches.incrementAndGet(); raw(200, "0123456789", etag = "e1") }

        assertEquals(2, fetches.get())
        assertEquals(0, cache.size())
    }

    @Test
    fun errorResponsesAreNotCached() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val k = key()
        val first = cache.cachedRead(k, 5_000, uri) { raw(403, "forbidden") }
        assertEquals(403, first.statusCode)
        assertEquals(0, cache.size())
    }

    @Test
    fun concurrentIdenticalReadsAreCoalescedIntoOneFetch() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)
        val k = key()
        val leaderEntered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        val leader: Deferred<SolidRawResponse> = async {
            cache.cachedRead(k, 5_000, uri) {
                fetches.incrementAndGet()
                leaderEntered.complete(Unit)
                gate.await()
                raw(200, "v1", etag = "e1")
            }
        }

        leaderEntered.await()
        val followers = (1..9).map {
            async {
                cache.cachedRead(k, 5_000, uri) {
                    fetches.incrementAndGet(); raw(200, "FOLLOWER", etag = "e9")
                }
            }
        }
        gate.complete(Unit)

        val results = (followers + leader).awaitAll()
        assertEquals(1, fetches.get())
        assertTrue(results.all { it.body == "v1" })
    }

    @Test
    fun disabledTtlNeverServesStale() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)
        val k = key()

        cache.cachedRead(k, ttlMillis = 0, uri = uri) { fetches.incrementAndGet(); raw(200, "v1", etag = "e1") }
        cache.cachedRead(k, ttlMillis = 0, uri = uri) { cond ->
            fetches.incrementAndGet()
            assertEquals("e1", cond["If-None-Match"])
            raw(304)
        }

        assertEquals(2, fetches.get())
    }

    @Test
    fun headAndGetUseSeparateEntries() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        val fetches = AtomicInteger(0)

        cache.cachedRead(key(method = "GET"), 5_000, uri) { fetches.incrementAndGet(); raw(200, "body", etag = "e1") }
        cache.cachedRead(key(method = "HEAD", accept = ""), 5_000, uri) { fetches.incrementAndGet(); raw(200, "", etag = "e1") }

        assertEquals(2, fetches.get())
        assertFalse(cache.size() == 1)
    }

    @Test
    fun clearEmptiesCache() = runBlocking {
        val cache = SolidResponseCache(clock = { 0L })
        cache.cachedRead(key(), 5_000, uri) { raw(200, "v1", etag = "e1") }
        assertEquals(1, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(null)
    }
}
