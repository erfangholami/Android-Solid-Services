package com.erfangholami.androidsolidservices.api.transport

import java.net.URI

/**
 * What a `401` actually said, rather than what it might have meant.
 *
 * A 401 answers two very different questions with one status code: *"your token is stale"* and
 * *"you may not have this"*. Only the first is worth a token refresh; treating the second as
 * expiry turns every cross-pod read of a resource you cannot see into refresh traffic, which is
 * how a healthy token ends up rate-limited — and, on providers that revoke a grant family when a
 * refresh token is replayed, how a session dies.
 */
internal sealed interface AuthChallenge {

    /** The DPoP proof needs the server's nonce; retry with it, no refresh. */
    data object NonceStale : AuthChallenge

    /** The server named the token as invalid or expired: refresh is the correct answer. */
    data object TokenExpired : AuthChallenge

    /** The server named an authorization problem: refreshing cannot change the outcome. */
    data object NotAuthorized : AuthChallenge

    /** No machine-readable reason — the caller decides from context. */
    data object Unspecified : AuthChallenge

    companion object {

        fun parse(wwwAuthenticate: String?): AuthChallenge {
            val header = wwwAuthenticate.orEmpty()
            val mentionsBadToken = header.contains("invalid_token", ignoreCase = true) ||
                header.contains("expired_token", ignoreCase = true)
            return when {
                header.contains("use_dpop_nonce", ignoreCase = true) && !mentionsBadToken ->
                    NonceStale

                mentionsBadToken -> TokenExpired

                header.contains("insufficient_scope", ignoreCase = true) ||
                    header.contains("invalid_request", ignoreCase = true) -> NotAuthorized

                else -> Unspecified
            }
        }
    }
}

/**
 * Whether this challenge justifies spending a token refresh.
 *
 * An unspecified 401 is ambiguous, so origin decides: a pod that is not the identity's own
 * answering 401 without naming a token problem is denying access, not reporting expiry — a
 * server that meant expiry would have said so. Refreshing there buys nothing and costs a
 * round-trip against the identity provider.
 */
internal fun AuthChallenge.warrantsTokenRefresh(requestIsOwnOrigin: Boolean): Boolean = when (this) {
    AuthChallenge.TokenExpired -> true
    AuthChallenge.Unspecified -> requestIsOwnOrigin
    AuthChallenge.NonceStale, AuthChallenge.NotAuthorized -> false
}

internal fun isOwnOrigin(webId: String, uri: URI): Boolean {
    val identity = runCatching { URI.create(webId) }.getOrNull() ?: return true
    val identityAuthority = identity.authority ?: return true
    return identityAuthority.equals(uri.authority, ignoreCase = true)
}
