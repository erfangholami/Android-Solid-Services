package com.erfangholami.androidsolidservices.host

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.shared.model.profile.WebId

/**
 * What the binders need to know about the host's signed-in accounts, and nothing more.
 *
 * The account store loads asynchronously, so every answer waits for [awaitReady] first; asking
 * before that would report a signed-in user as absent. [AuthenticatorHostSession] is the
 * implementation over the library's [Authenticator]; tests supply their own.
 */
public interface HostSession {

    /** Suspends until the account store has loaded. Cheap once it has. */
    public suspend fun awaitReady()

    /** `true` when at least one account holds a usable session. */
    public suspend fun hasLoggedIn(): Boolean

    /** `true` when [webId] is signed in and its session has not expired. */
    public suspend fun hasSession(webId: String): Boolean

    /** The parsed WebID profile document of [webId], or `null` when it is not signed in. */
    public suspend fun profileDocument(webId: String): WebId?
}

/** The [HostSession] over the library's [Authenticator]. */
public class AuthenticatorHostSession(
    private val authenticator: Authenticator,
) : HostSession {

    override suspend fun awaitReady() {
        authenticator.getActiveWebId()
    }

    override suspend fun hasLoggedIn(): Boolean {
        awaitReady()
        return authenticator.isUserAuthorized()
    }

    override suspend fun hasSession(webId: String): Boolean {
        awaitReady()
        return runCatching { authenticator.getProfile(webId).isAuthorized }.getOrDefault(false)
    }

    override suspend fun profileDocument(webId: String): WebId? {
        awaitReady()
        return runCatching { authenticator.getProfile(webId).webId }.getOrNull()
    }
}
