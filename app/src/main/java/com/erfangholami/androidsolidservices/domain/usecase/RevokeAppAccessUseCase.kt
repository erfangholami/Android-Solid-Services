package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import javax.inject.Inject

class RevokeAppAccessUseCase @Inject constructor(
    private val accessGrantRepository: AccessGrantRepository,
) {

    suspend operator fun invoke(callerPackage: String, webId: String) {
        accessGrantRepository.revokeAccessGrant(callerPackage, webId)
    }
}
