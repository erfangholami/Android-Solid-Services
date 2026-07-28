package com.erfangholami.androidsolidservices.shared.util

import com.erfangholami.androidsolidservices.shared.util.IriUtils.canonical
import java.net.URI
import java.net.URISyntaxException

/**
 * Utilities for working with IRIs (Internationalized Resource Identifiers)
 * in the context of the Solid protocol.
 *
 * Java's [URI] class only handles a subset of valid IRIs (ASCII-range). These
 * helpers provide basic normalization and validation aligned with the Solid
 * Protocol's IRI requirements.
 *
 * Spec: https://solidproject.org/TR/protocol — IRI handling
 */
public object IriUtils {

    private val VALID_SCHEMES = setOf("http", "https")

    /**
     * Returns `true` if [iri] is a syntactically valid absolute IRI with an
     * `http` or `https` scheme (the only schemes used in Solid).
     */
    public fun isValid(iri: String): Boolean {
        if (iri.isBlank()) return false
        return try {
            val uri = URI(iri)
            uri.isAbsolute && uri.scheme in VALID_SCHEMES
        } catch (_: URISyntaxException) {
            false
        }
    }

    /**
     * Returns `true` if [iri] identifies an LDP container (URI ends with `/`).
     * Per the Solid Protocol, the `/` character indicates a hierarchical
     * container relationship.
     */
    public fun isContainer(iri: String): Boolean = iri.endsWith("/")

    /**
     * Appends a trailing slash to [iri] if absent, returning a container IRI.
     */
    public fun toContainerIri(iri: String): String =
        if (iri.endsWith("/")) iri else "$iri/"

    /**
     * Strips the trailing slash from [iri] if present.
     */
    public fun stripTrailingSlash(iri: String): String =
        if (iri.endsWith("/")) iri.dropLast(1) else iri

    /**
     * Returns the parent container IRI of [iri], or `null` if [iri] is the
     * storage root (no parent).
     *
     * Examples:
     * - `https://pod.example/foo/bar/baz`  → `https://pod.example/foo/bar/`
     * - `https://pod.example/foo/bar/`     → `https://pod.example/foo/`
     * - `https://pod.example/`             → `null`
     */
    public fun parentContainer(iri: String): String? {
        val normalized = stripTrailingSlash(iri)
        val lastSlash = normalized.lastIndexOf('/')
        if (lastSlash <= normalized.indexOf("//") + 1) return null
        return normalized.substring(0, lastSlash + 1)
    }

    /**
     * Returns the last path segment of [iri] (the resource name),
     * excluding any trailing slash.
     *
     * Examples:
     * - `https://pod.example/foo/bar.ttl`  → `bar.ttl`
     * - `https://pod.example/foo/bar/`     → `bar`
     */
    public fun resourceName(iri: String): String {
        val normalized = stripTrailingSlash(iri)
        return normalized.substringAfterLast('/')
    }

    /**
     * Attempts to parse [iri] as a [URI], returning `null` on failure instead
     * of throwing.
     */
    public fun toUriOrNull(iri: String): URI? =
        runCatching { URI.create(iri) }.getOrNull()

    /**
     * Resolves [reference] against [base], returning the resolved IRI string.
     * Handles relative references such as `/path/to/resource`.
     */
    public fun resolve(base: String, reference: String): String =
        URI.create(base).resolve(reference).toString()

    /**
     * Returns the canonical form of [iri] for equivalence comparison:
     * lower-cases the scheme and host, removes `.`/`..` dot-segments, and
     * normalizes percent-encoding via [URI]. **Path case, the trailing slash,
     * and any fragment are preserved** — a file `x` and a container `x/`, or
     * two different `#fragment`s, are genuinely distinct resources/agents and
     * must NOT be unified. Falls back to the raw string if [iri] doesn't parse.
     */
    public fun canonical(iri: String): String =
        runCatching {
            val u = URI(iri).normalize()
            val scheme = u.scheme?.lowercase()
            val host = u.host?.lowercase()
            if (scheme != null && host != null) {
                URI(scheme, u.userInfo, host, u.port, u.path, u.query, u.fragment).toString()
            } else {
                u.toString()
            }
        }.getOrDefault(iri)

    /**
     * Returns `true` if [a] and [b] denote the same IRI once [canonical]-ized.
     *
     * Use this when comparing WebIDs, agent URIs, or resource URIs that may differ
     * only in scheme/host case, dot-segments, or percent-encoding.
     */
    public fun sameIri(a: String, b: String): Boolean =
        a == b || canonical(a) == canonical(b)

    private const val IRI_FORBIDDEN = "<>\"{}|^`\\"

    /**
     * Escapes [iri] for safe emission inside `<...>` angle brackets in
     * N-Triples / Turtle / N3. The IRIREF production forbids
     * control characters (U+0000–U+0020) and `< > " { } | ^ ` \` unescaped; we
     * replace each with a `\\uXXXX` UCHAR so a hostile or merely unusual IRI
     * can't break out of the term and corrupt the serialized document. Returns
     * the input unchanged when it contains none of those characters.
     */
    public fun escapeIri(iri: String): String {
        if (iri.none { it.code <= 0x20 || it in IRI_FORBIDDEN }) return iri
        return buildString(iri.length + 8) {
            iri.forEach { c ->
                if (c.code <= 0x20 || c in IRI_FORBIDDEN) {
                    append("\\u")
                    append(c.code.toString(16).uppercase().padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
    }
}
