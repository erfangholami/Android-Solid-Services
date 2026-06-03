package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.net.Uri
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.SOLID_SHARE_URI_SCHEME
import com.erfangholami.androidsolidservices.shared.util.encodeUriString

/**
 * Encodes and parses `solidshare://` deep links and bare share URLs. Pure string logic, kept out of
 * the manager so it is independently testable.
 */
internal object ShareLinkCodec {

    /** Builds a `solidshare://share?u=…[&o=…]` deep link. */
    fun deepLink(resourceUri: String, ownerWebId: String?): String {
        val base = "$SOLID_SHARE_URI_SCHEME://share?u=" + Uri.encode(resourceUri)
        return if (ownerWebId.isNullOrBlank()) base else base + "&o=" + Uri.encode(ownerWebId)
    }

    /** Parses a `solidshare://` deep link, or `null` if it isn't one / has no resource parameter. */
    fun parse(deepLink: String): ParsedShareLink? {
        if (!deepLink.startsWith("$SOLID_SHARE_URI_SCHEME://")) return null
        val parsed = runCatching { Uri.parse(deepLink) }.getOrNull() ?: return null
        val resourceUri = parsed.getQueryParameter("u") ?: return null
        return ParsedShareLink(
            resourceUri = resourceUri,
            ownerWebId = parsed.getQueryParameter("o")?.takeIf { it.isNotBlank() },
        )
    }

    /** The encoded bare-URL form of [resourceUri]. */
    fun bareUrl(resourceUri: String): String = encodeUriString(resourceUri).toString()
}
