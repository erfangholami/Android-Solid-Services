package com.erfangholami.androidsolidservices.domain.repository

import android.content.Intent
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

interface AuthRepository {

    val activeProfileFlow: StateFlow<Profile?>
    val loggedInProfilesFlow: StateFlow<List<Profile>>
    val isAuthorizedFlow: StateFlow<Boolean>
    val activeWebIdFlow: StateFlow<String?>

    fun isUserAuthorized(): Boolean
    fun getAllLoggedInProfiles(): List<Profile>
    fun getProfile(webId: String): Profile

    suspend fun createAuthenticationIntent(
        webId: String? = null,
        oidcIssuer: String? = null,
        appName: String,
        redirectUri: String,
    ): Pair<Intent?, String?>

    suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?,
    ): String?

    suspend fun getActiveWebId(): String?
    suspend fun setActiveWebId(webId: String)
    suspend fun removeProfile(webId: String)
    suspend fun removeAllProfiles()
}
