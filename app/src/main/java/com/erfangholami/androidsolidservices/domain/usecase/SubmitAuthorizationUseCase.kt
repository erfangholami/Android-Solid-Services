package com.erfangholami.androidsolidservices.domain.usecase

import android.content.Intent
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import javax.inject.Inject

class SubmitAuthorizationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(responseData: Intent?): Boolean {
        try {
            authRepository.submitAuthorizationResponse(responseData)
        } catch (_: Exception) {
        }
        return authRepository.isUserAuthorized()
    }
}
