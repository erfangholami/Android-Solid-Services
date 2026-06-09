package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerImplementation
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

/**
 * Creates, lists, and revokes shares of pod resources.
 *
 * Sharing is built on Web Access Control (WAC); pods that use Access
 * Control Policy (ACP) are supported through a pluggable access-control
 * backend. The manager keeps a private
 * bookkeeping pair (`given_shares.ttl`, `received_shares.ttl`) under
 * `{podRoot}/solidshare/shares/` so the user can see what they have shared and what
 * they have received without re-walking the pod every time.
 *
 * Notification plumbing (LDN inbox push, listing, accept/reject of
 * incoming requests) lives in
 * [com.erfangholami.androidsolidservices.api.notifications.NotificationsManager].
 * `SharingManager` calls into it internally on `createShare` /
 * `revokeShare`, and exposes high-level accept/reject helpers for share
 * requests because those translate into a share creation or rejection.
 */
public interface SharingManager {

    public companion object {
        public fun getInstance(
            authenticator: Authenticator,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManager =
            SharingManagerImplementation.getInstance(authenticator, profile)

        public fun getInstance(
            resourceManager: SolidResourceManager,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManager =
            SharingManagerImplementation.getInstance(resourceManager, profile)
    }

    /**
     * Returns the locally-tracked given shares (fast — single HTTP read of
     * the index file). Use [refreshGivenShares] to re-validate against the
     * actual ACLs on the pod.
     */
    public suspend fun getStoredGivenShares(
        webId: String,
    ): SolidNetworkResponse<List<GivenShare>>

    /**
     * Re-validates each tracked given share by re-reading the relevant
     * resource ACL. Drops any entries whose ACL no longer grants the receiver
     * the recorded mode and persists the verified list back to the index.
     */
    public suspend fun refreshGivenShares(
        webId: String,
    ): SolidNetworkResponse<List<GivenShare>>

    /**
     * Walks the entire pod tree, reads every resource's ACL, and rebuilds
     * `given_shares.ttl` from scratch. Use this to recover shares that were
     * created on another client, outside this app's index.
     *
     * The walk is **resilient**: a resource that can't be read (for example a
     * 403 because its ACL was deleted, or a transient 5xx) is skipped and the
     * rest of the tree is still scanned. Such a resource's existing index rows
     * are **preserved** — only resources whose live ACL was positively read
     * are reconciled — so a partial walk can't wipe rows for an unobserved
     * region. The returned list is the index's resulting state.
     *
     * **This is expensive.** A pod with N resources costs N HEAD + N (or
     * fewer) ACL GETs. Expose as an explicit user action, not a default
     * refresh. Containers' ACLs cover descendants via `acl:default`, so
     * the walker can skip child resources whose ACL is inherited.
     */
    public suspend fun rebuildGivenIndex(
        webId: String,
    ): SolidNetworkResponse<List<GivenShare>>

    /**
     * Re-asserts the signed-in owner's Read/Write/Control on [resourceUri]'s
     * ACL/ACR **without removing anyone else's access**, to recover from an
     * ACL/ACR edit that accidentally locked the owner out of their own
     * resource.
     *
     * Works only while the owner still holds Control (write access to the
     * ACL/ACR) and the resource's `acl` link is discoverable via HEAD. If the
     * resource is so locked that the app can't even read its metadata, this
     * fails and the lockout must be cleared server-side (the pod provider's
     * tooling). On WAC the re-asserted rule uses `acl:default` for containers;
     * on ACP it adds an owner policy (with `acp:memberAccessControl` for
     * containers).
     */
    public suspend fun repairOwnerControl(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit>

    /**
     * Returns only the given shares affecting [resourceUri] — read directly
     * from that resource's ACL (authoritative, may differ from the index).
     */
    public suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<List<GivenShare>>

    /**
     * Adds (or replaces) an authorization on [resourceUri] granting [mode]
     * to [receiver], and updates the given-shares index.
     *
     * If [resourceUri] is a container, the authorization uses `acl:default`
     * so members inherit access. If a share for the same
     * `(resource, receiver)` pair exists, it is replaced with the new mode.
     *
     * When [notifyReceiver] is true and [receiver] is a WebID, a best-effort
     * `as:Offer` is posted to the receiver's LDN inbox after the share is
     * persisted. Failure to deliver does not fail the share.
     */
    public suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean = true,
    ): SolidNetworkResponse<GivenShare>

    /**
     * Updates the access mode of an existing share — replacing [receiver]'s
     * authorization on [resourceUri] with [mode] (widening or narrowing it) and
     * patching the given-shares index, while preserving the share's original
     * `dcterms:created` time. Equivalent to [createShare] with the new mode.
     *
     * When [notifyReceiver] is true and [receiver] is a WebID, a best-effort
     * `as:Offer` carrying the new mode is posted to the receiver's inbox (as on
     * [createShare]); this also lets the receiver's "shared with me" view sync
     * to the changed level. Defaults to false so a silent re-grant stays silent.
     */
    public suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean = false,
    ): SolidNetworkResponse<GivenShare>

    /**
     * Removes the authorization for [receiver] on [resourceUri] and removes
     * the matching index triple. If [receiver] is a WebID, an `as:Undo` is
     * posted to their inbox (best-effort).
     */
    public suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<Unit>

    /**
     * Returns the locally-tracked received shares (fast). Use
     * [refreshReceivedShares] to re-validate.
     */
    public suspend fun getStoredReceivedShares(
        webId: String,
    ): SolidNetworkResponse<List<ReceivedShare>>

    /**
     * Re-validates each tracked received share via HEAD (uses `WAC-Allow` and
     * `Link rel="solid:owner"`). Stale entries are dropped.
     */
    public suspend fun refreshReceivedShares(
        webId: String,
    ): SolidNetworkResponse<List<ReceivedShare>>

    /**
     * Verifies access to [resourceUri] from the current user's perspective
     * and syncs `received_shares.ttl` accordingly:
     *
     * - if the WAC-Allow header grants any mode, the row is added or updated
     *   and the resulting [ReceivedShare] is returned;
     * - if access is **not** granted, any previously-stored row for this
     *   resource URI is removed and `null` is returned.
     *
     * Called when the user scans a QR code or pastes a share URL. Same call
     * doubles as a "re-verify" — scanning the same QR after the sender has
     * revoked will remove the stale row.
     *
     * [ownerHint] is the sender WebID carried by the share link (see
     * [parseShareDeepLink]); when it is a valid IRI it is trusted ahead of the
     * weaker owner-resolution signals so the stored row names the real sender.
     */
    public suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String? = null,
    ): SolidNetworkResponse<ReceivedShare?>

