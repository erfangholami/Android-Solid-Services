package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import java.net.URI

/**
 * Backend-agnostic seam for granting / revoking / listing access on a single
 * pod resource. Two implementations are foreseen:
 *
 * - [WacBackend]  — Web Access Control (`Link: rel="acl"` + `acl:Authorization`).
 *   The default; works on every Solid server we target.
 * - [AcpBackend]  — Access Control Policy
 *   (`Link: rel="…/acp#accessControl"` + `acp:Policy`/`acp:Matcher`),
 *   used by pods such as Inrupt ESS running in ACP mode.
 *
 * The right backend for a resource is chosen by [pickBackend] from the
 * resource's `Link` headers.
 *
 * Spec references:
 * - WAC: https://solidproject.org/TR/wac
 * - ACP: https://solidproject.org/TR/acp
 */
internal interface AccessBackend {

    /**
     * Grants [mode] to [receiver] on [resourceUri]. If [isContainer] is true,
     * the authorization also covers descendants (WAC `acl:default`).
     *
     * Replaces any pre-existing authorization for the same
     * `(resource, receiver)` pair and always re-asserts the owner's
     * Read/Write/Control rule so a first write cannot lock the owner out.
     *
     * When [includeImpliedModes] is true (default), WAC expands [mode] to the
     * capability set the sharing UI implies — `APPEND`/`WRITE` also grant
     * `acl:Read` so a recipient can GET what they may add to or overwrite (see
     * `ShareMode.impliedAclModes`). Pass `false` to write **only** the single
     * `acl:mode` for [mode], with no implied Read — required for an LDN inbox,
     * where the public must be able to POST (append) a notification but must
     * not read the inbox's contents. Per Web Access Control, creating a member
     * of a container needs `acl:Append`, which is a subclass of `acl:Write`
     * and does **not** entail `acl:Read`; Linked Data Notifications likewise
     * gates *writing* to the inbox, never reading. ACP already grants only the
     * named mode, so this flag is a no-op for [AcpBackend].
     *
     * Specs: https://solidproject.org/TR/wac and https://www.w3.org/TR/ldn/
     */
    public suspend fun grant(
        webId: String,
        resourceUri: URI,
        mode: ShareMode,
        receiver: ShareReceiver,
        isContainer: Boolean,
        includeImpliedModes: Boolean = true,
    )

    /**
     * Revokes every authorization that targets [receiver] on [resourceUri]
     * (or its descendants, if it's a container). Re-asserts the owner rule.
     *
     * [isContainer] is determined authoritatively by the caller (from the
     * resource's advertised LDP type), so the re-asserted owner rule keeps
     * its `acl:default` on a container and does not strip descendant
     * inheritance.
     */
    public suspend fun revoke(
        webId: String,
        resourceUri: URI,
        receiver: ShareReceiver,
        isContainer: Boolean,
    )

    /**
     * Returns every share-shaped authorization currently on [resourceUri].
     * One [GivenShare] is emitted per matching `(receiver, mode)` pair;
     * the owner's self-rule is filtered out.
     */
    public suspend fun listShares(
        webId: String,
        resourceUri: URI,
    ): List<GivenShare>

    /**
     * Ensures the owner has Read/Write/Control on [targetUri]. For containers,
     * the rule uses `acl:default` so the owner also retains control over all
     * descendants.
     */
    public suspend fun ensureOwnerOnly(
        webId: String,
        targetUri: URI,
        isContainer: Boolean,
    )

    /**
     * Additively re-asserts the owner's Read/Write/Control on [targetUri]'s
     * ACL/ACR, **preserving every other authorization** — unlike
     * [ensureOwnerOnly], which collapses to owner-only. Use to recover from an
     * ACL/ACR edit that accidentally removed the owner's own access.
     *
     * Requires the owner to still hold Control (write access to the ACL/ACR)
     * and the ACL/ACR to be discoverable; if Control is gone or the resource
     * can't be reached, the underlying read/write fails and the exception
     * propagates. For containers the re-asserted rule carries `acl:default`
     * (WAC) / `acp:memberAccessControl` (ACP) so descendants keep inheriting.
     */
    public suspend fun reclaimOwnerControl(
        webId: String,
        targetUri: URI,
        isContainer: Boolean,
    )
}
