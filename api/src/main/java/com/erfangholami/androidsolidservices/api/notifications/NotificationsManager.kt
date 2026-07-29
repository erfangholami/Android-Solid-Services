package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationsManagerImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * Single entry point for the LDN (Linked Data Notifications) inbox layer
 * used by the sharing feature.
 *
 * This layer is **pull-only**: callers refresh on demand
 * ([listNotifications] / [listRequests]) and either schedule a periodic
 * pull (e.g. WorkManager — Android's effective minimum is 15 minutes) or
 * pull when the user explicitly refreshes. Push delivery via the Solid
 * Notifications Protocol (https://solidproject.org/TR/notifications-protocol)
 * is not used here: sharing notifications are infrequent enough that a
 * periodic pull costs less battery and complexity than a persistent push
 * connection.
 *
 * Outgoing notifications go through this manager too:
 *  - [sendOffer] / [sendUpdate] / [sendUndo] — used internally by
 *    `SharingManager.createShare`, `updateShare`, and `revokeShare` so callers
 *    don't need to touch the notifier directly.
 *  - [sendRequest] / [sendReject] — request-to-share flow.
 */
public interface NotificationsManager {

    public companion object {
        public fun getInstance(
            authenticator: Authenticator,
            profile: ShareNotificationProfile = SolidShareNotificationProfile,
        ): NotificationsManager =
            NotificationsManagerImplementation.getInstance(authenticator, profile)

        public fun getInstance(
            resourceManager: SolidResourceManager,
            profile: ShareNotificationProfile = SolidShareNotificationProfile,
        ): NotificationsManager =
            NotificationsManagerImplementation.getInstance(resourceManager, profile)
    }

    /**
     * Lists every `as:Offer` / `as:Accept` / `as:Undo` / `as:Reject`
     * notification currently in [webId]'s LDN inbox.
     *
     * **Side effect** — `received_shares.ttl` is synced from the inbox
     * contents:
     * - `OFFER` / `ACCEPTED`: if access can be verified via WAC-Allow, the row
     *   is added or updated; otherwise left alone.
     * - `UNDO`: matching `(owner, resource)` row is removed.
     * - `REJECT`: no effect on received_shares; surfaced for the UI only.
     *
     * Inbox items are not deleted by this call; use [deleteNotification] to
     * remove a handled or dismissed item.
     */
    public suspend fun listNotifications(
        webId: String,
    ): SolidResult<List<ShareNotification>>

    /**
     * Lists every `interop:AccessRequest` (or legacy `solidshare:AccessRequest`)
     * currently in [webId]'s inbox — requests *from others* asking for access
     * to my resources.
     */
    public suspend fun listRequests(
        webId: String,
    ): SolidResult<List<ShareRequest>>

    /**
     * Compacts the user's LDN inbox by deleting items that are no longer
     * useful to keep around:
     *
     *  - **Offer/Undo pairs**: when an `as:Undo` cancels a prior
     *    `as:Offer` for the same `(actor, resource)` pair, both items are
     *    deleted. The net effect on `received_shares.ttl` is already
     *    reflected on the next [listNotifications] call.
     *  - **Stale items**: items whose `as:published` is older than
     *    [olderThanIso] (an ISO-8601 instant string) are deleted.
     *
     * Items the library doesn't recognise (e.g. non-SolidShare LDN
     * notifications) are left alone. Items missing `as:published` are
     * never deleted by age — only by pair-cancellation.
     *
     * Returns the number of inbox items deleted.
     */
    public suspend fun compactInbox(
        webId: String,
        olderThanIso: String? = null,
    ): SolidResult<Int>

    /**
     * Deletes a single inbox item by URI. Used to dismiss a notification
     * (mark-as-read) or to clear a handled `AccessRequest` after accept/reject.
     * The pod ACLs and the local share indexes remain the source of truth, so
     * removing the inbox message loses only the message itself, not the access
     * relationship. Returns `true` when the server confirmed deletion.
     */
    public suspend fun deleteNotification(
        webId: String,
        notificationUri: String,
    ): SolidResult<Boolean>

    /**
     * Ensures [webId] has a discoverable, append-enabled LDN inbox so it can
     * send-to and receive share notifications across servers, and returns the
     * inbox URI.
     *
     * If an inbox is already advertised — in the WebID document, via a `HEAD`
     * `Link: rel="…ldp#inbox"`, or in an extended profile linked through
     * `rdfs:seeAlso` / `foaf:isPrimaryTopicOf` — that URI is returned after
     * (best-effort) re-asserting public `acl:Append` on it, so an inbox created
     * before this grant existed (or by another client) is still repaired.
     * Otherwise a `{podRoot}inbox/` container is created and advertised by
     * writing `ldp:inbox` into a writable profile document — preferring the
     * storage-side extended profile, since the Inrupt-managed WebID document
     * itself is not writable.
     *
     * The public grant is **write-only `acl:Append`** on every backend (WAC and
     * ACP alike): per Web Access Control, creating a member of a container
     * requires `acl:Append` (a subclass of `acl:Write`, not entailing
     * `acl:Read`), and Linked Data Notifications gates *writing* to the inbox,
     * not reading — so anyone may POST a notification but no one but the owner
     * can read the inbox. (The grant skips the sharing UI's implied `acl:Read`
     * via `AccessBackend.grant(includeImpliedModes = false)`.)
     * Specs: https://solidproject.org/TR/wac and https://www.w3.org/TR/ldn/
     *
     * Idempotent and best-effort: safe to call on every account activation.
     */
    public suspend fun ensureInbox(webId: String): SolidResult<String>

    /**
     * Posts an `as:Offer` to [receiverWebId]'s inbox. Used by
     * `SharingManager.createShare(notifyReceiver = true)`. Returns
     * `Success(Unit)` on 2xx, an error variant otherwise. Best-effort:
     * `SharingManager` ignores the result and the share succeeds regardless.
     */
    public suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidResult<Unit>

    /**
     * Posts an `as:Undo` to [receiverWebId]'s inbox indicating that access
     * to [resourceUri] has been withdrawn. Note this deviates from strict
     * Activity Streams 2.0: `as:object` carries the resource URI rather
     * than the URI of the prior offer notification.
     */
    public suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): SolidResult<Unit>

    /**
     * Posts an `as:Update` to [receiverWebId]'s inbox indicating that the access
     * level [ownerWebId] grants them on [resourceUri] has changed to [mode]
     * (widened or narrowed). Used by `SharingManager.updateShare(notifyReceiver =
     * true)`. Distinct from [sendOffer] so the receiver sees "your access was
     * updated" rather than a fresh share. Surfaces on the receiver side as
     * [com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType.UPDATED]
     * and is treated like an `as:Offer` for received-share syncing. Best-effort.
     */
    public suspend fun sendUpdate(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidResult<Unit>

    /**
     * Posts a `solidshare:AccessRequest` to [ownerWebId]'s inbox asking
     * for [requestedMode] access on [resourceUri]. The owner sees it via
     * [listRequests] and may
     * [com.erfangholami.androidsolidservices.api.sharing.SharingManager.acceptShareRequest]
     * or
     * [com.erfangholami.androidsolidservices.api.sharing.SharingManager.rejectShareRequest].
     */
    public suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String? = null,
    ): SolidResult<Unit>

    /**
     * Posts an `as:Reject` to [requesterWebId]'s inbox declining their
     * AccessRequest on [resourceUri]. The optional [reason] is carried in
     * `as:summary`.
     */
    public suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String? = null,
    ): SolidResult<Unit>

    /**
     * Posts an `as:Accept` to [requesterWebId]'s inbox confirming their
     * AccessRequest on [resourceUri] was granted [mode] access, linking back to
     * the originating request via `as:inReplyTo` ([requestUri]). Surfaces on the
     * requester side as
     * [com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType.ACCEPTED]
     * and is treated like an `as:Offer` for received-share syncing. Used by
     * `SharingManager.acceptShareRequest`; best-effort like the other sends.
     */
    public suspend fun sendAccept(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode,
        requestUri: String? = null,
    ): SolidResult<Unit>

    /**
     * Posts a read-only record into **[ownerWebId]'s own** inbox noting that
     * they granted [requesterWebId]'s AccessRequest on [resourceUri] with
     * [mode]. This is the owner-side counterpart of [sendAccept] (which notifies
     * the requester): a local memo so the owner's notifications screen can show
     * "you approved …" after the request is cleared. Surfaces back via
     * [listNotifications] as
     * [com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType.DECISION_GRANTED]
     * with no received-share side effect. Used by `SharingManager.acceptShareRequest`;
     * best-effort like the other sends.
     */
    public suspend fun recordDecisionGranted(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode,
        requestUri: String? = null,
    ): SolidResult<Unit>

    /**
     * Posts a read-only record into **[ownerWebId]'s own** inbox noting that
     * they declined [requesterWebId]'s AccessRequest on [resourceUri] (which had
     * asked for [mode]), with an optional [reason]. Owner-side counterpart of
     * [sendReject]. Surfaces back via [listNotifications] as
     * [com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType.DECISION_REJECTED].
     * Used by `SharingManager.rejectShareRequest`; best-effort.
     */
    public suspend fun recordDecisionRejected(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        mode: ShareMode? = null,
        reason: String? = null,
    ): SolidResult<Unit>
}
