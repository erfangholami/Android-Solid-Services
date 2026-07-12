package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode

/**
 * The outcome of [SolidResourceManager.probeAccess]: what access the current user
 * effectively holds on a resource, read from its `WAC-Allow` response header.
 *
 * Note the third state — "couldn't determine" — is *not* modelled here: it is a
 * [com.erfangholami.androidsolidservices.shared.result.SolidResult.Failure] on the
 * probe (a 401 blip, 5xx, or transport error), so a caller must not mistake it for
 * [Denied]. Only [Accessible] and [Denied] are authoritative answers.
 */
public sealed interface AccessProbe {

    /**
     * The resource is reachable and the user holds [modes] (a subset of
     * View/Add/Edit — i.e. Read/Append/Write). [ownerWebId] is the resource's
     * `solid:owner`, or `null` when the server does not advertise one.
     */
    public data class Accessible(
        val modes: Set<ShareMode>,
        val ownerWebId: String?,
    ) : AccessProbe

    /** The resource exists but access is denied (403), or it does not exist (404). */
    public data object Denied : AccessProbe
}
