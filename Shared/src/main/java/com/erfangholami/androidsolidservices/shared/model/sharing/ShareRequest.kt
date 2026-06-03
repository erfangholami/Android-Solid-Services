package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Inbox notification representing a request from another agent asking the
 * current user (the resource owner) to grant access.
 *
 * Emitted on the owner's LDN inbox by another agent's
 * `NotificationsManager.sendRequest(...)`. The owner reviews the request
 * and either:
 * - accepts it via `SharingManager.acceptShareRequest(request)` — grants
 *   access and sends an `as:Offer` back to the requester;
 * - rejects it via `SharingManager.rejectShareRequest(request, reason)` —
 *   posts an `as:Reject` to the requester's inbox.
 *
 * Both choices leave the request resource in the inbox.
 */
@Parcelize
public data class ShareRequest(
    /** URI of the inbox resource that carries this request. */
    val requestUri: String,

    /** WebID of the requester (`as:actor`). */
    val requesterWebId: String,

    /** Resource URI being requested (`as:object`). */
    val resourceUri: String,

    /** Mode the requester is asking for (`solidshare:requestedMode`). */
    val requestedMode: ShareMode,

    /** Optional human-readable rationale (`as:summary`). */
    val summary: String?,

    /** `as:published` timestamp string, if present. */
    val publishedAt: String?,
) : Parcelable
