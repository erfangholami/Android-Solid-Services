package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.domain.repository.NotificationsRepository
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepositoryImplementation @Inject constructor(
    private val notificationsManager: NotificationsManager,
) : NotificationsRepository {

    override suspend fun listNotifications(webId: String): SolidNetworkResponse<List<ShareNotification>> =
        notificationsManager.listNotifications(webId)

    override suspend fun listRequests(webId: String): SolidNetworkResponse<List<ShareRequest>> =
        notificationsManager.listRequests(webId)

    override suspend fun compactInbox(
        webId: String,
        olderThanIso: String?,
    ): SolidNetworkResponse<Int> = notificationsManager.compactInbox(webId, olderThanIso)

    override suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidNetworkResponse<Unit> =
        notificationsManager.sendOffer(ownerWebId, receiverWebId, resourceUri, mode)

    override suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit> =
        notificationsManager.sendUndo(ownerWebId, receiverWebId, resourceUri)

    override suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String?,
    ): SolidNetworkResponse<Unit> =
        notificationsManager.sendRequest(requesterWebId, ownerWebId, resourceUri, requestedMode, summary)

    override suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String?,
    ): SolidNetworkResponse<Unit> =
        notificationsManager.sendReject(ownerWebId, requesterWebId, resourceUri, reason)
}
