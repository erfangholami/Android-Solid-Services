package com.erfangholami.androidsolidservices.data.remote

import android.content.Intent
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImplementation @Inject constructor(
    private val authenticator: Authenticator,
) : AuthRepository {

    override val activeProfileFlow: StateFlow<SolidAccount?> = authenticator.activeProfileFlow
    override val loggedInProfilesFlow: StateFlow<List<SolidAccount>> = authenticator.loggedInProfilesFlow
    override val isAuthorizedFlow: StateFlow<Boolean> = authenticator.isAuthorizedFlow
    override val activeWebIdFlow: StateFlow<String?> = authenticator.activeWebIdFlow

    override fun isUserAuthorized(): Boolean = authenticator.isUserAuthorized()

    override fun getAllLoggedInProfiles(): List<SolidAccount> = authenticator.getAllLoggedInProfiles()

    override fun getProfile(webId: String): SolidAccount = authenticator.getProfile(webId)

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String,
        clientId: String?,
    ): Pair<Intent?, String?> =
        authenticator.createAuthenticationIntent(webId, oidcIssuer, appName, redirectUri, clientId)

    override suspend fun submitAuthorizationResponse(responseData: Intent?): String? =
        authenticator.submitAuthorizationResponse(responseData)

    override suspend fun getActiveWebId(): String? = authenticator.getActiveWebId()

    override suspend fun setActiveWebId(webId: String) = authenticator.setActiveWebId(webId)

    override suspend fun removeProfile(webId: String) = authenticator.removeProfile(webId)

    override suspend fun removeAllProfiles() = authenticator.removeAllProfiles()
}
