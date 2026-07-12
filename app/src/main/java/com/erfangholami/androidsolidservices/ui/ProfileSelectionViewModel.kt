package com.erfangholami.androidsolidservices.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.GrantAppAccessUseCase
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileSelectionUiState(
    val profiles: List<SolidAccount> = emptyList(),
)

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val grantAppAccess: GrantAppAccessUseCase,
) : ViewModel() {

    val uiState: StateFlow<ProfileSelectionUiState> = authRepository.loggedInProfilesFlow
        .map { ProfileSelectionUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileSelectionUiState())

    fun grant(callerPackage: String, callerName: String, webId: String) {
        viewModelScope.launch {
            grantAppAccess(callerPackage, callerName, webId)
        }
    }
}
