package com.erfangholami.androidsolidservices.shared.model.sharing

/**
 * Kind of share notification carried by the LDN inbox.
 *
 * Maps to Activity Streams 2 / SolidShare activity types:
 * - [OFFER]    → `as:Offer`       — owner is granting access to me
 * - [ACCEPTED] → `as:Accept`      — owner has granted an AccessRequest I sent
 * - [UNDO]     → `as:Undo`        — owner has retracted access they gave me
 * - [REJECT]   → `as:Reject`      — owner has declined an AccessRequest I sent
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
}
