package com.erfangholami.androidsolidservices.api.auth.implementation

import android.net.Uri
import com.erfangholami.androidsolidservices.api.auth.Profile
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TokenRefreshCoordinatorTest {

    private val webId = "https://pod.example/me/profile/card#me"
    private val fixedNow = 1_000_000_000_000L

    private fun authStateWithAccessToken(expiresAt: Long): AuthState {
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
        return AuthState().apply {
            update(authResponse, null)
            update(tokenResponse, null)
        }
    }

    private fun coordinatorFor(profile: Profile): TokenRefreshCoordinator {
        val profileManager = mock(ProfileManager::class.java)
        `when`(profileManager.getProfileOrNull(webId)).thenReturn(profile)
        return TokenRefreshCoordinator(
            authService = mock(AuthorizationService::class.java),
            profileManager = profileManager,
            now = { fixedNow },
        )
    }

    @Test
    fun `forced refresh without a refresh token keeps a live session untouched`() {
        val profile = Profile(authState = authStateWithAccessToken(expiresAt = fixedNow + 600_000))
        assertNull(profile.authState.refreshToken)

        val result = runBlocking {
            coordinatorFor(profile).checkTokenAndRefresh(webId, profile, forceRefresh = true)
        }

        assertSame("a live refresh-less session must come back unchanged", profile, result)
        assertTrue(result.authState.isAuthorized)
        assertNull(result.toAccount().sessionError)
    }

    @Test
    fun `refresh-less session is marked expired once the access token is spent`() {
        val profile = Profile(authState = authStateWithAccessToken(expiresAt = fixedNow - 1))

        val result = runBlocking {
            coordinatorFor(profile).checkTokenAndRefresh(webId, profile, forceRefresh = true)
        }

        assertFalse(result.authState.isAuthorized)
        val sessionError = result.toAccount().sessionError.orEmpty()
        assertTrue(
            "expiry must be recorded as session_expired, was: $sessionError",
            sessionError.contains("session_expired"),
        )
        assertFalse(
            "a missing refresh token must not masquerade as a server-revoked grant",
            sessionError.contains("invalid_grant"),
        )
    }

    @Test
    fun `an already-expired session passes through untouched instead of being re-marked`() {
        val authState = authStateWithAccessToken(expiresAt = fixedNow - 1).apply {
            update(null as TokenResponse?, net.openid.appauth.AuthorizationException.TokenRequestErrors.INVALID_GRANT)
        }
        val profile = Profile(authState = authState)

        val result = runBlocking {
            coordinatorFor(profile).checkTokenAndRefresh(webId, profile, forceRefresh = true)
        }

        assertSame("a dead session must not be re-recorded on every probe", profile, result)
    }

    @Test
    fun `requestToken refuses a refresh without a refresh token instead of synthesizing invalid_grant`() {
        val profile = Profile(authState = authStateWithAccessToken(expiresAt = fixedNow - 1))

        val (tokenResponse, exception) = runBlocking {
            coordinatorFor(profile).requestToken(profile, isRefresh = true)
        }

        assertNull(tokenResponse)
        assertEquals("no_refresh_token", exception?.error)
    }
}
