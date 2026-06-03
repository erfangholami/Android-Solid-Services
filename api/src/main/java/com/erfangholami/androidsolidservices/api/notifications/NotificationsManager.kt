package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationsManagerImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

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
 *  - [sendOffer] / [sendUndo] — used internally by
 *    `SharingManager.createShare` and `revokeShare` so callers don't need
 *    to touch the notifier directly.
 *  - [sendRequest] / [sendReject] — request-to-share flow.
 */
public interface NotificationsManager {

    public companion object {
        public fun getInstance(authenticator: Authenticator): NotificationsManager =
            NotificationsManagerImplementation.getInstance(authenticator)

        public fun getInstance(resourceManager: SolidResourceManager): NotificationsManager =
            NotificationsManagerImplementation.getInstance(resourceManager)
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
    ): SolidNetworkResponse<List<ShareNotification>>

    /**
     * Lists every `interop:AccessRequest` (or legacy `solidshare:AccessRequest`)
     * currently in [webId]'s inbox — requests *from others* asking for access
     * to my resources.
     */
    public suspend fun listRequests(
        webId: String,
    ): SolidNetworkResponse<List<ShareRequest>>

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
    ): SolidNetworkResponse<Int>

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
    ): SolidNetworkResponse<Boolean>

    /**
     * Ensures [webId] has a discoverable, append-enabled LDN inbox so it can
     * send-to and receive share notifications across servers, and returns the
     * inbox URI.
     *
     * If an inbox is already advertised — in the WebID document, via a `HEAD`
     * `Link: rel="…ldp#inbox"`, or in an extended profile linked through
     * `rdfs:seeAlso` / `foaf:isPrimaryTopicOf` — that URI is returned unchanged.
     * Otherwise a `{podRoot}inbox/` container is created, granted public
     * `acl:Append` (write-only on ACP servers like Inrupt ESS; Read is also
     * implied on WAC servers), and advertised by writing `ldp:inbox` into a
     * writable profile document — preferring the storage-side extended profile,
     * since the Inrupt-managed WebID document itself is not writable.
     *
     * Idempotent and best-effort: safe to call on every account activation.
     */
    public suspend fun ensureInbox(webId: String): SolidNetworkResponse<String>

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
    ): SolidNetworkResponse<Unit>

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
    ): SolidNetworkResponse<Unit>

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
    ): SolidNetworkResponse<Unit>

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
    ): SolidNetworkResponse<Unit>

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
    ): SolidNetworkResponse<Unit>
}
