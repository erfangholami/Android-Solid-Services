package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

interface SharingRepository {

    suspend fun getStoredGivenShares(webId: String): SolidResult<List<GivenShare>>

    suspend fun refreshGivenShares(webId: String): SolidResult<List<GivenShare>>

    suspend fun rebuildGivenIndex(webId: String): SolidResult<List<GivenShare>>

    suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidResult<List<GivenShare>>

    suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean = true,
    ): SolidResult<GivenShare>

    suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): SolidResult<GivenShare>

    suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidResult<Unit>

    suspend fun getStoredReceivedShares(webId: String): SolidResult<List<ReceivedShare>>

    suspend fun refreshReceivedShares(webId: String): SolidResult<List<ReceivedShare>>

    suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String? = null,
    ): SolidResult<ReceivedShare?>

    suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidResult<Unit>

    suspend fun getAccessGrants(webId: String): SolidResult<List<AccessGrant>>

    suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): SolidResult<GivenShare>

    suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String? = null,
    ): SolidResult<Unit>

    suspend fun publishCatalogEntry(webId: String, entry: CatalogEntry): SolidResult<Unit>

    suspend fun removeCatalogEntry(webId: String, resourceUri: String): SolidResult<Unit>

    suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): SolidResult<List<CatalogEntry>>
}
