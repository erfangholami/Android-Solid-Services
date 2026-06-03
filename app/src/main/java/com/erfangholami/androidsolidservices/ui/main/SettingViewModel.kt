package com.erfangholami.androidsolidservices.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.LogoutAllUseCase
import com.erfangholami.androidsolidservices.domain.usecase.LogoutUseCase
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingUiState(
    val accounts: List<Profile> = emptyList(),
    val activeWebId: String = "",
    val logoutLoading: Boolean = false,
)

sealed interface SettingEvent {
    data object NavigateToLogin : SettingEvent
}

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val logout: LogoutUseCase,
    private val logoutAll: LogoutAllUseCase,
) : ViewModel() {

    private val logoutLoading = MutableStateFlow(false)

    val uiState: StateFlow<SettingUiState> = combine(
        authRepository.loggedInProfilesFlow,
        authRepository.activeWebIdFlow,
        logoutLoading,
    ) { accounts, activeWebId, loading ->
        SettingUiState(
            accounts = accounts,
            activeWebId = activeWebId ?: "",
            logoutLoading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingUiState())

    private val events = Channel<SettingEvent>(Channel.BUFFERED)
    val eventsFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.isAuthorizedFlow.collect { authorized ->
                if (!authorized) events.send(SettingEvent.NavigateToLogin)
            }
        }
    }

    fun switchAccount(webId: String) {
        viewModelScope.launch { authRepository.setActiveWebId(webId) }
    }

    fun logout() {
        viewModelScope.launch {
            logoutLoading.value = true
            logout(uiState.value.activeWebId)
            logoutLoading.value = false
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            logoutLoading.value = true
            logoutAll()
            logoutLoading.value = false
        }
    }
}
