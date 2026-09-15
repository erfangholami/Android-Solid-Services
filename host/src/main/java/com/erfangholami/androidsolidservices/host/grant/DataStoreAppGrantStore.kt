package com.erfangholami.androidsolidservices.host.grant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * An [AppGrantStore] that keeps every grant as one JSON list under one key of a Preferences
 * [DataStore] the host supplies.
 *
 * The list is small (one row per app and account) and read on every guarded call, so one key
 * read whole is cheaper than a table. A value that no longer parses reads as no grants and is
 * reported once, so a damaged store fails closed rather than crashing the host.
 */
public class DataStoreAppGrantStore(
    private val dataStore: DataStore<Preferences>,
) : AppGrantStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AppGrant.serializer())

    override fun grants(): Flow<List<AppGrant>> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun get(packageName: String, webId: String): AppGrant? =
        grants().first().firstOrNull { it.packageName == packageName && it.webId == webId }

    override suspend fun put(grant: AppGrant) {
        rewrite { current ->
            current.filterNot { it.packageName == grant.packageName && it.webId == grant.webId } + grant
        }
    }

    override suspend fun revoke(packageName: String, webId: String) {
        rewrite { current -> current.filterNot { it.packageName == packageName && it.webId == webId } }
    }

    override suspend fun revokeAll(webId: String) {
        rewrite { current -> current.filterNot { it.webId == webId } }
    }

    override suspend fun revokePackage(packageName: String) {
        rewrite { current -> current.filterNot { it.packageName == packageName } }
    }

    private suspend fun rewrite(transform: (List<AppGrant>) -> List<AppGrant>) {
        dataStore.edit { preferences ->
            val current = decode(preferences[KEY])
            val next = transform(current)
            if (next != current) preferences[KEY] = json.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<AppGrant> {
        raw ?: return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: SerializationException) {
            Telemetry.recordException(e, TelemetryAttribute.OPERATION to "host.grants.decode")
            emptyList()
        } catch (e: IllegalArgumentException) {
            Telemetry.recordException(e, TelemetryAttribute.OPERATION to "host.grants.decode")
            emptyList()
        }
    }

    public companion object {

        /** The one Preferences key the grants live under. */
        public val KEY: Preferences.Key<String> = stringPreferencesKey("ass.host.app_grants")
    }
}
