package com.erfangholami.androidsolidservices.shared.model.resource

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun parseIsoInstantMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(raw).toEpochMilli() }
        .getOrNull()
}

internal fun parseHttpDateMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).parse(raw))
            .toEpochMilli()
    }.getOrNull()
}

internal fun statSecondsToMillis(seconds: Long?): Long? = seconds?.let { it * 1000 }
