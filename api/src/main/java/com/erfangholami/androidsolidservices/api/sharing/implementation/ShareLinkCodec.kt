package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.net.Uri
import com.erfangholami.androidsolidservices.api.sharing.ShareLinkCodec
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.SOLID_SHARE_URI_SCHEME
import com.erfangholami.androidsolidservices.shared.util.encodeUriString

internal object SolidShareLinkCodec : ShareLinkCodec {

    override fun deepLink(resourceUri: String, ownerWebId: String?): String {
        val base = "$SOLID_SHARE_URI_SCHEME://share?u=" + Uri.encode(resourceUri)
        return if (ownerWebId.isNullOrBlank()) base else base + "&o=" + Uri.encode(ownerWebId)
    }

    override fun parse(deepLink: String): ParsedShareLink? {
        if (!deepLink.startsWith("$SOLID_SHARE_URI_SCHEME://")) return null
        val parsed = runCatching { Uri.parse(deepLink) }.getOrNull() ?: return null
        val resourceUri = parsed.getQueryParameter("u") ?: return null
        return ParsedShareLink(
            resourceUri = resourceUri,
            ownerWebId = parsed.getQueryParameter("o")?.takeIf { it.isNotBlank() },
        )
    }

    override fun bareUrl(resourceUri: String): String = encodeUriString(resourceUri).toString()
}
