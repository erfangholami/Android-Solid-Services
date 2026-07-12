package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.util.getLastModified
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val DEFAULT_CAS_ATTEMPTS = 4
private const val CAS_BACKOFF_MS = 60L
private const val CAS_JITTER_MS = 120L

/**
 * Compare-and-swap update for a read-modify-write on an RDF resource.
 *
 * Reads a fresh copy via [read], applies [mutate] in place, and writes it back with
 * a conditional `If-Match` on the copy's ETag. If a concurrent writer landed first
 * (HTTP 412 → [SolidError.PreconditionFailed]), it re-reads and retries up to
 * [maxAttempts] times with jittered backoff — so a naive read-modify-write can no
 * longer silently clobber the other writer's change (last-write-wins). The 412 also
 * invalidates the response cache, so each retry's [read] observes the latest state.
 *
 * [mutate] returns `false` when the resource already satisfies the intent (nothing to
 * write); the CAS then succeeds without a network round-trip. The precondition prefers
 * a strong `If-Match` ETag; when the server only emits a weak ETag (e.g. Node Solid
 * Server) it falls back to `If-Unmodified-Since` on the resource's `Last-Modified`
 * (one-second granularity); with neither validator the write is unconditional.
 *
 * @param read fetches a fresh copy each attempt (its validators drive the precondition).
 * @param mutate mutates the copy in place; returns `true` to write, `false` to skip.
 */
internal suspend fun <T : Resource> SolidResourceManager.casUpdate(
    webId: String,
    maxAttempts: Int = DEFAULT_CAS_ATTEMPTS,
    read: suspend () -> SolidResult<T>,
    mutate: (T) -> Boolean,
): SolidResult<T> {
    var attempt = 1
    while (true) {
        val resource = when (val r = read()) {
            is SolidResult.Success -> r.value
            is SolidResult.Failure -> return r
        }
        if (!mutate(resource)) return SolidResult.Success(resource)
        val headers = resource.getHeaders()
        val ifMatch = headers.getETag()
        val ifUnmodifiedSince = if (ifMatch == null) headers.getLastModified() else null
        when (val write = update(webId, resource, ifMatch = ifMatch, ifUnmodifiedSince = ifUnmodifiedSince)) {
            is SolidResult.Success -> return write
            is SolidResult.Failure -> {
                val retryable = write.error.code == SolidErrorCode.PRECONDITION_FAILED &&
                    attempt < maxAttempts
                if (!retryable) return write
                attempt++
                delay(CAS_BACKOFF_MS * attempt + Random.nextLong(CAS_JITTER_MS))
            }
        }
    }
}
