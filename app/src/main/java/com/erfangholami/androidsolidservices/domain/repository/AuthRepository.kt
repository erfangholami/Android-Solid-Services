package com.erfangholami.androidsolidservices.domain.repository

import android.content.Intent
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {

    val activeProfileFlow: StateFlow<SolidAccount?>
    val loggedInProfilesFlow: StateFlow<List<SolidAccount>>
    val isAuthorizedFlow: StateFlow<Boolean>
    val activeWebIdFlow: StateFlow<String?>

    fun isUserAuthorized(): Boolean
    fun getAllLoggedInProfiles(): List<SolidAccount>
    fun getProfile(webId: String): SolidAccount

    suspend fun createAuthenticationIntent(
        webId: String? = null,
        oidcIssuer: String? = null,
        appName: String,
        redirectUri: String,
        clientId: String? = null,
    ): Pair<Intent?, String?>

    suspend fun submitAuthorizationResponse(responseData: Intent?): String?

    suspend fun getActiveWebId(): String?
    suspend fun setActiveWebId(webId: String)
    suspend fun removeProfile(webId: String)
    suspend fun removeAllProfiles()
}
