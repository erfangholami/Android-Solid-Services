package com.erfangholami.androidsolidservices.api.exceptions

/**
 * Library-side exception types for the sharing + notifications pipeline.
 *
 * Each variant captures the data the caller needs to act:
 *  - [AccessDenied] — the user doesn't have access to a resource. UI
 *    cue: offer to send a `solidshare:AccessRequest`.
 *  - [NoInbox] — the target agent's WebID profile doesn't advertise an
 *    `ldp:inbox` (and no HEAD-link fallback worked). UI cue: notification
 *    won't be delivered; tell the user the receiver isn't reachable.
 *  - [InboxUnauthorized] / [InboxForbidden] — the inbox responded 401 /
 *    403. UI cue: the sender's auth is missing or insufficient; for 403,
 *    contact the receiver to relax inbox ACL.
 *  - [NotificationDelivery] — generic non-2xx from the inbox POST that
 *    isn't auth-related. UI cue: retry / log diagnostic.
 *  - [ImpersonationDetected] — a notification's `as:actor` doesn't match
 *    the resource owner. UI cue: silently drop or quarantine; useful for
 *    debugging.
 *  - [StaleAcl] — a conditional PATCH/PUT to the ACL was rejected with
 *    412. UI cue: re-read the ACL and retry the operation.
 *  - [UnsupportedAuthBackend] — the resource lives on a pod whose auth
 *    backend isn't supported (today: pure-ACP). UI cue: inform the user
 *    the server isn't compatible.
 *
 * These exceptions are thrown by `SharingManager` / `NotificationsManager`
 * implementations and surfaced as a typed `SolidResult.Failure(SolidError…)` at
 * the public boundary. The bound services map them to
 * [com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode]
 * for IPC transport; the client SDK reconstructs the typed exception.
 */
public sealed class SharingException(message: String) : Exception(message) {

    public class AccessDenied(
        public val resourceUri: String,
        public val ownerWebId: String? = null,
    ) : SharingException(
        buildString {
            append("No access to ")
            append(resourceUri)
            append('.')
            if (ownerWebId != null) {
                append(" Owner: ")
                append(ownerWebId)
                append('.')
            }
            append(" Consider sending an access request.")
        },
    )

    public class NoInbox(
        public val targetWebId: String,
    ) : SharingException(
        "No ldp:inbox advertised for $targetWebId — notification cannot be delivered.",
    )

    public class InboxUnauthorized(
        public val inboxUri: String,
    ) : SharingException(
        "Inbox at $inboxUri requires authentication (401).",
    )

    public class InboxForbidden(
        public val inboxUri: String,
    ) : SharingException(
        "Inbox at $inboxUri forbids this operation (403).",
    )

    public class NotificationDelivery(
        public val inboxUri: String,
        public val statusCode: Int?,
    ) : SharingException(
        "Notification delivery to $inboxUri failed${statusCode?.let { " (HTTP $it)" } ?: ""}.",
    )

    public class ImpersonationDetected(
        public val notificationUri: String,
        public val claimedActor: String,
        public val actualOwner: String?,
    ) : SharingException(
        "Notification $notificationUri claims actor=$claimedActor but " +
                "resource owner is ${actualOwner ?: "unknown"} — dropped.",
    )

    public class StaleAcl(
        public val aclUri: String,
    ) : SharingException(
        "ACL at $aclUri was modified concurrently (412 Precondition Failed).",
    )

    public class UnsupportedAuthBackend(
        public val resourceUri: String,
        public val backend: String,
    ) : SharingException(
        "Auth backend $backend not supported for $resourceUri.",
    )

    /**
     * Access to a resource could not be *authoritatively determined* — a
     * transient signal (401 token-refresh, 5xx, transport error) prevented a
     * conclusive Granted/Denied answer, and the caller has no prior record of
     * the share to fall back on. Distinct from [AccessDenied] (a definitive
     * "no"): UI cue is "couldn't verify right now — try again", NOT "you don't
     * have access".
     */
    public class AccessIndeterminate(
        public val resourceUri: String,
    ) : SharingException(
        "Could not verify access to $resourceUri right now; please try again.",
    )

    /**
     * Indicates that a pod walk backing `rebuildGivenIndex` could not be
     * completed because an ACL/container read failed and part of the pod
     * was not observed.
     *
     * `rebuildGivenIndex` does not throw this: it skips unreadable
     * resources, preserves their stored index rows, and rebuilds from
     * whatever it could read. This type is retained only for binary
     * compatibility.
     */
    public class IncompleteScan(
        public val rootUri: String,
    ) : SharingException(
        "Pod scan under $rootUri was incomplete; aborting index rebuild to avoid data loss.",
    )
}
