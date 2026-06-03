package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.SystemAccountRepository
import javax.inject.Inject

class LogoutAllUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val systemAccountRepository: SystemAccountRepository,
) {

    suspend operator fun invoke() {
        authRepository.getAllLoggedInProfiles().forEach { profile ->
            profile.userInfo?.webId?.let { systemAccountRepository.removeAccount(it) }
        }
        authRepository.removeAllProfiles()
    }
}
