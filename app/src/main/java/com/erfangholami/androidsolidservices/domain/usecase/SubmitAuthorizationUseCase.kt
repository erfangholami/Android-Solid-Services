package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import javax.inject.Inject
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class SubmitAuthorizationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {

    suspend operator fun invoke(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?,
    ): Boolean {
        try {
            authRepository.submitAuthorizationResponse(authResponse, authException)
        } catch (_: Exception) {
        }
        return authRepository.isUserAuthorized()
    }
}
