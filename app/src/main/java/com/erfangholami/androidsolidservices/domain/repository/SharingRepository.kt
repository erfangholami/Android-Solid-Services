package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

interface SharingRepository {

    suspend fun getStoredGivenShares(webId: String): SolidNetworkResponse<List<GivenShare>>

    suspend fun refreshGivenShares(webId: String): SolidNetworkResponse<List<GivenShare>>

    suspend fun rebuildGivenIndex(webId: String): SolidNetworkResponse<List<GivenShare>>

    suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<List<GivenShare>>

    suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean = true,
    ): SolidNetworkResponse<GivenShare>

    suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<GivenShare>

    suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<Unit>

    suspend fun getStoredReceivedShares(webId: String): SolidNetworkResponse<List<ReceivedShare>>

    suspend fun refreshReceivedShares(webId: String): SolidNetworkResponse<List<ReceivedShare>>

    suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String? = null,
    ): SolidNetworkResponse<ReceivedShare?>

    suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidNetworkResponse<Unit>

    suspend fun getAccessGrants(webId: String): SolidNetworkResponse<List<AccessGrant>>

    suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): SolidNetworkResponse<GivenShare>

    suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String? = null,
    ): SolidNetworkResponse<Unit>

    suspend fun publishCatalogEntry(webId: String, entry: CatalogEntry): SolidNetworkResponse<Unit>

    suspend fun removeCatalogEntry(webId: String, resourceUri: String): SolidNetworkResponse<Unit>

    suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): SolidNetworkResponse<List<CatalogEntry>>
}
