package com.erfangholami.androidsolidservices.host.grant

import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The host's record of what each app may do as each WebID.
 *
 * One grant per `(packageName, webId)`: [put] replaces any earlier grant for the pair. Grants
 * are local to the device by design; nothing about them reaches the pod.
 */
public interface AppGrantStore {

    /** Every grant, live; emits again on each change. */
    public fun grants(): Flow<List<AppGrant>>

    public suspend fun get(packageName: String, webId: String): AppGrant?

    /** Writes [grant], replacing the grant its package holds for its WebID. */
    public suspend fun put(grant: AppGrant)

    public suspend fun revoke(packageName: String, webId: String)

    /** Drops every grant for [webId]; the hook for signing that account out. */
    public suspend fun revokeAll(webId: String)

    /** Drops every grant [packageName] holds; the hook for an uninstalled app. */
    public suspend fun revokePackage(packageName: String)
}

/** The grants for one WebID, live. */
public fun AppGrantStore.grantsFor(webId: String): Flow<List<AppGrant>> =
    grants().map { all -> all.filter { it.webId == webId } }
