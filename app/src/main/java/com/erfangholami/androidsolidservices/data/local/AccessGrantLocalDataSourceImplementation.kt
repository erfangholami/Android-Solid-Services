package com.erfangholami.androidsolidservices.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.erfangholami.androidsolidservices.data.local.AccessGrantLocalDataSourceImplementation.PreferencesKeys.APP_LIST_KEY
import com.erfangholami.androidsolidservices.domain.model.GrantedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessGrantLocalDataSourceImplementation @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AccessGrantLocalDataSource {

    private object PreferencesKeys {
        val APP_LIST_KEY = stringPreferencesKey("granted_app_list")
    }

    override fun hasAccessGrant(appPackageName: String, webId: String): Boolean {
        return runBlocking {
            withContext(Dispatchers.IO) {
                grantedApplications().map { list ->
                    list.find { it.packageName == appPackageName && it.webId == webId }
                }.first() != null
            }
        }
    }

    override suspend fun addAccessGrant(
        appPackageName: String,
        appName: String,
        webId: String
    ) {
       dataStore.edit {
           val apps = Json.decodeFromString<List<GrantedApp>>(it[APP_LIST_KEY] ?: "[]")
           if (apps.none { it.packageName == appPackageName && it.webId == webId }) {
               it[APP_LIST_KEY] = Json.encodeToString(apps + GrantedApp(appPackageName, appName, webId))
           }
       }
    }

    override suspend fun revokeAccessGrant(appPackageName: String, webId: String) {
        dataStore.edit {
            val apps = Json.decodeFromString<List<GrantedApp>>(it[APP_LIST_KEY] ?: "[]")
            val newList = apps.filter { !(it.packageName == appPackageName && it.webId == webId) }
            if (newList.size != apps.size) {
                it[APP_LIST_KEY] = Json.encodeToString(newList)
            }
        }
    }

    override fun grantedApplications(): Flow<List<GrantedApp>> {
        return dataStore.data.map {
            Json.decodeFromString<List<GrantedApp>>(it[APP_LIST_KEY] ?: "[]")
        }
    }
}
