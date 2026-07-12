package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import net.openid.appauth.TokenResponse

/**
 * Internal, transport-facing view of an authenticated session: the token-refresh and DPoP mechanics
 * the HTTP layer needs to authenticate outgoing requests.
 *
 * Deliberately kept off the public [Authenticator] surface. SDK consumers talk to the
 * resource/sharing/contacts managers and must never deal with access tokens, `Authorization`/`DPoP`
 * headers, or `DPoP-Nonce` values themselves — that plumbing is the library's responsibility.
 */
internal interface AuthSession {

    /**
     * Returns a valid [TokenResponse] for [webId], refreshing it first if it is near expiry (or if
     * [forceRefresh] is set), or `null` if no usable session exists.
     */
    suspend fun getLastTokenResponse(webId: String, forceRefresh: Boolean = false): TokenResponse?

    /** Builds the `Authorization` (+ `DPoP` proof, when DPoP-bound) headers for one request. */
    suspend fun getAuthHeaders(webId: String, httpMethod: String, uri: String): Map<String, String>

    /** Records the `DPoP-Nonce` a server at [resourceUri] returned, for use in subsequent proofs. */
    fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String)
}

/**
 * Narrows a public [Authenticator] to its internal [AuthSession].
 *
 * This is a downcast, not a general conversion: it succeeds only for the library's own
 * [Authenticator] (the one [Authenticator.getInstance] returns, which also implements
 * [AuthSession]) and throws for any other implementation — e.g. a test double or mock. The
 * transport layer is built exclusively from real instances, so within the library the cast is
 * safe; the guard exists to fail loudly rather than silently mis-authenticate if that ever
 * stops holding.
 */
internal fun Authenticator.asSession(): AuthSession = this as? AuthSession
    ?: error("Authenticator must be obtained from Authenticator.getInstance(context).")
