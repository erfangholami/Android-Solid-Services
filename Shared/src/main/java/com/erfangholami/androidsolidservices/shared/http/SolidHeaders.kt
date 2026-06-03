package com.erfangholami.androidsolidservices.shared.http

import kotlinx.serialization.Serializable

/**
 * A case-insensitive, HTTP-client-agnostic view of response headers.
 *
 * Replaces direct exposure of OkHttp's `Headers` in the resource model so that `Shared` — and its
 * consumers (the `client` SDK and third-party apps) — carry no okhttp dependency on their public
 * API. `api` builds this from `okhttp3.Headers.toMultimap()`; the header accessors in
 * `shared.util` (`getETag`, `getWacAllow`, `getAclUri`, …) read from it.
 *
 * Lookups are case-insensitive, matching HTTP header semantics (and OkHttp's `Headers`).
 */
@Serializable
public class SolidHeaders(
    private val raw: Map<String, List<String>> = emptyMap(),
) {
    private val byLowerName: Map<String, List<String>>
        get() = raw.entries
            .groupBy({ it.key.lowercase() }, { it.value })
            .mapValues { (_, lists) -> lists.flatten() }

    /** The last value for [name] (case-insensitive), or `null` if the header is absent. */
    public operator fun get(name: String): String? = byLowerName[name.lowercase()]?.lastOrNull()

    /** Every value for [name] (case-insensitive), in order; empty when absent. */
    public fun values(name: String): List<String> = byLowerName[name.lowercase()] ?: emptyList()

    /** The underlying multimap (original casing preserved). */
    public fun toMultimap(): Map<String, List<String>> = raw

    public companion object {
        public val EMPTY: SolidHeaders = SolidHeaders()
    }
}
