package com.erfangholami.androidsolidservices.shared.model.sharing

/**
 * Kind of share notification carried by the LDN inbox.
 *
 * Maps to Activity Streams 2 / SolidShare activity types:
 * - [OFFER]    → `as:Offer`       — owner is granting access to me
 * - [ACCEPTED] → `as:Accept`      — owner has granted an AccessRequest I sent
 * - [UNDO]     → `as:Undo`        — owner has retracted access they gave me
 * - [REJECT]   → `as:Reject`      — owner has declined an AccessRequest I sent
 * - [DECISION_GRANTED]  → `as:Accept` **I authored** — my own record of having
 *   granted someone else's AccessRequest
 * - [DECISION_REJECTED] → `as:Reject` **I authored** — my own record of having
 *   declined someone else's AccessRequest
 *
 * The two `DECISION_*` variants are the inbox owner's own read-only memo of a
 * decision they made on an incoming request. They are the *same* `as:Accept` /
 * `as:Reject` activity the owner sends the requester (see
 * `NotificationsManager.sendAccept` / `sendReject`), mirrored into the owner's
 * own inbox. The reader tells them apart from a genuine incoming accept/reject
 * purely by whose inbox they sit in: an accept/reject whose `as:actor` is the
 * inbox owner is a self-authored decision; otherwise it came from a
 * counterpart. They carry no received-share side effect.
 */
public enum class ShareNotificationType {
    /** Sender is offering access. */
    OFFER,

    /** Sender previously offered access and has now revoked it. */
    UNDO,

    /**
     * Owner has rejected an AccessRequest I previously sent. The
     * notification's `mode` field is null; `resourceUri` is the resource
     * I had requested. The owner may include a rationale in `summary`.
     */
    REJECT,

    /**
     * Owner has accepted an AccessRequest I previously sent. `resourceUri`
     * is the resource I requested and `mode` the granted mode; the owner is
     * the `as:actor`. Treated like [OFFER] for received-share syncing.
     */
    ACCEPTED,

    /**
     * My own record that I **granted** a request from someone else. `actor`
     * ([ShareNotification.ownerWebId]) is me, `target`
     * ([ShareNotification.targetWebId]) is the requester I granted,
     * `resourceUri` the resource of mine I shared, and `mode` the granted mode.
     * Read-only — no received-share side effect.
     */
    DECISION_GRANTED,

    /**
     * My own record that I **declined** a request from someone else. `actor`
     * ([ShareNotification.ownerWebId]) is me, `target`
     * ([ShareNotification.targetWebId]) is the requester I declined,
     * `resourceUri` the resource they asked for, `mode` the mode they requested,
     * and `summary` an optional rationale. Read-only — no received-share side
     * effect.
     */
    DECISION_REJECTED,
}
