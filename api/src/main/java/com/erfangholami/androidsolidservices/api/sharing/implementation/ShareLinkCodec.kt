package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.net.Uri
import com.erfangholami.androidsolidservices.api.sharing.ShareLinkCodec
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.SOLID_SHARE_URI_SCHEME
import com.erfangholami.androidsolidservices.shared.util.encodeUriString

internal object SolidShareLinkCodec : ShareLinkCodec {

    private const val HTTPS_LINK_BASE = "https://solidshare.app/s"
    private const val PARAM_RESOURCE = "resource"
    private const val PARAM_OWNER = "owner"

    override fun deepLink(resourceUri: String, ownerWebId: String?): String {
        val base = "$HTTPS_LINK_BASE?$PARAM_RESOURCE=" + Uri.encode(resourceUri)
        return if (ownerWebId.isNullOrBlank()) base else "$base&$PARAM_OWNER=" + Uri.encode(ownerWebId)
    }

    override fun parse(deepLink: String): ParsedShareLink? {
        val recognized = deepLink.startsWith(HTTPS_LINK_BASE) ||
                deepLink.startsWith("$SOLID_SHARE_URI_SCHEME://")
        if (!recognized) return null
        val parsed = runCatching { Uri.parse(deepLink) }.getOrNull() ?: return null
        val resourceUri = parsed.getQueryParameter(PARAM_RESOURCE) ?: return null
        return ParsedShareLink(
            resourceUri = resourceUri,
            ownerWebId = parsed.getQueryParameter(PARAM_OWNER)?.takeIf { it.isNotBlank() },
        )
    }

    override fun bareUrl(resourceUri: String): String = encodeUriString(resourceUri).toString()
}
