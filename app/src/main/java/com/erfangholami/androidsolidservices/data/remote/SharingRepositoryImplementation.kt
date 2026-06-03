package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.domain.repository.SharingRepository
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingRepositoryImplementation @Inject constructor(
    private val sharingManager: SharingManager,
) : SharingRepository {

    override suspend fun getStoredGivenShares(webId: String): SolidNetworkResponse<List<GivenShare>> =
        sharingManager.getStoredGivenShares(webId)

    override suspend fun refreshGivenShares(webId: String): SolidNetworkResponse<List<GivenShare>> =
        sharingManager.refreshGivenShares(webId)

    override suspend fun rebuildGivenIndex(webId: String): SolidNetworkResponse<List<GivenShare>> =
        sharingManager.rebuildGivenIndex(webId)

    override suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<List<GivenShare>> =
        sharingManager.getGivenSharesForResource(webId, resourceUri)

    override suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidNetworkResponse<GivenShare> =
        sharingManager.createShare(webId, resourceUri, mode, receiver, notifyReceiver)

    override suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<GivenShare> =
        sharingManager.updateShare(webId, resourceUri, mode, receiver)

    override suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<Unit> = sharingManager.revokeShare(webId, resourceUri, receiver)

    override suspend fun getStoredReceivedShares(webId: String): SolidNetworkResponse<List<ReceivedShare>> =
        sharingManager.getStoredReceivedShares(webId)

    override suspend fun refreshReceivedShares(webId: String): SolidNetworkResponse<List<ReceivedShare>> =
        sharingManager.refreshReceivedShares(webId)

    override suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String?,
    ): SolidNetworkResponse<ReceivedShare?> =
        sharingManager.addReceivedShare(webId, resourceUri, ownerHint)

    override suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidNetworkResponse<Unit> =
        sharingManager.removeReceivedShare(webId, resourceUri, ownerWebId)

    override suspend fun getAccessGrants(webId: String): SolidNetworkResponse<List<AccessGrant>> =
        sharingManager.getAccessGrants(webId)

    override suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): SolidNetworkResponse<GivenShare> = sharingManager.acceptShareRequest(webId, request)

    override suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String?,
    ): SolidNetworkResponse<Unit> = sharingManager.rejectShareRequest(webId, request, reason)

    override suspend fun publishCatalogEntry(
        webId: String,
        entry: CatalogEntry,
    ): SolidNetworkResponse<Unit> = sharingManager.publishCatalogEntry(webId, entry)

    override suspend fun removeCatalogEntry(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit> = sharingManager.removeCatalogEntry(webId, resourceUri)

    override suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): SolidNetworkResponse<List<CatalogEntry>> =
        sharingManager.getOwnerCatalog(viewerWebId, ownerWebId)
}
