package com.erfangholami.androidsolidservices.api.auth.store

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
import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.api.auth.ProfileList
import com.erfangholami.androidsolidservices.api.auth.contains
import com.erfangholami.androidsolidservices.api.auth.store.UserRepository
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class UserRepositoryImplementation private constructor(
    private val context: Context,
) : UserRepository {

    companion object {

        private const val PROFILES_FILE_NAME = "profiles.json"
        private const val PREFERENCES_FILE_NAME = "user_preferences"
        private const val FAILURE_MARKER_FILE_NAME = "profile_store_read_failures"
        private const val STORE_FAILURE_LIMIT = 3
        private val ACTIVE_WEB_ID_KEY = stringPreferencesKey("active_web_id")

        @Volatile
        private var instance: UserRepository? = null

        object ProfileListSerializer : Serializer<ProfileList> {

            @Volatile
            internal var failureMarker: File? = null

            override val defaultValue: ProfileList
                get() = ProfileList()

            override suspend fun readFrom(input: InputStream): ProfileList {
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return ProfileList()
                val json = try {
                    KeystoreCipher.decrypt(bytes).decodeToString().also { clearDecryptFailures() }
                } catch (e: Exception) {
                    if (bytes.first() == '{'.code.toByte()) {
                        Log.w(
                            "Authenticator",
                            "Profile store decrypt failed (${e.javaClass.simpleName}); " +
                                "parsing as legacy plaintext store",
                        )
                        bytes.decodeToString()
                    } else {
                        val strikes = bumpDecryptFailures()
                        Telemetry.recordException(
                            e,
                            TelemetryAttribute.OPERATION to "solid.auth.profile_store",
                            "auth_error" to "store_decrypt_failed",
                            "strikes" to strikes.toString(),
                        )
                        if (strikes >= STORE_FAILURE_LIMIT) {
                            Log.e(
                                "Authenticator",
                                "Profile store undecryptable after $strikes separate reads; " +
                                    "conceding corruption so the store is quarantined",
                                e,
                            )
                            clearDecryptFailures()
                            throw CorruptionException("Profile store undecryptable after $strikes reads", e)
                        }
                        Log.w(
                            "Authenticator",
                            "Profile store decrypt failed (strike $strikes/$STORE_FAILURE_LIMIT, " +
                                "${e.javaClass.simpleName}); treating as transient and keeping the file",
                            e,
                        )
                        throw IOException(
                            "Profile store temporarily unreadable (decrypt strike $strikes/$STORE_FAILURE_LIMIT)",
                            e,
                        )
                    }
                }
                return try {
                    Json.decodeFromString<ProfileList>(json)
                } catch (serialization: SerializationException) {
                    Telemetry.recordException(
                        serialization,
                        TelemetryAttribute.OPERATION to "solid.auth.profile_store",
                        "auth_error" to "store_corrupt",
                    )
                    Log.e(
                        "Authenticator",
                        "Profile store unparseable; conceding corruption so the store is quarantined",
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

            private fun bumpDecryptFailures(): Int = try {
                val marker = failureMarker ?: return 1
                val count = (marker.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0) + 1
                marker.writeText(count.toString())
                count
            } catch (_: Exception) {
                1
            }

            private fun clearDecryptFailures() {
                try {
                    failureMarker?.delete()
                } catch (_: Exception) {
                }
            }
        }

        internal fun resetForTest() {
            instance = null
        }

        fun getInstance(
            context: Context,
        ): UserRepository {
            return instance ?: synchronized(this) {
                instance ?: UserRepositoryImplementation(context).also {
                    ProfileListSerializer.failureMarker =
                        File(context.applicationContext.filesDir, FAILURE_MARKER_FILE_NAME)
                    instance = it
                }
            }
        }
    }

    private val Context.profilesDataStore: DataStore<ProfileList> by dataStore(
        fileName = PROFILES_FILE_NAME,
        serializer = ProfileListSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { corruption ->
            val quarantined = quarantineProfileStore()
            Telemetry.recordException(
                corruption,
                TelemetryAttribute.OPERATION to "solid.auth.profile_store",
                "auth_error" to "store_quarantined",
                "preserved" to (quarantined != null).toString(),
            )
            Log.e(
                "Authenticator",
                "Profile store unreadable; the original was set aside as " +
                    "${quarantined?.name ?: "(copy failed)"} and every account must sign in again",
                corruption,
            )
            ProfileList()
        },
    )

    private fun quarantineProfileStore(): File? = runCatching {
        val original = File(context.filesDir, "datastore/$PROFILES_FILE_NAME")
        if (!original.exists()) return null
        val target = File(original.parentFile, "$PROFILES_FILE_NAME.quarantined.${System.currentTimeMillis()}")
        original.copyTo(target, overwrite = true)
        target
    }.getOrNull()

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
