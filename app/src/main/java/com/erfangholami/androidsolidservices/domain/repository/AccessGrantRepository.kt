package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.domain.model.GrantedApp
import kotlinx.coroutines.flow.Flow

interface AccessGrantRepository {

    fun hasAccessGrant(appPackageName: String, webId: String): Boolean

    suspend fun addAccessGrant(appPackageName: String, appName: String, webId: String)

    suspend fun revokeAccessGrant(appPackageName: String, webId: String)

    fun grantedApplications(): Flow<List<GrantedApp>>
}
