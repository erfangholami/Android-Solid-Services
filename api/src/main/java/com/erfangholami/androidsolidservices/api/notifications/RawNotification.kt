package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad

/**
 * A single Linked Data Notification read from an LDN inbox, exposed as a
 * generic Activity Streams 2.0 view.
 *
 * This is the pure-transport representation produced by [NotificationTransport]:
 * it carries the standard AS2 envelope fields common to every notification, with
 * no assumption about what the activity *means*. A share offer, a chat message, a
 * comment, and a follow request all decode into this same shape. Domain layers
 * (for example SolidShare's sharing notifications) map this into their own typed
 * model; predicates outside the AS2 envelope — such as a WAC `acl:mode` carried
 * on a share offer — are reachable through [objectsOf] without this type having
 * to know about them.
 *
 * Spec anchors:
 *  - LDN: https://www.w3.org/TR/ldn/
 *  - Activity Streams 2.0: https://www.w3.org/TR/activitystreams-core/
 *  - Solid Notifications: https://solidproject.org/TR/notifications-protocol
 */
public data class RawNotification(
    /** URI of the notification resource inside the inbox. */
    val uri: String,
    /** Subject IRI of the activity this notification carries, or `null` if none was found. */
    val activitySubject: String?,
    /** Every `rdf:type` IRI declared on the activity. */
    val types: List<String>,
    /** `as:actor` — the agent that performed the activity, or `null`. */
    val actor: String?,
    /** `as:object` — the object the activity is about, or `null`. */
    val `object`: String?,
    /** `as:target` — the intended recipient of the activity, or `null`. */
    val target: String?,
    /** `as:summary` — a human-readable summary, or `null`. */
    val summary: String?,
    /** `as:published` — the raw lexical timestamp value, or `null`. */
    val publishedAt: String?,
    /** `as:inReplyTo` — the activity this one answers, or `null`. */
    val inReplyTo: String?,
    /** Every triple of the notification document, for predicates outside the AS2 envelope. */
    val quads: List<RdfQuad>,
) {
    /**
     * Returns the object values of every triple on [activitySubject] with the
     * given [predicate]. The escape hatch domain layers use to read predicates
     * this generic envelope does not model (for example `acl:mode`). Empty when
     * there is no activity subject or no matching triple.
     */
    public fun objectsOf(predicate: String): List<String> {
        val subject = activitySubject ?: return emptyList()
        return quads
            .filter { it.subject == subject && it.predicate == predicate }
            .map { it.`object` }
    }
}
