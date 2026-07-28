package com.erfangholami.androidsolidservices.shared.util

import android.util.Log
import java.net.URI

internal const val RDF_PARSING_LOG_TAG: String = "RdfParsing"

internal fun tryParseUri(raw: String, where: String): URI? =
    runCatching { URI.create(raw) }
        .onFailure { t ->
            Log.w(RDF_PARSING_LOG_TAG, "$where: malformed URI '$raw'", t)
        }
        .getOrNull()
