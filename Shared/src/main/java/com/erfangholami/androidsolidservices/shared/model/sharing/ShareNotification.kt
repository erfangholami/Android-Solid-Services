package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * One inbox notification surfaced to the receiver — either an offer of new
 * access ([ShareNotificationType.OFFER]) or a notice that previously-granted
 * access was withdrawn ([ShareNotificationType.UNDO]).
 *
 * The library does not delete inbox items after listing them; the inbox is
 * the durable history. The state derived from these notifications is
 * mirrored into `received_shares.ttl` automatically each time
 * `SharingManager.listShareNotifications` is called.
 */
@Parcelize
public data class ShareNotification(
    /** URI of the inbox resource that carries this notification. */
    val notificationUri: String,

    /** Whether the notification announces, retracts, or rejects access. */
    val type: ShareNotificationType,

    /** WebID of the sender (`as:actor`). */
    val ownerWebId: String,

    /** Resource URI the notification is about (`as:object`). */
    val resourceUri: String,

    /** Access mode granted. Present on OFFER / ACCEPTED, null on UNDO / REJECT. */
    val mode: ShareMode?,

    /**
     * Optional human-readable text (`as:summary`). Used for REJECT to
     * carry a rationale; usually absent on OFFER / UNDO.
     */
    val summary: String?,

    /** `as:published` timestamp string from the notification, if present. */
    val publishedAt: String?,

    /**
     * WebID of the activity's `as:target`, when present. For the self-authored
     * [ShareNotificationType.DECISION_GRANTED] / [ShareNotificationType.DECISION_REJECTED]
     * records this is the requester the decision was about (the counterpart to
     * show), since `ownerWebId` (the `as:actor`) is the inbox owner themselves.
     * Null for the other types, which carry no meaningful target for callers.
     */
    val targetWebId: String? = null,

    /**
     * RDF class IRI asserted on the `as:object` in the notification body
     * (`<resourceUri> rdf:type <iri>`), announcing that the share carries a
     * data-module entity (e.g. `https://schema.org/Ticket`). Null when the
     * notification describes a plain resource.
     */
    val resourceType: String? = null,

    /**
     * Human title asserted on the `as:object` (`schema:name`), when the sender
     * announced one alongside [resourceType]. Null otherwise.
     */
    val resourceName: String? = null,
) : Parcelable
