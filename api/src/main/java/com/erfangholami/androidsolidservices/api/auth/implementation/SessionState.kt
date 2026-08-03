package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Profile

internal object SessionErrors {
    const val NO_REFRESH_TOKEN = "no_refresh_token"
    const val SESSION_EXPIRED = "session_expired"
    const val RATE_LIMITED = "rate_limited"
    const val INVALID_GRANT = "invalid_grant"
    const val INVALID_CLIENT = "invalid_client"
    const val UNAUTHORIZED = "unauthorized"
}

/**
 * The one classification of a stored session's auth state. Every surface that used to ask its
 * own combination of `isAuthorized` and error codes — the account flows, the refresh
 * coordinator's dead-session gate, the active-account reconciler — projects from this instead,
 * so they can never disagree.
 *
 * Deliberately blind to [Profile.isComplete]: whether a login ever finished decides where an
 * account is *shown*, never whether its tokens may be spent — a revoked grant stays revoked no
 * matter how partial the profile around it is.
 */
internal sealed interface SessionState {

    /** Holds authorized tokens. */
    data object Active : SessionState

    /** The session ended; signing in again with the same WebID restores the account in place. */
    data class NeedsReauth(val reason: String) : SessionState

    /** The provider revoked the grant; nothing but a fresh login helps. */
    data object Revoked : SessionState

    companion object {
        fun of(profile: Profile): SessionState {
            if (profile.authState.isAuthorized) return Active
            return when (val error = profile.authState.authorizationException?.error) {
                SessionErrors.INVALID_GRANT, SessionErrors.INVALID_CLIENT -> Revoked
                null -> NeedsReauth(SessionErrors.UNAUTHORIZED)
                else -> NeedsReauth(error)
            }
        }
    }
}

/** Whether spending this session's tokens can ever succeed again without a new login. */
internal val SessionState.isDead: Boolean
    get() = this is SessionState.Revoked ||
        (this as? SessionState.NeedsReauth)?.reason == SessionErrors.SESSION_EXPIRED

internal val SessionState.isSignedIn: Boolean
    get() = this is SessionState.Active

internal val SessionState.isExpired: Boolean
    get() = this is SessionState.Revoked || this is SessionState.NeedsReauth

/** A login that produced an identity and a profile document; anything less is shown nowhere. */
internal val Profile.isComplete: Boolean
    get() = userInfo != null && webId != null
