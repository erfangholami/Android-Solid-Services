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
    resourceUri: String,
    wac: WacBackend,
    acp: AcpBackend,
): AccessBackend = if (looksLikeWacSidecar(metadata.aclUri, resourceUri)) wac else acp

/**
 * Both IRIs are parsed here — this is an origin comparison (scheme / host / port), one of the few
 * places that genuinely needs a [URI] rather than the identifier string. An IRI that doesn't parse
 * can't be shown to share an origin with the other, so it is not treated as a WAC sidecar.
 */
private fun looksLikeWacSidecar(aclUri: String?, resourceUri: String): Boolean {
    val acl = aclUri?.let { runCatching { URI.create(it) }.getOrNull() } ?: return false
    val resource = runCatching { URI.create(resourceUri) }.getOrNull() ?: return false
    return acl.scheme.equals(resource.scheme, ignoreCase = true) &&
            acl.host.equals(resource.host, ignoreCase = true) &&
            acl.port == resource.port
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
