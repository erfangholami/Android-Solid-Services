package com.erfangholami.androidsolidservices.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.androidsolidservices.domain.model.GrantedApp
import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.usecase.RevokeAppAccessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessGrantUiState(
    val grantedApps: List<GrantedApp> = emptyList(),
)

@HiltViewModel
class AccessGrantViewModel @Inject constructor(
    accessGrantRepository: AccessGrantRepository,
    private val revokeAppAccess: RevokeAppAccessUseCase,
) : ViewModel() {

    val uiState: StateFlow<AccessGrantUiState> =
        accessGrantRepository.grantedApplications()
            .map { AccessGrantUiState(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                AccessGrantUiState(),
            )

    fun revokeAccess(app: GrantedApp) {
        viewModelScope.launch {
            revokeAppAccess(app.packageName, app.webId)
        }
    }
}
