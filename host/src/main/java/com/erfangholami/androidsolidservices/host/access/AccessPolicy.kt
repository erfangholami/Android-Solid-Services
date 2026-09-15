package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel

/** The outcome of an access check: proceed, or answer the caller with a code and a message. */
public sealed interface AccessCheck {

    public data object Allowed : AccessCheck

    /** [code] is an `ExceptionsErrorCode` value the client SDK maps to a typed exception. */
    public data class Denied(val code: Int, val message: String) : AccessCheck
}

/**
 * What a verb is about, matched against the caller's grant.
 *
 * A verb that names one resource is checked on that resource; a data-module verb on its module;
 * a verb that touches the account as a whole (the share indexes, the inbox) on the whole pod;
 * and reading the WebID profile, which every app needs to find the pod root, on any entry at all.
 */
public sealed interface VerbTarget {

    public data class Resource(val uri: String) : VerbTarget

    public data class Module(val id: String) : VerbTarget

    public data object Pod : VerbTarget

    public data object AnyEntry : VerbTarget
}

/** One target a verb needs at one minimum level. A verb with two targets carries two of these. */
public data class Requirement(val target: VerbTarget, val level: AccessLevel)

/** Decides whether an app may run a verb as a WebID. The host decides through its grants. */
public interface AccessPolicy {

    public suspend fun check(
        callerPackage: String,
        webId: String,
        target: VerbTarget,
        level: AccessLevel,
    ): AccessCheck
}
