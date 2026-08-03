package com.erfangholami.androidsolidservices.shared.util

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The current instant as an ISO-8601 UTC string truncated to seconds — the form every
 * `dcterms:created` / `dcterms:modified` and `as:published` literal this library writes takes,
 * so timestamps compare as strings and round-trip through RDF unchanged.
 */
public fun nowIsoDateTime(): String =
    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
