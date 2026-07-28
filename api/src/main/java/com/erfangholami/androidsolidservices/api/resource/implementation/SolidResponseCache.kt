package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.http.SolidRawResponse
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Headers
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

internal class SolidResponseCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val clock: () -> Long = System::nanoTime,
) {

    internal data class Key(
        val principal: String,
        val method: String,
        val url: String,
        val accept: String,
    )

    internal class Entry(
        val statusCode: Int,
        val headers: Headers,
        val body: ByteArray,
        val etag: String?,
        val lastModified: String?,
        @Volatile var storedAtNanos: Long,
    ) {
        val byteSize: Long get() = body.size.toLong() + APPROX_ENTRY_OVERHEAD
        fun toRawResponse(uri: URI): SolidRawResponse =
            SolidRawResponse(statusCode, headers, body, uri)

        companion object {
            fun from(response: SolidRawResponse, now: Long): Entry =
                Entry(
                    statusCode = response.statusCode,
                    headers = response.headers,
                    body = response.bodyBytes,
                    etag = response.headers[HTTPHeaderName.ETAG],
                    lastModified = response.headers[HTTPHeaderName.LAST_MODIFIED],
                    storedAtNanos = now,
                )
        }
    }

    private val lock = Any()
    private val store = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private var currentBytes = 0L

    private val inFlight = ConcurrentHashMap<Key, CompletableDeferred<SolidRawResponse>>()

    suspend fun cachedRead(
        key: Key,
        ttlMillis: Long,
        uri: URI,
        fetch: suspend (conditionalHeaders: Map<String, String>) -> SolidRawResponse,
    ): SolidRawResponse {
        peekFresh(key, ttlMillis)?.let { return it.toRawResponse(uri) }
        return singleFlight(key) {
            val stillFresh = peekFresh(key, ttlMillis)
            if (stillFresh != null) {
                stillFresh.toRawResponse(uri)
            } else {
                val existing = peek(key)
                val response = fetch(conditionalHeaders(existing))
                handleResponse(key, existing, response, uri)
            }
        }
    }

    fun invalidate(url: String) {
        synchronized(lock) {
            val iterator = store.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.url == url) {
                    currentBytes -= entry.value.byteSize
                    iterator.remove()
                }
            }
        }
    }

    fun invalidateWithParent(url: String) {
        invalidate(url)
        parentContainer(url)?.let { invalidate(it) }
    }

    fun clear() {
        synchronized(lock) {
            store.clear()
            currentBytes = 0L
        }
    }

    internal fun size(): Int = synchronized(lock) { store.size }

    private fun handleResponse(
        key: Key,
        existing: Entry?,
        response: SolidRawResponse,
        uri: URI,
    ): SolidRawResponse {
        if (response.statusCode == NOT_MODIFIED && existing != null) {
            revalidated(key, existing)
            return existing.toRawResponse(uri)
        }
        if (response.statusCode == OK && isCacheable(key.method, response)) {
            put(key, Entry.from(response, clock()))
            return response
        }
        if (response.statusCode == NOT_FOUND || response.statusCode == GONE) {
            remove(key)
        }
        return response
    }

    private fun conditionalHeaders(existing: Entry?): Map<String, String> {
        if (existing == null) return emptyMap()
        existing.etag?.let { return mapOf(HTTPHeaderName.IF_NONE_MATCH to it) }
        existing.lastModified?.let { return mapOf(HTTPHeaderName.IF_MODIFIED_SINCE to it) }
        return emptyMap()
    }

    private fun isCacheable(method: String, response: SolidRawResponse): Boolean {
        if (method != "GET" && method != "HEAD") return false
        if (response.statusCode != OK) return false
        val cacheControl = response.headers[HEADER_CACHE_CONTROL]?.lowercase().orEmpty()
        if (cacheControl.contains("no-store")) return false
        val vary = response.headers[HTTPHeaderName.VARY]
        if (vary != null && vary.split(",").any { it.trim() == "*" }) return false
        if (method == "GET" && response.bodyBytes.size > maxEntryBytes) return false
        return true
    }

    private fun peek(key: Key): Entry? = synchronized(lock) { store[key] }

    private fun peekFresh(key: Key, ttlMillis: Long): Entry? = synchronized(lock) {
        val entry = store[key] ?: return null
        if (isFresh(entry, ttlMillis)) entry else null
    }

    private fun isFresh(entry: Entry, ttlMillis: Long): Boolean =
        ttlMillis > 0L && (clock() - entry.storedAtNanos) < ttlMillis * NANOS_PER_MILLI

    private fun put(key: Key, entry: Entry) = synchronized(lock) {
        val previous = store.put(key, entry)
        if (previous != null) currentBytes -= previous.byteSize
        currentBytes += entry.byteSize
        evictIfNeeded()
    }

    private fun revalidated(key: Key, existing: Entry) = synchronized(lock) {
        existing.storedAtNanos = clock()
        store[key]
    }

    private fun remove(key: Key) = synchronized(lock) {
        store.remove(key)?.let { currentBytes -= it.byteSize }
    }

    private fun evictIfNeeded() {
        val iterator = store.entries.iterator()
        while ((store.size > maxEntries || currentBytes > maxBytes) && iterator.hasNext()) {
            val eldest = iterator.next()
            currentBytes -= eldest.value.byteSize
            iterator.remove()
        }
    }

    private suspend fun singleFlight(
        key: Key,
        block: suspend () -> SolidRawResponse,
    ): SolidRawResponse {
        while (true) {
            val mine = CompletableDeferred<SolidRawResponse>()
            val leader = inFlight.putIfAbsent(key, mine)
            if (leader == null) {
                try {
                    val result = block()
                    mine.complete(result)
                    return result
                } catch (throwable: Throwable) {
                    mine.completeExceptionally(throwable)
                    throw throwable
                } finally {
                    inFlight.remove(key, mine)
                }
            }
            try {
                return leader.await()
            } catch (cancellation: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (_: Throwable) {
            }
        }
    }

    private fun parentContainer(url: String): String? {
        val uri = runCatching { URI.create(url) }.getOrNull() ?: return null
        val path = uri.path ?: return null
        if (path.isEmpty() || path == "/") return null
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash < 0) return null
        val parentPath = trimmed.substring(0, lastSlash + 1)
        return runCatching {
            URI(uri.scheme, uri.authority, parentPath, null, null).toString()
        }.getOrNull()
    }

    internal companion object {
        const val PUBLIC_PRINCIPAL: String = "<public>"

        private const val OK = 200
        private const val NOT_MODIFIED = 304
        private const val NOT_FOUND = 404
        private const val GONE = 410

        private const val HEADER_CACHE_CONTROL = "Cache-Control"
        private const val NANOS_PER_MILLI = 1_000_000L

        private const val DEFAULT_MAX_ENTRIES = 256
        private const val DEFAULT_MAX_BYTES = 16L * 1024 * 1024
        private const val DEFAULT_MAX_ENTRY_BYTES = 2L * 1024 * 1024
        private const val APPROX_ENTRY_OVERHEAD = 512L
    }
}
