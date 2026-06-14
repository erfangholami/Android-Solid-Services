package com.erfangholami.androidsolidservices.shared.model.resource

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Parses an ISO-8601 datetime — as carried by `dcterms:created` / `dcterms:modified` — into
 * epoch milliseconds, or `null` when [raw] is absent or cannot be parsed.
 */
internal fun parseIsoInstantMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(raw).toEpochMilli() }
        .getOrNull()
}

/**
 * Parses an RFC 1123 HTTP date — as carried by the `Last-Modified` header — into epoch
 * milliseconds, or `null` when [raw] is absent or cannot be parsed.
 */
internal fun parseHttpDateMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).parse(raw))
            .toEpochMilli()
    }.getOrNull()
}

/**
 * Converts a POSIX `stat` timestamp in seconds — as carried by `stat:mtime` / `stat:ctime` —
 * into epoch milliseconds, or `null` when [seconds] is `null`.
 */
internal fun statSecondsToMillis(seconds: Long?): Long? = seconds?.let { it * 1000 }