    /**
     * Removes a tracked received share. Does not affect the resource itself.
     */
    public suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidNetworkResponse<Unit>

    /**
     * Reconciles the user's received-shares index against a batch of share
     * [notifications] already read — and ownership-gated — from their inbox,
     * then returns the updated stored received shares.
     *
     * An `OFFER`/`ACCEPTED` adds or updates the resource (trusting the
     * gate-verified notification's own mode/owner when a live cross-pod access
     * probe is indeterminate); an `UNDO` removes it; `REJECT` and decision
     * records are ignored. Best-effort per item: a single failure is logged and
     * skipped, never failing the call.
     *
     * Pairs with
     * [com.erfangholami.androidsolidservices.api.notifications.NotificationsManager.listNotifications]:
     * the caller lists notifications once for its feed, then hands the same list
     * here to keep the "shared with me" view in sync without a second inbox read.
     */
    public suspend fun syncReceivedShares(
        webId: String,
        notifications: List<ShareNotification>,
    ): SolidNetworkResponse<List<ReceivedShare>>

    /**
     * Returns every access relationship the library can observe for [webId],
     * unified into one list (see [AccessGrant]):
     *
     *  - the shares the user has **given** (from `given_shares.ttl`),
     *  - the shares the user has **received** (from `received_shares.ttl`),
     *  - the incoming access **requests** awaiting the user's decision (from the
     *    LDN inbox), and
     *  - grants discovered via Solid Application Interoperability (SAI)
     *    registries when the pod exposes a `interop:hasRegistrySet`.
     *
     * The app-index sources are authoritative and their read failures surface
     * as an error/exception variant. SAI discovery is **best-effort additive
     * enrichment**: it never fails the call — a pod without SAI support simply
     * contributes no rows. Rows that coincide between an app index and SAI
     * (same direction, counterpart, resource, mode) are de-duplicated in favour
     * of the authoritative app-index row.
     */
    public suspend fun getAccessGrants(
        webId: String,
    ): SolidNetworkResponse<List<AccessGrant>>

