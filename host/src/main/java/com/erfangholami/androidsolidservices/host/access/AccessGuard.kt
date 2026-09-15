package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.host.HostSession
import com.erfangholami.androidsolidservices.host.dispatch.CallerAttribution
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode

/**
 * The three gates every guarded verb passes, in order: the caller resolves to a package, the
 * WebID has a live session, and the caller's grant covers the verb.
 *
 * [caller] must run on the binder thread, where the calling UID is known; [gate] captures it
 * there and defers the rest to the dispatched coroutine. The codes match what the old
 * resource-only guard answered, so the client SDK maps them the same way.
 */
public class AccessGuard(
    private val session: HostSession,
    private val policy: AccessPolicy,
    private val callerResolver: () -> String? = CallerAttribution::callingPackage,
) {

    /** The calling app's package, read now, on the binder thread. */
    public fun caller(): String? = callerResolver()

    public suspend fun check(
        caller: String?,
        webId: String,
        vararg requirements: Requirement,
    ): AccessCheck {
        session.awaitReady()
        if (caller == null) {
            return AccessCheck.Denied(ExceptionsErrorCode.UNKNOWN, "Unable to resolve the calling package.")
        }
        if (!session.hasSession(webId)) {
            return AccessCheck.Denied(ExceptionsErrorCode.SOLID_NOT_LOGGED_IN, "No signed-in session for $webId.")
        }
        requirements.forEach { requirement ->
            val check = policy.check(caller, webId, requirement.target, requirement.level)
            if (check is AccessCheck.Denied) return check
        }
        return AccessCheck.Allowed
    }

    /** Captures the caller now and answers the full check when invoked inside the coroutine. */
    public fun gate(webId: String, vararg requirements: Requirement): suspend () -> AccessCheck {
        val caller = caller()
        return { check(caller, webId, *requirements) }
    }

    /** The one-requirement form of [gate]. */
    public fun gate(webId: String, target: VerbTarget, level: AccessLevel): suspend () -> AccessCheck =
        gate(webId, Requirement(target, level))
}
