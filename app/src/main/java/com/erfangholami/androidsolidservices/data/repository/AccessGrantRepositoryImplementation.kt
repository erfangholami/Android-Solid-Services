package com.erfangholami.androidsolidservices.data.repository

import com.erfangholami.androidsolidservices.data.local.AccessGrantLocalDataSource
import com.erfangholami.androidsolidservices.domain.model.GrantedApp
import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessGrantRepositoryImplementation @Inject constructor(
    private val accessGrantLocalDataSource: AccessGrantLocalDataSource,
) : AccessGrantRepository {

    override fun hasAccessGrant(appPackageName: String, webId: String): Boolean {
        return accessGrantLocalDataSource.hasAccessGrant(appPackageName, webId)
    }

    override suspend fun addAccessGrant(
        appPackageName: String,
        appName: String,
        webId: String,
    ) {
        accessGrantLocalDataSource.addAccessGrant(appPackageName, appName, webId)
    }

    override suspend fun revokeAccessGrant(appPackageName: String, webId: String) {
        accessGrantLocalDataSource.revokeAccessGrant(appPackageName, webId)
    }

    override fun grantedApplications(): Flow<List<GrantedApp>> {
        return accessGrantLocalDataSource.grantedApplications()
    }
}
