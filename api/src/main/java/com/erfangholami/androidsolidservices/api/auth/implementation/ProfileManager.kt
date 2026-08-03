package com.erfangholami.androidsolidservices.api.auth.implementation

import android.content.Context
import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.api.auth.ProfileList
import com.erfangholami.androidsolidservices.api.auth.store.UserRepository
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val STORE_READ_RETRIES = 6L

private fun <T> Flow<T>.retryStoreRead(label: String): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= STORE_READ_RETRIES) return@retryWhen false
    Log.w(
        "Authenticator",
        "Profile store read failed for $label (attempt ${attempt + 1}/$STORE_READ_RETRIES, " +
            "${cause.javaClass.simpleName}); retrying",
    )
    delay(minOf(500L shl attempt.toInt().coerceAtMost(5), 15_000L))
    true
}

/**
 * The single reader and single writer of the durable account store.
 *
 * Every mutation persists first and publishes second, inside one write lock, so the state the
 * synchronous getters and every flow project from is exactly what the store holds — there is no
 * cached flow to lag behind a write and nothing for a read-your-writes overlay to paper over.
 * Which accounts count as signed-in or expired is decided by [SessionState], nowhere else.
 */
internal class ProfileManager private constructor(
    context: Context,
) : ProfileStore {
    companion object {
        @Volatile
        private var instance: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return instance ?: synchronized(this) {
                instance ?: ProfileManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userRepository: UserRepository = UserRepository.getInstance(context)
    private val initDeferred = CompletableDeferred<Unit>()
    private val writeMutex = Mutex()

    private val profilesState = MutableStateFlow(ProfileList())
    private val activeState = MutableStateFlow<String?>(null)

    val allProfilesFlow: StateFlow<ProfileList> = profilesState.asStateFlow()

    val activeWebIdFlow: StateFlow<String?> = activeState.asStateFlow()

    val loggedInProfilesFlow: StateFlow<List<Profile>> = profilesState
        .map { profileList ->
            profileList.profiles.values.filter { SessionState.of(it).isSignedIn && it.isComplete }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val expiredProfilesFlow: StateFlow<List<Profile>> = profilesState
        .map { profileList ->
            profileList.profiles.values.filter { SessionState.of(it).isExpired && it.isComplete }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeProfileFlow: StateFlow<Profile?> = combine(
        profilesState,
        activeState,
    ) { profiles, activeId ->
        activeId?.let { profiles.profiles[it] }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val isAuthorizedFlow: StateFlow<Boolean> = loggedInProfilesFlow
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

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
            runCatching {
                profilesState.value =
                    userRepository.readAllProfiles().retryStoreRead("profiles").first()
                activeState.value =
                    userRepository.activeWebIdFlow().retryStoreRead("active-webid").first()
            }.onFailure { failure ->
                Log.e(
                    "Authenticator",
                    "Profile store unreadable at init after retries; starting empty (file kept)",
                    failure,
                )
                Telemetry.recordException(
                    failure,
                    TelemetryAttribute.OPERATION to "solid.auth.profile_store",
                    "auth_error" to "store_init_failed",
                )
            }
            initDeferred.complete(Unit)
        }
        scope.launch {
            initDeferred.await()
            combine(profilesState, activeState) { profiles, activeId ->
                profiles to activeId
            }.collect { (profileList, activeId) ->
                reconcileActiveWebId(profileList, activeId)
            }
        }
    }

    private suspend fun reconcileActiveWebId(profileList: ProfileList, activeId: String?) {
        if (isActiveUsable(profileList, activeId)) return

        val fallback = profileList.profiles.entries.firstOrNull { (_, profile) ->
            SessionState.of(profile).isSignedIn && profile.isComplete
        }?.key

        when {
            fallback != null -> {
                Telemetry.log("solid.auth reconciler moved active (profiles=${profileList.profiles.size})")
                persistActiveWebId(fallback)
            }
            activeId != null && profileList.profiles[activeId] == null -> {
                Telemetry.log("solid.auth reconciler cleared active (profiles=${profileList.profiles.size})")
                persistActiveWebId(null)
            }
        }
    }

    private fun isActiveUsable(profileList: ProfileList, activeId: String?): Boolean {
        val profile = activeId?.let { profileList.profiles[it] } ?: return false
        return SessionState.of(profile).isSignedIn && profile.isComplete
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
        return activeState.value
    }

    override fun getProfileOrNull(webId: String): Profile? {
        ensureInitialized()
        return profilesState.value.profiles[webId]
    }

    fun getProfile(webId: String): Profile {
        ensureInitialized()
        return getProfileOrNull(webId)
            ?: throw NoSuchElementException("No profile found for WebID: $webId")
    }

    fun getActiveProfile(): Profile {
        ensureInitialized()
        val activeId = activeState.value
        if (activeId != null) {
            val profile = profilesState.value.profiles[activeId]
            if (profile != null && SessionState.of(profile).isSignedIn && profile.isComplete) {
                return profile
            }
        }
        return loggedInProfilesFlow.value.firstOrNull()
            ?: throw NoSuchElementException("No authorized profiles exist.")
    }

    override suspend fun writeProfile(webId: String, profile: Profile) {
        writeMutex.withLock {
            userRepository.writeProfile(webId, profile)
            profilesState.value = profilesState.value.copy(
                profiles = profilesState.value.profiles + (webId to profile),
            )
        }
    }

    suspend fun removeProfile(webId: String) {
        writeMutex.withLock {
            userRepository.removeProfile(webId)
            profilesState.value = profilesState.value.copy(
                profiles = profilesState.value.profiles - webId,
            )
            if (activeState.value == webId) {
                val fallback = profilesState.value.profiles.entries.firstOrNull { (_, profile) ->
                    SessionState.of(profile).isSignedIn && profile.isComplete
                }?.key
                persistActiveWebIdLocked(fallback)
            }
        }
    }

    suspend fun removeAllProfiles() {
        writeMutex.withLock {
            userRepository.removeAllProfiles()
            profilesState.value = ProfileList()
            persistActiveWebIdLocked(null)
        }
    }

    suspend fun setActiveWebId(webId: String?) {
        if (webId != null) {
            val profile = getProfileOrNull(webId)
            require(profile != null && SessionState.of(profile).isSignedIn && profile.isComplete) {
                "Cannot set active account: profile for $webId is not authorized or incomplete."
            }
        }
        persistActiveWebId(webId)
    }

    private suspend fun persistActiveWebId(webId: String?) {
        writeMutex.withLock { persistActiveWebIdLocked(webId) }
    }

    private suspend fun persistActiveWebIdLocked(webId: String?) {
        userRepository.setActiveWebId(webId)
        activeState.value = webId
    }
}
