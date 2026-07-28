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
