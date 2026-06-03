package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A single access relationship between the current user and another agent over
 * a resource, unified across every source the library can observe:
 *
 *  - shares the user has **given** (from `given_shares.ttl`),
 *  - shares the user has **received** (from `received_shares.ttl`),
 *  - incoming access **requests** awaiting the user's decision (from the LDN
 *    inbox), and
 *  - grants discovered via Solid Application Interoperability (SAI) registries,
 *    when the pod exposes them.
 *
 * This is the data behind a "who has access to what" overview. Items sourced
 * from the app's own indexes ([AccessGrantSource.APP_INDEX]) are authoritative;
 * [AccessGrantSource.SAI_REGISTRY] items are additive enrichment and may be
 * absent on servers without SAI support.
 */
@Parcelize
public data class AccessGrant(
    /** Whether this grant is one the user gave, received, or was asked for. */
    val direction: AccessGrantDirection,

    /**
     * The other party in the relationship: the receiver for [AccessGrantDirection.GIVEN],
     * the owner for [AccessGrantDirection.RECEIVED], the requester for
     * [AccessGrantDirection.INCOMING_REQUEST].
     */
    val counterpartWebId: String,

    /** Resource the access concerns. */
    val resourceUri: String,

    /** Access mode involved. */
    val mode: ShareMode,

    /** Whether the access is already in effect or still awaiting a decision. */
    val status: AccessGrantStatus,

    /** Where this row was observed. */
    val source: AccessGrantSource,

    /**
     * `as:published` / `interop:grantedAt` timestamp string when known, else
     * null. App-index given/received rows carry no timestamp.
     */
    val grantedAt: String?,

    /**
     * URI of the originating inbox request, present only for
     * [AccessGrantDirection.INCOMING_REQUEST] so the caller can accept or reject it.
     */
    val requestUri: String?,
) : Parcelable

/** Direction of an [AccessGrant] relative to the current user. */
public enum class AccessGrantDirection {
    /** The user granted access to someone else. */
    GIVEN,

    /** Someone granted access to the user. */
    RECEIVED,

    /** Someone is asking the user to grant access. */
    INCOMING_REQUEST,
}

/** Whether an [AccessGrant] is in effect or pending a decision. */
public enum class AccessGrantStatus {
    /** Access is in effect. */
    ACTIVE,

    /** Awaiting the user's accept/reject decision (an incoming request). */
    PENDING,
}

/** Where an [AccessGrant] was observed. */
public enum class AccessGrantSource {
    /** The app's own `given_shares.ttl` / `received_shares.ttl` / LDN inbox. */
    APP_INDEX,

    /** A Solid Application Interoperability registry on the pod. */
    SAI_REGISTRY,
}
