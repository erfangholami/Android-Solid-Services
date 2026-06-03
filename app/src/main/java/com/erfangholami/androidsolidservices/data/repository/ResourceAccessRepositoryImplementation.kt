package com.erfangholami.androidsolidservices.data.repository

import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.repository.ResourceAccessRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceAccessRepositoryImplementation @Inject constructor(
    private val accessGrantRepository: AccessGrantRepository,
) : ResourceAccessRepository {

    override fun hasAccess(webId: String, callerPackageName: String): Boolean {
        return accessGrantRepository.hasAccessGrant(callerPackageName, webId)
    }
}
