package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.SystemAccountRepository
import javax.inject.Inject

class SyncSystemAccountsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val systemAccountRepository: SystemAccountRepository,
) {

    operator fun invoke() {
        val existingWebIds = systemAccountRepository.getAccountWebIds()
        authRepository.getAllLoggedInProfiles().forEach { profile ->
            val webId = profile.userInfo?.webId ?: return@forEach
            if (webId !in existingWebIds) {
                systemAccountRepository.addAccount(webId)
            }
        }
    }
}
