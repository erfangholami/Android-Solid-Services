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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionStateTest {

    private fun authorizedAuthState(): AuthState {
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
            .setAccessTokenExpirationTime(System.currentTimeMillis() + 600_000)
            .build()
        return AuthState().apply {
            update(authResponse, null)
            update(tokenResponse, null)
        }
    }

    private fun withError(error: AuthorizationException): AuthState =
        authorizedAuthState().apply { update(null as TokenResponse?, error) }

    @Test
    fun `authorized tokens classify as active`() {
        val state = SessionState.of(Profile(authState = authorizedAuthState()))

        assertEquals(SessionState.Active, state)
        assertTrue(state.isSignedIn)
        assertFalse(state.isDead)
        assertFalse(state.isExpired)
    }

    @Test
    fun `an invalid_grant is revoked and dead no matter how partial the profile is`() {
        val state = SessionState.of(
            Profile(authState = withError(AuthorizationException.TokenRequestErrors.INVALID_GRANT)),
        )

        assertEquals(SessionState.Revoked, state)
        assertTrue(state.isDead)
        assertTrue(state.isExpired)
    }

    @Test
    fun `a recorded session expiry is dead but re-authenticable`() {
        val expiry = AuthorizationException.fromOAuthTemplate(
            AuthorizationException.TokenRequestErrors.OTHER,
            SessionErrors.SESSION_EXPIRED,
            null,
            null,
        )
        val state = SessionState.of(Profile(authState = withError(expiry)))

        assertEquals(SessionState.NeedsReauth(SessionErrors.SESSION_EXPIRED), state)
        assertTrue(state.isDead)
        assertTrue(state.isExpired)
    }

    @Test
    fun `an unauthorized session without a recorded error needs reauth but is not dead`() {
        val state = SessionState.of(Profile(authState = AuthState()))

        assertEquals(SessionState.NeedsReauth(SessionErrors.UNAUTHORIZED), state)
        assertFalse("a session that merely lacks tokens may still be refreshed", state.isDead)
        assertTrue(state.isExpired)
    }

    @Test
    fun `completeness is orthogonal to the auth state`() {
        val profile = Profile(authState = authorizedAuthState())

        assertFalse("no identity and no profile document yet", profile.isComplete)
        assertTrue(SessionState.of(profile).isSignedIn)
    }
}
