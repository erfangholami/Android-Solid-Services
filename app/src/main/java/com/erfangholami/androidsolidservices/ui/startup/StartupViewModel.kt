package com.erfangholami.androidsolidservices.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface StartupEvent {
    data object NavigateToMain : StartupEvent
    data object NavigateToLogin : StartupEvent
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val events = Channel<StartupEvent>(Channel.BUFFERED)
    val eventsFlow = events.receiveAsFlow()

    fun decideStartDestination() {
        viewModelScope.launch {
            val authorized = withContext(Dispatchers.Default) { authRepository.isUserAuthorized() }
            events.send(
                if (authorized) StartupEvent.NavigateToMain else StartupEvent.NavigateToLogin
            )
        }
    }
}
