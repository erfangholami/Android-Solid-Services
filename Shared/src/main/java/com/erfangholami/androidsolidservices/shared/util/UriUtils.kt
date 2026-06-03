package com.erfangholami.androidsolidservices.shared.util

import java.net.URI
import java.net.URL

/**
 * Re-encodes [uri] by round-tripping through the multi-argument [URI] constructor,
 * which percent-encodes any characters that are illegal in the relevant URI component.
 * Returns [uri] unchanged if re-encoding fails.
 */
public fun encodeUri(uri: URI): URI {
    return try {
        URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment,
        )
    } catch (_: Exception) {
        uri
    }
}

/**
 * Parses [raw] into a [URI], applying percent-encoding to any illegal characters.
 *
 * Tries `encodeUri(URI(raw))` first, then falls back to parsing via [URL] (which
 * handles some non-standard inputs), and finally `URI.create(raw)` as a last resort.
 */
public fun encodeUriString(raw: String): URI {
    return try {
        encodeUri(URI(raw))
    } catch (_: Exception) {
        try {
            val url = URL(raw)
            URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
        } catch (_: Exception) {
            URI.create(raw)
        }
    }
}