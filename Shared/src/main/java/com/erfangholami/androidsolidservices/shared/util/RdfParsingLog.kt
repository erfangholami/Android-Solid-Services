package com.erfangholami.androidsolidservices.shared.util

import android.util.Log
import java.net.URI

internal const val RDF_PARSING_LOG_TAG: String = "RdfParsing"

/**
 * Parse [raw] into a [URI] or return `null`, logging a warning if the
 * value is malformed. Use this everywhere malformed RDF/header values
 * should not crash but ARE a sign of pod-side corruption — without this
 * helper the failures are silent and produce incomplete domain models
 * that callers can't distinguish from "field absent".
 */
internal fun tryParseUri(raw: String, where: String): URI? =
    runCatching { URI.create(raw) }
        .onFailure { t ->
            Log.w(RDF_PARSING_LOG_TAG, "$where: malformed URI '$raw'", t)
        }
        .getOrNull()
