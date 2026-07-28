package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import java.net.URI

internal fun pickBackend(
    metadata: SolidMetadata,
    resourceUri: String,
    wac: WacBackend,
    acp: AcpBackend,
): AccessBackend = if (looksLikeWacSidecar(metadata.aclUri, resourceUri)) wac else acp

private fun looksLikeWacSidecar(aclUri: String?, resourceUri: String): Boolean {
    val acl = aclUri?.let { runCatching { URI.create(it) }.getOrNull() } ?: return false
    val resource = runCatching { URI.create(resourceUri) }.getOrNull() ?: return false
    return acl.scheme.equals(resource.scheme, ignoreCase = true) &&
            acl.host.equals(resource.host, ignoreCase = true) &&
            acl.port == resource.port
}

internal fun isAlreadyOwnerOnly(wacAllow: WacAllow?): Boolean {
    val wa = wacAllow ?: return false
    return wa.canRead() && wa.canWrite() && wa.canControl() && wa.publicModes.isEmpty()
}
