package com.erfangholami.androidsolidservices.domain.usecase

import android.content.Intent
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SubmitAuthorizationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(responseData: Intent?): Boolean = withContext(Dispatchers.Default) {
        try {
            authRepository.submitAuthorizationResponse(responseData)
        } catch (_: Exception) {
        }
        authRepository.isUserAuthorized()
    }
}
