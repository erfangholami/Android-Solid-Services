package com.erfangholami.androidsolidservices.api.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class AuthChallengeTest {

    private val webId = "https://alice.solidcommunity.net/profile/card#me"
    private val ownPod = URI.create("https://alice.solidcommunity.net/notes/n1")
    private val foreignPod = URI.create("https://storage.inrupt.com/bob/shared/photo.jpg")

    @Test
    fun `a nonce challenge is not an expiry`() {
        val challenge = AuthChallenge.parse(
            """DPoP realm="solid", error="use_dpop_nonce", error_description="nonce required"""",
        )

        assertEquals(AuthChallenge.NonceStale, challenge)
        assertFalse(challenge.warrantsTokenRefresh(requestIsOwnOrigin = true))
    }

    @Test
    fun `a nonce challenge that also names a bad token still refreshes`() {
        val challenge = AuthChallenge.parse("""DPoP error="invalid_token", error="use_dpop_nonce"""")

        assertEquals(AuthChallenge.TokenExpired, challenge)
        assertTrue(challenge.warrantsTokenRefresh(requestIsOwnOrigin = false))
    }

    @Test
    fun `a named token problem refreshes wherever it happens`() {
        listOf("""Bearer error="invalid_token"""", """DPoP error="expired_token"""").forEach {
            val challenge = AuthChallenge.parse(it)
            assertEquals(AuthChallenge.TokenExpired, challenge)
            assertTrue(challenge.warrantsTokenRefresh(requestIsOwnOrigin = false))
        }
    }

    @Test
    fun `an authorization problem never refreshes`() {
        val challenge = AuthChallenge.parse("""Bearer error="insufficient_scope"""")

        assertEquals(AuthChallenge.NotAuthorized, challenge)
        assertFalse(challenge.warrantsTokenRefresh(requestIsOwnOrigin = true))
    }

    @Test
    fun `a bare 401 from a foreign pod is a denial, not an expiry`() {
        val challenge = AuthChallenge.parse("DPoP realm=\"solid\"")

        assertEquals(AuthChallenge.Unspecified, challenge)
        assertFalse(
            "this is the cross-pod read that produced the refresh storm",
            challenge.warrantsTokenRefresh(isOwnOrigin(webId, foreignPod)),
        )
    }

    @Test
    fun `a bare 401 from the identity's own pod still refreshes`() {
        val challenge = AuthChallenge.parse("DPoP realm=\"solid\"")

        assertTrue(challenge.warrantsTokenRefresh(isOwnOrigin(webId, ownPod)))
    }

    @Test
    fun `a missing header is unspecified rather than an error`() {
        assertEquals(AuthChallenge.Unspecified, AuthChallenge.parse(null))
        assertEquals(AuthChallenge.Unspecified, AuthChallenge.parse(""))
    }

    @Test
    fun `an unparseable identity is treated as its own origin`() {
        assertTrue(isOwnOrigin("not a uri", ownPod))
    }
}
