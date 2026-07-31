package com.erfangholami.androidsolidservices.ui.login

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.usecase.SubmitAuthorizationUseCase
import com.erfangholami.androidsolidservices.ui.navigation.Login
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val isAddingAccount: Boolean = false,
)

sealed interface LoginEvent {
    data class LaunchBrowser(val intent: Intent) : LoginEvent
    data object NavigateToMain : LoginEvent
    /** [webId] is the account just added, for the system add-account flow to report back. */
    data class NavigateBack(val webId: String? = null) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val submitAuthorization: SubmitAuthorizationUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val isAddingAccount: Boolean = savedStateHandle.toRoute<Login>().isAddingAccount

    private val _uiState = MutableStateFlow(LoginUiState(isAddingAccount = isAddingAccount))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val events = Channel<LoginEvent>(Channel.BUFFERED)
    val eventsFlow = events.receiveAsFlow()

    companion object {
        private const val APP_NAME = "Android Solid Service"
        private const val AUTH_APP_REDIRECT_URL =
            "com.erfangholami.androidsolidservices:/oauth2redirect"

        // Identify with a hosted Client ID Document rather than registering dynamically with each
        // provider: a static identity never expires, while a dynamic registration does — Inrupt
        // discards them after 24 hours, and the refresh token dies with the registration. The
        // document is served from the documentation site and lives at docs/client.jsonld.
        private const val CLIENT_ID_DOCUMENT =
            "https://androidsolidservices.erfangholami.com/client.jsonld"

        private const val OIDC_ISSUER_INRUPT_COM = "https://login.inrupt.com"
        private const val OIDC_ISSUER_SOLID_COMMUNITY = "https://solidcommunity.net"
    }

    fun onStart() {
        if (!isAddingAccount && authRepository.isUserAuthorized()) {
            viewModelScope.launch { events.send(LoginEvent.NavigateToMain) }
        }
    }

    fun loginWithWebId(webId: String) = startLogin(webId = webId)

    fun loginWithInruptCom() = startLogin(oidcIssuer = OIDC_ISSUER_INRUPT_COM)

    fun loginWithSolidCommunity() = startLogin(oidcIssuer = OIDC_ISSUER_SOLID_COMMUNITY)

    fun loginWithCustomIssuer(issuerUrl: String) = startLogin(oidcIssuer = issuerUrl)

    private fun startLogin(
        webId: String? = null,
        oidcIssuer: String? = null,
    ) = launchLogin {
        authRepository.createAuthenticationIntent(
            webId = webId,
            oidcIssuer = oidcIssuer,
            appName = APP_NAME,
            redirectUri = AUTH_APP_REDIRECT_URL,
            clientId = CLIENT_ID_DOCUMENT,
        )
    }

    fun submitAuthorizationResponse(responseData: Intent?) {
        viewModelScope.launch {
            val authorized = submitAuthorization(responseData)
            _uiState.update { it.copy(loading = false) }
            if (authorized) {
                _uiState.update { it.copy(errorMessage = null) }
                events.send(
                    if (isAddingAccount) {
                        LoginEvent.NavigateBack(authRepository.activeWebIdFlow.value)
                    } else {
                        LoginEvent.NavigateToMain
                    },
                )
            } else {
                _uiState.update { it.copy(errorMessage = "A problem during login occurred!") }
            }
        }
    }

    private fun launchLogin(block: suspend () -> Pair<Intent?, String?>) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val (intent, error) = block()
                if (intent != null) {
                    events.send(LoginEvent.LaunchBrowser(intent))
                } else {
                    _uiState.update { it.copy(loading = false, errorMessage = error) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = e.message ?: "Login failed"
                    )
                }
            }
        }
    }
}
