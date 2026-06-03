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
 * Narrows a public [Authenticator] to its internal [AuthSession]. The library ships a single
 * [Authenticator] implementation, so this always succeeds for instances obtained from
 * [Authenticator.getInstance].
 */
internal fun Authenticator.asSession(): AuthSession = this as? AuthSession
    ?: error("Authenticator must be obtained from Authenticator.getInstance(context).")
