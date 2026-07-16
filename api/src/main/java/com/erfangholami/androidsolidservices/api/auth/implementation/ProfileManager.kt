package com.erfangholami.androidsolidservices.api.auth.implementation

import android.content.Context
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import com.erfangholami.androidsolidservices.shared.model.profile.ProfileList
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import com.erfangholami.androidsolidservices.api.repository.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal class ProfileManager private constructor(
    context: Context,
) {
    companion object {
        @Volatile
        private var INSTANCE: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userRepository: UserRepository = UserRepository.getInstance(context)
    private val initDeferred = CompletableDeferred<Unit>()

    val allProfilesFlow: StateFlow<ProfileList> = userRepository.readAllProfiles()
        .stateIn(scope, SharingStarted.Eagerly, ProfileList())

    val activeWebIdFlow: StateFlow<String?> = userRepository.activeWebIdFlow()
        .stateIn(scope, SharingStarted.Eagerly, null)

    val loggedInProfilesFlow: StateFlow<List<Profile>> = allProfilesFlow
        .map { profileList ->
            profileList.profiles.values.filter {
                it.authState.isAuthorized && it.userInfo != null && it.webId != null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Accounts that were signed in but whose session has terminally expired (a token refresh was
     * rejected with `invalid_grant`/`invalid_client`, so [net.openid.appauth.AuthState] is no
     * longer authorized). Their local state — identity, WebID document, DPoP key — is retained;
     * signing in again with the same WebID restores the account in place.
     */
    val expiredProfilesFlow: StateFlow<List<Profile>> = allProfilesFlow
        .map { profileList ->
            profileList.profiles.values.filter {
                !it.authState.isAuthorized && it.userInfo != null && it.webId != null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeProfileFlow: StateFlow<Profile?> = combine(
        allProfilesFlow,
        activeWebIdFlow,
    ) { profiles, activeId ->
        if (activeId != null) {
            profiles.profiles[activeId]
        } else {
            null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val isAuthorizedFlow: StateFlow<Boolean> = loggedInProfilesFlow
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Public, AppAuth-free projections of [activeProfileFlow] / [loggedInProfilesFlow]. */
    val activeAccountFlow: StateFlow<SolidAccount?> = activeProfileFlow
        .map { it?.toAccount() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val loggedInAccountsFlow: StateFlow<List<SolidAccount>> = loggedInProfilesFlow
        .map { profiles -> profiles.map { it.toAccount() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val expiredAccountsFlow: StateFlow<List<SolidAccount>> = expiredProfilesFlow
        .map { profiles -> profiles.map { it.toAccount() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            userRepository.readAllProfiles().first()
            userRepository.activeWebIdFlow().first()
            initDeferred.complete(Unit)
        }
        scope.launch {
            initDeferred.await()
            combine(allProfilesFlow, activeWebIdFlow) { profiles, activeId ->
                profiles to activeId
            }.collect { (profileList, activeId) ->
                reconcileActiveWebId(profileList, activeId)
            }
        }
    }

    /**
     * Keeps the persisted active WebID pointing at a usable account. When the active account's
     * session expires (or its profile disappears) while another signed-in account remains, the
     * selection moves to that account instead of dangling on one that is no longer authorized —
     * previously the UI listed the remaining signed-in accounts with none of them selected. An
     * expired account stays selected only when no authorized account remains, so a re-login
     * surface can still show which account to restore.
     */
    private suspend fun reconcileActiveWebId(profileList: ProfileList, activeId: String?) {
        val activeProfile = activeId?.let { profileList.profiles[it] }
        val activeIsUsable = activeProfile != null &&
            activeProfile.authState.isAuthorized && activeProfile.userInfo != null
        if (activeIsUsable) return

        val fallback = profileList.profiles.entries.firstOrNull { (_, profile) ->
            profile.authState.isAuthorized && profile.userInfo != null
        }?.key

        when {
            fallback != null -> userRepository.setActiveWebId(fallback)
            activeId != null && activeProfile == null -> userRepository.setActiveWebId(null)
        }
    }

    suspend fun awaitInit() = initDeferred.await()

    private fun ensureInitialized() {
        if (!initDeferred.isCompleted) {
            runBlocking { initDeferred.await() }
        }
    }

    fun isUserAuthorized(): Boolean {
        ensureInitialized()
        return isAuthorizedFlow.value
    }

    fun getAllLoggedInProfiles(): List<Profile> {
        ensureInitialized()
        return loggedInProfilesFlow.value
    }

    fun getActiveWebId(): String? {
        ensureInitialized()
        return activeWebIdFlow.value
    }

    fun getProfileOrNull(webId: String): Profile? {
        ensureInitialized()
        return allProfilesFlow.value.profiles[webId]
    }

    fun getProfile(webId: String): Profile {
        ensureInitialized()
        return allProfilesFlow.value.profiles[webId]
            ?: throw NoSuchElementException("No profile found for WebID: $webId")
    }

    fun getActiveProfile(): Profile {
        ensureInitialized()
        val activeId = activeWebIdFlow.value
        if (activeId != null) {
            val profile = allProfilesFlow.value.profiles[activeId]
            if (profile != null && profile.authState.isAuthorized && profile.userInfo != null) {
                return profile
            }
        }
        return loggedInProfilesFlow.value.firstOrNull()
            ?: throw NoSuchElementException("No authorized profiles exist.")
    }

    suspend fun writeProfile(webId: String, profile: Profile) {
        userRepository.writeProfile(webId, profile)
    }

    suspend fun removeProfile(webId: String) {
        userRepository.removeProfile(webId)
        if (activeWebIdFlow.value == webId) {
            val remaining = allProfilesFlow.value.profiles
                .filter { (key, p) -> key != webId && p.authState.isAuthorized && p.userInfo != null }
            val newActiveId = remaining.keys.firstOrNull()
            userRepository.setActiveWebId(newActiveId)
        }
    }

    suspend fun removeAllProfiles() {
        userRepository.removeAllProfiles()
        userRepository.setActiveWebId(null)
    }

    suspend fun setActiveWebId(webId: String?) {
        if (webId != null) {
            val profile = allProfilesFlow.value.profiles[webId]
            require(profile != null && profile.authState.isAuthorized && profile.userInfo != null) {
                "Cannot set active account: profile for $webId is not authorized or incomplete."
            }
        }
        userRepository.setActiveWebId(webId)
    }
}
