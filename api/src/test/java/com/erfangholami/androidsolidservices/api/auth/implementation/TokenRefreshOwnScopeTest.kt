package com.erfangholami.androidsolidservices.api.auth.implementation

import android.net.Uri
import com.erfangholami.androidsolidservices.api.auth.Profile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TokenRefreshOwnScopeTest {

    private val webId = "https://pod.example/me/profile/card#me"
    private val fixedNow = 1_000_000_000_000L

    private class FakeProfileStore(initial: Profile) : ProfileStore {
        @Volatile
        var stored: Profile = initial
        val written = mutableListOf<Profile>()

        override fun getProfileOrNull(webId: String): Profile? = stored

        override suspend fun writeProfile(webId: String, profile: Profile) {
            stored = profile
            written += profile
        }
    }

    private class GatedEndpoint(
        private val response: TokenResponse,
    ) : RefreshTokenEndpoint {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0

        override suspend fun refresh(profile: Profile) = run {
            calls++
            entered.complete(Unit)
            release.await()
            Pair(response, null)
        }
    }

    private fun config() = AuthorizationServiceConfiguration(
        Uri.parse("https://op.example/auth"),
        Uri.parse("https://op.example/token"),
    )

    private fun expiredAuthStateWithRefreshToken(refreshToken: String): AuthState {
        val config = config()
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
            .setAccessToken("access-old")
            .setRefreshToken(refreshToken)
            .setAccessTokenExpirationTime(fixedNow - 1)
            .build()
        return AuthState().apply {
            update(authResponse, null)
            update(tokenResponse, null)
        }
    }

    private fun rotatedResponse(newRefreshToken: String): TokenResponse {
        val refreshRequest = TokenRequest.Builder(config(), "client-id")
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken("rt-old")
            .build()
        return TokenResponse.Builder(refreshRequest)
            .setTokenType("Bearer")
            .setAccessToken("access-new")
            .setRefreshToken(newRefreshToken)
            .setAccessTokenExpirationTime(fixedNow + 3_600_000)
            .build()
    }

    @Test
    fun `cancelling the caller does not cancel the rotation and the rotated token is kept`() {
        val profile = Profile(authState = expiredAuthStateWithRefreshToken("rt-old"))
        val store = FakeProfileStore(profile)
        val endpoint = GatedEndpoint(rotatedResponse("rt-new"))
        val coordinator = TokenRefreshCoordinator(
            authService = mock(AuthorizationService::class.java),
            profileManager = store,
            now = { fixedNow },
            endpoint = endpoint,
        )

        runBlocking {
            val caller = launch(Dispatchers.IO) {
                coordinator.checkTokenAndRefresh(webId, profile)
            }
            withTimeout(5_000) { endpoint.entered.await() }
            val flight = coordinator.inFlightOrNull(webId)!!

            caller.cancel()
            caller.join()
            assertTrue("the awaiting caller must be gone", caller.isCancelled)

            endpoint.release.complete(Unit)
            val refreshed = withTimeout(5_000) { flight.await() }

            assertEquals("rt-new", refreshed.authState.refreshToken)
            assertEquals(
                "the rotation must be persisted even though its caller died",
                listOf("rt-new"),
                store.written.map { it.authState.refreshToken },
            )
        }
    }

    @Test
    fun `concurrent callers share one refresh and the spent token is sent once`() {
        val profile = Profile(authState = expiredAuthStateWithRefreshToken("rt-old"))
        val store = FakeProfileStore(profile)
        val endpoint = GatedEndpoint(rotatedResponse("rt-new"))
        val coordinator = TokenRefreshCoordinator(
            authService = mock(AuthorizationService::class.java),
            profileManager = store,
            now = { fixedNow },
            endpoint = endpoint,
        )

        runBlocking {
            val first = launch(Dispatchers.IO) { coordinator.checkTokenAndRefresh(webId, profile) }
            withTimeout(5_000) { endpoint.entered.await() }
            val second = launch(Dispatchers.IO) { coordinator.checkTokenAndRefresh(webId, profile) }

            endpoint.release.complete(Unit)
            first.join()
            second.join()

            assertEquals("one refresh across all callers", 1, endpoint.calls)
            assertEquals(listOf("rt-new"), store.written.map { it.authState.refreshToken })
        }
    }

    @Test
    fun `a caller arriving just after completion is served the rotated result without a second spend`() {
        val profile = Profile(authState = expiredAuthStateWithRefreshToken("rt-old"))
        val store = FakeProfileStore(profile)
        val endpoint = GatedEndpoint(rotatedResponse("rt-new"))
        val coordinator = TokenRefreshCoordinator(
            authService = mock(AuthorizationService::class.java),
            profileManager = store,
            now = { fixedNow },
            endpoint = endpoint,
        )

        runBlocking {
            endpoint.release.complete(Unit)
            val refreshed = coordinator.checkTokenAndRefresh(webId, profile)
            assertEquals("rt-new", refreshed.authState.refreshToken)

            val again = coordinator.checkTokenAndRefresh(webId, profile)

            assertEquals("rt-new", again.authState.refreshToken)
            assertEquals("the coalesced result is reused, not re-fetched", 1, endpoint.calls)
        }
    }
}
