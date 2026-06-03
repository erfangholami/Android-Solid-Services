package com.erfangholami.androidsolidservices.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.SyncSystemAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val webId: String = "",
    val storages: List<String> = emptyList(),
)

@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val syncSystemAccounts: SyncSystemAccountsUseCase,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = authRepository.activeProfileFlow
        .filterNotNull()
        .map { profile ->
            MainUiState(
                webId = profile.userInfo?.webId ?: "",
                storages = profile.webId?.getStorages()?.map { it.toString() } ?: emptyList(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch { syncSystemAccounts() }
    }
}
