package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import java.net.URI

/**
 * Chooses which [AccessBackend] applies to [resourceUri], given metadata already
 * fetched via HEAD.
 *
 * The choice is made from the `Link: rel="acl"` header plus an origin comparison:
 * - an `acl` link on the **same origin** as [resourceUri] -> [WacBackend]. This is
 *   the WAC sidecar pattern (`/foo` -> `/foo.acl`) used by CSS, NSS, and Inrupt ESS
 *   in WAC mode.
 * - an `acl` link on a **different origin**, or no `acl` link at all -> [AcpBackend].
 *   Inrupt ESS in ACP mode advertises the ACR on a separate authorization origin
 *   (e.g. `authorization.inrupt.com/<hash>`); sending WAC-formatted documents there
 *   produces a `404 "ACR requested does not exist"`.
 *
 * Spec references: WAC https://solidproject.org/TR/wac, ACP https://solidproject.org/TR/acp
 */
internal fun pickBackend(
    metadata: SolidMetadata,
    resourceUri: URI,
    wac: WacBackend,
    acp: AcpBackend,
): AccessBackend = if (looksLikeWacSidecar(metadata.aclUri, resourceUri)) wac else acp

private fun looksLikeWacSidecar(aclUri: URI?, resourceUri: URI): Boolean {
    aclUri ?: return false
    return aclUri.scheme.equals(resourceUri.scheme, ignoreCase = true) &&
            aclUri.host.equals(resourceUri.host, ignoreCase = true) &&
            aclUri.port == resourceUri.port
}

/**
 * True when the server's `WAC-Allow` header reports the current caller has
 * `read`+`write`+`control` and the public has no advertised access.
 *
 * Both backends use this to short-circuit `ensureOwnerOnly` — if the resource is
 * already in the state we would write, there is nothing to do. On Inrupt ESS (ACP)
 * this is essential: newly-created resources are owner-only by default and the ACR
 * endpoint refuses to materialize via PUT, so re-asserting the rule produces a 404
 * against `authorization.inrupt.com`.
 */
internal fun isAlreadyOwnerOnly(wacAllow: WacAllow?): Boolean {
    val wa = wacAllow ?: return false
    return wa.canRead() && wa.canWrite() && wa.canControl() && wa.publicModes.isEmpty()
}
