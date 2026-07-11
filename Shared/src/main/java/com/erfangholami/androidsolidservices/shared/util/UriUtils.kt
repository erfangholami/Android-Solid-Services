package com.erfangholami.androidsolidservices.shared.util

import java.net.URI
import java.net.URL

/**
 * Re-encodes [uri] so every hierarchical component is a valid RFC 3986 sequence,
 * without disturbing percent-triplets that are already present.
 *
 * The components are rebuilt from their *raw* (still-encoded) forms: characters that
 * are legal in their component are copied through, valid `%XX` triplets are copied
 * verbatim, and only actually-illegal characters (spaces, non-ASCII, stray `%`) are
 * percent-encoded. This makes the function idempotent and safe for identifiers that
 * legitimately contain encoded reserved characters — a path segment holding `%2F`
 * stays `%2F` instead of collapsing into a real `/`.
 *
 * Opaque URIs (`mailto:`, `urn:`) have no hierarchical components and are returned
 * unchanged, as is [uri] itself if re-encoding fails.
 */
public fun encodeUri(uri: URI): URI {
    return try {
        if (uri.isOpaque) return uri
        val sb = StringBuilder()
        uri.scheme?.let { sb.append(it).append(':') }
        uri.rawAuthority?.let { sb.append("//").append(it) }
        uri.rawPath?.let { sb.append(encodeComponent(it, PATH_EXTRA)) }
        uri.rawQuery?.let { sb.append('?').append(encodeComponent(it, QUERY_FRAGMENT_EXTRA)) }
        uri.rawFragment?.let { sb.append('#').append(encodeComponent(it, QUERY_FRAGMENT_EXTRA)) }
        URI(sb.toString())
    } catch (_: Exception) {
        uri
    }
}

/**
 * Parses [raw] into a [URI], applying percent-encoding to any illegal characters
 * while preserving percent-triplets that are already valid.
 *
 * Tries `encodeUri(URI(raw))` first, then falls back to parsing via [URL] (which
 * tolerates some non-standard inputs such as unencoded spaces), and finally
 * `URI.create(raw)` as a last resort.
 */
public fun encodeUriString(raw: String): URI {
    return try {
        encodeUri(URI(raw))
    } catch (_: Exception) {
        try {
            val url = URL(raw)
            val sb = StringBuilder()
            sb.append(url.protocol).append(':')
            url.authority?.let { sb.append("//").append(it) }
            url.path?.let { sb.append(encodeComponent(it, PATH_EXTRA)) }
            url.query?.let { sb.append('?').append(encodeComponent(it, QUERY_FRAGMENT_EXTRA)) }
            url.ref?.let { sb.append('#').append(encodeComponent(it, QUERY_FRAGMENT_EXTRA)) }
            URI(sb.toString())
        } catch (_: Exception) {
            URI.create(raw)
        }
    }
}

private const val URI_UNRESERVED_PUNCTUATION = "-._~"
private const val URI_SUB_DELIMS = "!$&'()*+,;="
private const val PATH_EXTRA = "/:@"
private const val QUERY_FRAGMENT_EXTRA = "/:@?"

private fun Char.isUriHexDigit(): Boolean =
    this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'

private fun encodeComponent(raw: String, extraAllowed: String): String = buildString {
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        when {
            c == '%' && i + 2 < raw.length && raw[i + 1].isUriHexDigit() && raw[i + 2].isUriHexDigit() -> {
                append(raw, i, i + 3)
                i += 3
            }

            c.code < 0x80 && (
                    c.isLetterOrDigit() ||
                            c in URI_UNRESERVED_PUNCTUATION ||
                            c in URI_SUB_DELIMS ||
                            c in extraAllowed
                    ) -> {
                append(c)
                i++
            }

            else -> {
                val end = if (Character.isHighSurrogate(c) && i + 1 < raw.length &&
                    Character.isLowSurrogate(raw[i + 1])
                ) i + 2 else i + 1
                raw.substring(i, end).toByteArray(Charsets.UTF_8).forEach { byte ->
                    append('%').append("%02X".format(byte.toInt() and 0xFF))
                }
                i = end
            }
        }
    }
}
