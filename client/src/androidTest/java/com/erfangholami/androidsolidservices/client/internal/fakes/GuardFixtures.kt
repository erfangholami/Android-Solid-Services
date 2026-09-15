package com.erfangholami.androidsolidservices.client.internal.fakes

import com.erfangholami.androidsolidservices.host.HostSession
import com.erfangholami.androidsolidservices.host.grant.AppGrantStore
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantEntry
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A session that is always ready and always signed in, so only the grant decides an outcome. */
internal class AlwaysSignedInSession(private val webId: String) : HostSession {

    override suspend fun awaitReady() = Unit

    override suspend fun hasLoggedIn(): Boolean = true

    override suspend fun hasSession(webId: String): Boolean = webId == this.webId

    override suspend fun profileDocument(webId: String): WebId? = null
}

/** Grants held in memory, seeded before the service binds. */
internal class InMemoryGrantStore(initial: List<AppGrant> = emptyList()) : AppGrantStore {

    private val state = MutableStateFlow(initial)

    override fun grants(): Flow<List<AppGrant>> = state

    override suspend fun get(packageName: String, webId: String): AppGrant? =
        state.value.firstOrNull { it.packageName == packageName && it.webId == webId }

    override suspend fun put(grant: AppGrant) {
        state.value = state.value.filterNot {
            it.packageName == grant.packageName && it.webId == grant.webId
        } + grant
    }

    override suspend fun revoke(packageName: String, webId: String) {
        state.value = state.value.filterNot { it.packageName == packageName && it.webId == webId }
    }

    override suspend fun revokeAll(webId: String) {
        state.value = state.value.filterNot { it.webId == webId }
    }

    override suspend fun revokePackage(packageName: String) {
        state.value = state.value.filterNot { it.packageName == packageName }
    }
}

internal fun grantOf(
    packageName: String,
    webId: String,
    level: AccessLevel,
    target: GrantTarget = GrantTarget.Pod,
): AppGrant = AppGrant(
    packageName = packageName,
    webId = webId,
    appLabel = "Instrumented caller",
    entries = listOf(GrantEntry(target, level)),
    grantedAt = "2026-09-15T00:00:00Z",
)
