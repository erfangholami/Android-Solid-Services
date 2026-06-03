package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.SystemAccountRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val systemAccountRepository: SystemAccountRepository,
) {

    suspend operator fun invoke(webId: String) {
        authRepository.removeProfile(webId)
        systemAccountRepository.removeAccount(webId)
    }
}
