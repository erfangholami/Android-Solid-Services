package com.erfangholami.androidsolidservices.api.http

import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import okhttp3.Headers
import java.net.URI

internal class SolidRawResponse(
    val statusCode: Int,
    val headers: Headers,
    val bodyBytes: ByteArray,
    val uri: URI,
) {
    val body: String by lazy { bodyBytes.toString(Charsets.UTF_8) }
    fun isSuccessful(): Boolean = statusCode in 200..299

    /**
     * Returns a short diagnostic string suitable for an error message: status
     * code plus the most useful response-side context — `WWW-Authenticate`
     * when the server emitted a challenge (401s) and an excerpt of the body
     * otherwise. HEAD responses have no body, so the challenge header is the
     * only signal that lets a caller distinguish `invalid_token` from
     * `use_dpop_nonce` from a missing-resource 401.
     */
    fun errorDetail(): String {
        val wwwAuth = headers[HTTPHeaderName.WWW_AUTHENTICATE]
        if (wwwAuth != null) return "$statusCode ($wwwAuth)"
        val excerpt = body.lineSequence().firstOrNull()?.take(200)?.trim().orEmpty()
        return if (excerpt.isEmpty()) statusCode.toString() else "$statusCode ($excerpt)"
    }
}