package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMappingTest {

    @Test
    fun fresh_profile_maps_as_unauthorized_without_a_session_error() {
        val account = Profile(authState = AuthState()).toAccount()
        assertFalse(account.isAuthorized)
        assertNull(account.sessionError)
    }

    @Test
    fun terminal_refresh_failure_surfaces_the_recorded_oauth_error() {
        val authState = AuthState()
        authState.update(
            null as TokenResponse?,
            AuthorizationException.TokenRequestErrors.INVALID_GRANT,
        )
        val account = Profile(authState = authState).toAccount()
        assertFalse(account.isAuthorized)
        assertTrue(
            "sessionError should carry the OAuth error, was: ${account.sessionError}",
            account.sessionError.orEmpty().contains("invalid_grant"),
        )
    }
}
