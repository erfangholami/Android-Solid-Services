package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InProgressAuthStore {
    private val _state = MutableStateFlow<Profile?>(null)
    val state: StateFlow<Profile?> = _state.asStateFlow()

    fun get(): Profile? = _state.value
    fun set(profile: Profile) {
        _state.value = profile
    }

    fun clear() {
        _state.value = null
    }
}