    /**
     * Approves a [ShareRequest] previously received in this user's inbox.
     *
     * Equivalent to calling [createShare] with the request's resource,
     * requested mode, and requester WebID — and `notifyReceiver = true`,
     * so the requester gets an `as:Offer` informing them of the grant.
     *
     * Does **not** delete the request from the inbox. The inbox remains the
     * durable record of the decision.
     */
    public suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): SolidNetworkResponse<GivenShare>

    /**
     * Declines a [ShareRequest] by posting an `as:Reject` notification to
     * the requester's inbox, optionally with a [reason] carried as
     * `as:summary`.
     *
     * Does **not** create any share or modify the index. Does not delete
     * the request from this user's inbox.
     */
    public suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String? = null,
    ): SolidNetworkResponse<Unit>

    /**
     * Adds (or replaces) an entry in the owner's public catalog at
     * `{podRoot}/solidshare/catalog.ttl`. The catalog lets potential
     * requesters discover what to ask for; the listed resources stay
     * private until access is granted via [acceptShareRequest].
     *
     * The catalog itself is publicly readable. The entry's contents
     * (title / description / depiction) become public knowledge.
     */
    public suspend fun publishCatalogEntry(
        webId: String,
        entry: CatalogEntry,
    ): SolidNetworkResponse<Unit>

    /**
     * Removes the entry for [resourceUri] from the owner's catalog, if
     * present. Does not affect the resource itself or any share state.
     */
    public suspend fun removeCatalogEntry(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit>

    /**
     * Reads [ownerWebId]'s public catalog from the viewer's perspective.
     * Used by a requester to browse what an owner is willing to entertain
     * requests for.
     *
     * @param viewerWebId WebID of the agent making the request (used for
     *   DPoP-authenticated HTTP).
     * @param ownerWebId  WebID of the catalog's owner.
     */
    public suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): SolidNetworkResponse<List<CatalogEntry>>

    /**
     * Encoding used inside QR codes when the target is a Solid-aware
     * receiver. Returns a `solidshare://` deep-link wrapping the original
     * [resourceUri] so the SolidShare app picks it up directly.
     *
     * For non-Solid scanners (public shares, posters, anything that just
     * opens URLs in a browser), use [getShareBareUrl] instead so the QR
     * decodes to a plain `https://…` link.
     *
     * Pass [ownerWebId] to embed the sender's WebID in the link so the
     * receiver can identify who shared the resource even when adding it via
     * QR / link (the notification path already carries the owner).
     */
    public fun getShareDeepLink(resourceUri: String, ownerWebId: String? = null): String

    /**
     * Extracts the resource URI — and, when present, the embedded owner WebID
     * — from a [getShareDeepLink] string. Returns null when [deepLink] is not
     * a `solidshare://` link.
     */
    public fun parseShareDeepLink(deepLink: String): ParsedShareLink?

    /**
     * Returns the bare `https://…` URL to encode in QR codes for non-Solid
     * receivers and any "open in any browser" path. Equivalent to the
     * resource URI; provided as a named helper so callers can document the
     * choice between the two encodings.
     */
    public fun getShareBareUrl(resourceUri: String): String
}
