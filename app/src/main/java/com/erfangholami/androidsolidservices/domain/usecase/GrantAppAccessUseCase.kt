package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import javax.inject.Inject

class GrantAppAccessUseCase @Inject constructor(
    private val accessGrantRepository: AccessGrantRepository,
) {

    suspend operator fun invoke(callerPackage: String, callerName: String, webId: String) {
        accessGrantRepository.addAccessGrant(callerPackage, callerName, webId)
    }
}
