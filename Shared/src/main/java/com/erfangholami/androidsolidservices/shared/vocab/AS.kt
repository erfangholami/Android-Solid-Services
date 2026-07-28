package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Activity Streams 2.0 vocabulary constants.
 * https://www.w3.org/ns/activitystreams#
 * Used by the Solid Notifications Protocol for notification message types.
 * Spec: https://solidproject.org/TR/notifications-protocol
 */
public object AS {
    public const val NAMESPACE: String = "https://www.w3.org/ns/activitystreams#"

    public const val CREATE: String = "${NAMESPACE}Create"
    public const val UPDATE: String = "${NAMESPACE}Update"
    public const val DELETE: String = "${NAMESPACE}Delete"
    public const val ADD: String = "${NAMESPACE}Add"
    public const val REMOVE: String = "${NAMESPACE}Remove"
    public const val MOVE: String = "${NAMESPACE}Move"
    public const val ANNOUNCE: String = "${NAMESPACE}Announce"

    /** Offer activity — used by SolidShare to deliver a share via LDN inbox. */
    public const val OFFER: String = "${NAMESPACE}Offer"

    /** Undo activity — used to retract a previous Offer on revoke. */
    public const val UNDO: String = "${NAMESPACE}Undo"

    /** Reject activity — used by an owner to decline a received AccessRequest. */
    public const val REJECT: String = "${NAMESPACE}Reject"

    /** Accept activity — sent to a requester when their AccessRequest is granted. */
    public const val ACCEPT: String = "${NAMESPACE}Accept"

    public const val OBJECT_TYPE: String = "${NAMESPACE}Object"
    public const val ACTIVITY: String = "${NAMESPACE}Activity"
    public const val COLLECTION: String = "${NAMESPACE}Collection"
    public const val ORDERED_COLLECTION: String = "${NAMESPACE}OrderedCollection"
    public const val LINK: String = "${NAMESPACE}Link"

    /** The object affected by the activity. */
    public const val OBJECT: String = "${NAMESPACE}object"

    /** Target resource(s) for the activity. */
    public const val TARGET: String = "${NAMESPACE}target"

    /** When the notification was published (xsd:dateTime). */
    public const val PUBLISHED: String = "${NAMESPACE}published"

    /** Unique identifier for the notification. */
    public const val ID: String = "${NAMESPACE}id"

    /** Agent that performed the activity. */
    public const val ACTOR: String = "${NAMESPACE}actor"

    /** Summary of the activity. */
    public const val SUMMARY: String = "${NAMESPACE}summary"

    /** Links an Accept/Reject back to the AccessRequest it answers. */
    public const val IN_REPLY_TO: String = "${NAMESPACE}inReplyTo"

    public const val ITEMS: String = "${NAMESPACE}items"
    public const val ORDERED_ITEMS: String = "${NAMESPACE}orderedItems"
    public const val FIRST: String = "${NAMESPACE}first"
    public const val LAST: String = "${NAMESPACE}last"
    public const val NEXT: String = "${NAMESPACE}next"
    public const val PREV: String = "${NAMESPACE}prev"
    public const val TOTAL_ITEMS: String = "${NAMESPACE}totalItems"
}
