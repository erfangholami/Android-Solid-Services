package com.erfangholami.androidsolidservices.api.repository.implementation

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import com.erfangholami.androidsolidservices.shared.model.profile.ProfileList
import com.erfangholami.androidsolidservices.shared.model.profile.contains
import com.erfangholami.androidsolidservices.api.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal class UserRepositoryImplementation private constructor(
    private val context: Context,
) : UserRepository {

    companion object {

        private const val PROFILES_FILE_NAME = "profiles.json"
        private const val PREFERENCES_FILE_NAME = "user_preferences"
        private val ACTIVE_WEB_ID_KEY = stringPreferencesKey("active_web_id")

        @Volatile
        private var INSTANCE: UserRepository? = null

        object ProfileListSerializer : Serializer<ProfileList> {
            override val defaultValue: ProfileList
                get() = ProfileList()

            override suspend fun readFrom(input: InputStream): ProfileList {
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return ProfileList()
                val json = try {
                    KeystoreCipher.decrypt(bytes).decodeToString()
                } catch (e: Exception) {
                    Log.w(
                        "Authenticator",
                        "AuthTrace: profile store decrypt failed (${e.javaClass.simpleName}: ${e.message}) — " +
                            "falling back to plaintext parse",
                    )
                    bytes.decodeToString()
                }
                return try {
                    Json.decodeFromString<ProfileList>(json)
                } catch (serialization: SerializationException) {
                    Log.e(
                        "Authenticator",
                        "AuthTrace: profile store UNREADABLE — the corruption handler will now REPLACE it " +
                            "with an empty store, signing every account out",
                        serialization,
                    )
                    throw CorruptionException("Unable to read profiles", serialization)
                }
            }

            override suspend fun writeTo(
                t: ProfileList,
                output: OutputStream
            ) {
                withContext(Dispatchers.IO) {
                    output.write(
                        KeystoreCipher.encrypt(Json.encodeToString(t).encodeToByteArray())
                    )
                }
            }
        }

        fun getInstance(
            context: Context,
        ): UserRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepositoryImplementation(context).also { INSTANCE = it }
            }
        }
    }

    private val Context.profilesDataStore: DataStore<ProfileList> by dataStore(
        fileName = PROFILES_FILE_NAME,
        serializer = ProfileListSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { ProfileList() },
    )

    private val Context.preferencesDataStore by preferencesDataStore(
        name = PREFERENCES_FILE_NAME
    )

    override fun readAllProfiles(): Flow<ProfileList> {
        return context.profilesDataStore.data
    }

    override fun activeWebIdFlow(): Flow<String?> {
        return context.preferencesDataStore.data.map { it[ACTIVE_WEB_ID_KEY] }
    }

    override suspend fun writeProfile(webId: String, profile: Profile) {
        context.profilesDataStore.updateData {
            it.copy(profiles = it.profiles.toMutableMap().apply {
                put(webId, profile)
            })
        }
    }

    override suspend fun removeProfile(webId: String) {
        context.profilesDataStore.updateData {
            if (it.contains(webId)) {
                it.copy(profiles = it.profiles.toMutableMap().apply {
                    remove(webId)
                })
            } else {
                it
            }
        }
    }

    override suspend fun removeAllProfiles() {
        context.profilesDataStore.updateData {
            ProfileList()
        }
    }

    override suspend fun getActiveWebId(): String? {
        return context.preferencesDataStore.data
            .map { it[ACTIVE_WEB_ID_KEY] }
            .first()
    }

    override suspend fun setActiveWebId(webId: String?) {
        context.preferencesDataStore.edit { prefs ->
            if (webId != null) {
                prefs[ACTIVE_WEB_ID_KEY] = webId
            } else {
                prefs.remove(ACTIVE_WEB_ID_KEY)
            }
        }
    }
}