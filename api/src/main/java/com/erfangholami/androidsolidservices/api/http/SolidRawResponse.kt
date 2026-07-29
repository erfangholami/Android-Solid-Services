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

    fun errorDetail(): String {
        val wwwAuth = headers[HTTPHeaderName.WWW_AUTHENTICATE]
        if (wwwAuth != null) return "$statusCode ($wwwAuth)"
        val excerpt = body.lineSequence().firstOrNull()?.take(200)?.trim().orEmpty()
        return if (excerpt.isEmpty()) statusCode.toString() else "$statusCode ($excerpt)"
    }
}
