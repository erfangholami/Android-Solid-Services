package com.erfangholami.androidsolidservices.api.notifications

/**
 * The `Slug` header prefixes a share-notification profile asks the server to use
 * when naming the notification resources it POSTs to an inbox — one per activity
 * kind. Lets a profile brand created notifications with something other than
 * SolidShare's `solidshare-*` names (these surface as the inbox file names, e.g.
 * `solidshare-offer-<uuid>.ttl`).
 *
 * The default is [SolidShareNotificationSlugs].
 */
public interface NotificationSlugs {
    /** Slug prefix for an `as:Offer`. */
    public val offer: String

    /** Slug prefix for an `as:Undo`. */
    public val undo: String

    /** Slug prefix for an `as:Update` (access-level change). */
    public val update: String

    /** Slug prefix for an `interop:AccessRequest`. */
    public val request: String

    /** Slug prefix for an `as:Reject` sent to a requester. */
    public val reject: String

    /** Slug prefix for an `as:Accept` sent to a requester. */
    public val accept: String

    /** Slug prefix for the owner's own "granted" decision record. */
    public val decisionAccept: String

    /** Slug prefix for the owner's own "declined" decision record. */
    public val decisionReject: String
}

/** SolidShare's `solidshare-*` notification slug prefixes. */
public object SolidShareNotificationSlugs : NotificationSlugs {
    override val offer: String = "solidshare-offer"
    override val undo: String = "solidshare-undo"
    override val update: String = "solidshare-update"
    override val request: String = "solidshare-request"
    override val reject: String = "solidshare-reject"
    override val accept: String = "solidshare-accept"
    override val decisionAccept: String = "solidshare-decision-accept"
    override val decisionReject: String = "solidshare-decision-reject"
}
