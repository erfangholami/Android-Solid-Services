package com.erfangholami.androidsolidservices.api.auth.implementation

import android.net.Uri
import com.erfangholami.androidsolidservices.api.auth.Profile
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RefreshPolicyTest {

    private val webId = "https://pod.example/me/profile/card#me"
    private var clock = 1_000_000_000_000L
    private val policy = RefreshPolicy { clock }

    private fun profileExpiringAt(expiresAt: Long): Profile {
        val config = AuthorizationServiceConfiguration(
            Uri.parse("https://op.example/auth"),
            Uri.parse("https://op.example/token"),
        )
        val authRequest = AuthorizationRequest.Builder(
            config,
            "client-id",
            ResponseTypeValues.CODE,
            Uri.parse("https://app.example/redirect"),
        ).setScope("openid").build()
        val authResponse = AuthorizationResponse.Builder(authRequest)
            .setAuthorizationCode("code-1")
            .setState(authRequest.state)
            .build()
        val tokenRequest = TokenRequest.Builder(config, "client-id")
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setAuthorizationCode("code-1")
            .setRedirectUri(Uri.parse("https://app.example/redirect"))
            .build()
        val tokenResponse = TokenResponse.Builder(tokenRequest)
            .setTokenType("Bearer")
            .setAccessToken("access-1")
            .setAccessTokenExpirationTime(expiresAt)
            .build()
        return Profile(
            authState = AuthState().apply {
                update(authResponse, null)
                update(tokenResponse, null)
            },
        )
    }

    @Test
    fun `a token is refreshed one minute before it expires, not after`() {
        assertTrue(policy.needsTokenRefresh(profileExpiringAt(clock + 59_000)))
        assertFalse(policy.needsTokenRefresh(profileExpiringAt(clock + 61_000)))
    }

    @Test
    fun `a fresh token skips refresh unless forced`() {
        val fresh = profileExpiringAt(clock + 600_000)

        assertTrue(policy.shouldSkip(webId, fresh, forceRefresh = false))
        assertFalse(policy.shouldSkip(webId, fresh, forceRefresh = true))
    }

    @Test
    fun `a dead session is never refreshed, forced or not`() {
        val dead = profileExpiringAt(clock - 1).let {
            it.copy(
                authState = it.authState.apply {
                    update(null as TokenResponse?, AuthorizationException.TokenRequestErrors.INVALID_GRANT)
                },
            )
        }

        assertTrue(policy.shouldSkip(webId, dead, forceRefresh = false))
        assertTrue(policy.shouldSkip(webId, dead, forceRefresh = true))
    }

    @Test
    fun `a second forced refresh within the cooldown is suppressed while the token is valid`() {
        val fresh = profileExpiringAt(clock + 600_000)
        policy.noteForced(webId)

        clock += 59_000
        assertTrue(policy.shouldSkip(webId, fresh, forceRefresh = true))

        clock += 2_000
        assertFalse(policy.shouldSkip(webId, fresh, forceRefresh = true))
    }

    @Test
    fun `the forced cooldown never blocks a genuinely expiring token`() {
        policy.noteForced(webId)
        val expiring = profileExpiringAt(clock + 30_000)

        assertFalse(policy.shouldSkip(webId, expiring, forceRefresh = true))
    }

    @Test
    fun `a 429 pauses refreshes for the advertised window, clamped to sane bounds`() {
        val expiring = profileExpiringAt(clock + 30_000)

        policy.recordRateLimit(webId, retryAfterSeconds = 5)
        assertTrue(policy.shouldSkip(webId, expiring, forceRefresh = false))
        clock += 10_000
        assertFalse("the 5s ask is clamped up to the 10s floor", policy.shouldSkip(webId, expiring, false))

        policy.recordRateLimit(webId, retryAfterSeconds = 100_000)
        clock += 300_000
        assertFalse("a huge ask is clamped down to the 300s ceiling", policy.shouldSkip(webId, expiring, false))
    }

    @Test
    fun `rate limiting yields once the access token is hard-expired`() {
        policy.recordRateLimit(webId, retryAfterSeconds = null)
        val spent = profileExpiringAt(clock - 1)

        assertFalse(
            "with no usable token left, waiting out the window is worse than trying",
            policy.shouldSkip(webId, spent, forceRefresh = false),
        )
    }

    @Test
    fun `a refresh result is coalesced briefly and then expires`() {
        val refreshed = profileExpiringAt(clock + 600_000)
        policy.noteSuccess(webId, refreshed)

        assertEquals(refreshed, policy.coalescedResult(webId))
        clock += 5_001
        assertNull(policy.coalescedResult(webId))
    }

    @Test
    fun `forgetting a webid clears its windows`() {
        policy.noteForced(webId)
        policy.recordRateLimit(webId, retryAfterSeconds = null)
        policy.noteSuccess(webId, profileExpiringAt(clock + 600_000))

        policy.forget(webId)

        assertNull(policy.coalescedResult(webId))
        assertFalse(policy.shouldSkip(webId, profileExpiringAt(clock + 600_000), forceRefresh = true))
    }
}
