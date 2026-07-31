package com.erfangholami.androidsolidservices.api.repository

import android.content.Context
import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.api.auth.ProfileList
import com.erfangholami.androidsolidservices.api.repository.implementation.UserRepositoryImplementation
import kotlinx.coroutines.flow.Flow

internal interface UserRepository {

    companion object {
        fun getInstance(context: Context): UserRepository =
            UserRepositoryImplementation.getInstance(context)
    }

    fun readAllProfiles(): Flow<ProfileList>
    fun activeWebIdFlow(): Flow<String?>
    suspend fun writeProfile(webId: String, profile: Profile)
    suspend fun removeProfile(webId: String)
    suspend fun removeAllProfiles()
    suspend fun getActiveWebId(): String?
    suspend fun setActiveWebId(webId: String?)
}
