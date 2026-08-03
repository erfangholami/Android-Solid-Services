package com.erfangholami.androidsolidservices.api.auth

/**
 * The credential-bearing face of a signed-in identity: everything the HTTP layer needs to put a
 * user on a request, and nothing about managing accounts.
 *
 * [Authenticator] extends this, so the session a transport uses is the authenticator it was
 * built from — but a transport depends only on this seam, which keeps it constructible in tests
 * with a three-method fake and keeps account management out of layers that only need a token.
 */
public interface SolidSession {

    /**
     * Ensures [webId] holds a usable access token, refreshing if the session needs and supports
     * it, and reports whether one is available. With [forceRefresh] the refresh is requested even
     * while the current token looks valid — the escalation path after an unexplained `401` from
     * the identity's own origin; the refresh coordinator still applies its own cooldowns.
     */
    public suspend fun hasValidToken(webId: String, forceRefresh: Boolean = false): Boolean

    /**
     * The headers that authenticate [webId] on one specific request: the access token plus a
     * DPoP proof bound to [httpMethod] and [uri], carrying the origin's current nonce when one
     * is known.
     *
     * @throws IllegalStateException when no token is available — call [hasValidToken] first.
     */
    public suspend fun authHeaders(webId: String, httpMethod: String, uri: String): Map<String, String>

    /**
     * Stores the `DPoP-Nonce` a server issued for [resourceUri]'s origin, so the next proof for
     * that origin carries it.
     */
    public fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String)
}
