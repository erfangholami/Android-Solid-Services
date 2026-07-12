package com.erfangholami.androidsolidservices.shared.http

/**
 * An HTTP entity tag (`ETag`) and its strength (RFC 7232 §2.3).
 *
 * The [value] is the opaque validator with its surrounding quotes stripped; [weak]
 * is `true` for a weak validator (the header carried the `W/` prefix). The
 * distinction matters for conditional writes: `If-Match` uses the *strong*
 * comparison function, so a weak validator can never satisfy it — sending a weak
 * tag as `If-Match` guarantees a `412`. Some Solid servers (notably Node Solid
 * Server / solidcommunity.net) only ever emit weak ETags for RDF resources, which
 * is why the library falls back to `If-Unmodified-Since` for those writes rather
 * than sending a validator that is doomed to fail.
 */
public data class EntityTag(
    val value: String,
    val weak: Boolean,
) {
    public companion object {
        /**
         * Parses a raw `ETag` header value into an [EntityTag], or returns `null`
         * when [raw] is `null` or blank. A leading `W/` (case-insensitive) marks a
         * weak validator; surrounding double quotes are stripped from the value.
         */
        public fun parse(raw: String?): EntityTag? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val weak = trimmed.startsWith("W/", ignoreCase = true)
            val bare = (if (weak) trimmed.substring(2) else trimmed).trim().removeSurrounding("\"")
            return EntityTag(bare, weak)
        }
    }
}
