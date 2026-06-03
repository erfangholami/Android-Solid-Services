package com.erfangholami.androidsolidservices.data.remote

import android.content.Intent
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

@Singleton
class AuthRepositoryImplementation @Inject constructor(
    private val authenticator: Authenticator,
) : AuthRepository {

    override val activeProfileFlow: StateFlow<Profile?> = authenticator.activeProfileFlow
    override val loggedInProfilesFlow: StateFlow<List<Profile>> = authenticator.loggedInProfilesFlow
    override val isAuthorizedFlow: StateFlow<Boolean> = authenticator.isAuthorizedFlow
    override val activeWebIdFlow: StateFlow<String?> = authenticator.activeWebIdFlow

    override fun isUserAuthorized(): Boolean = authenticator.isUserAuthorized()

    override fun getAllLoggedInProfiles(): List<Profile> = authenticator.getAllLoggedInProfiles()

    override fun getProfile(webId: String): Profile = authenticator.getProfile(webId)

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String,
    ): Pair<Intent?, String?> =
        authenticator.createAuthenticationIntent(webId, oidcIssuer, appName, redirectUri)

    override suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?,
    ): String? = authenticator.submitAuthorizationResponse(authResponse, authException)

    override suspend fun getActiveWebId(): String? = authenticator.getActiveWebId()

    override suspend fun setActiveWebId(webId: String) = authenticator.setActiveWebId(webId)

    override suspend fun removeProfile(webId: String) = authenticator.removeProfile(webId)

    override suspend fun removeAllProfiles() = authenticator.removeAllProfiles()
}
