package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

interface NotificationsRepository {

    suspend fun listNotifications(webId: String): SolidNetworkResponse<List<ShareNotification>>

    suspend fun listRequests(webId: String): SolidNetworkResponse<List<ShareRequest>>

    suspend fun compactInbox(
        webId: String,
        olderThanIso: String? = null,
    ): SolidNetworkResponse<Int>

    suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidNetworkResponse<Unit>

    suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit>

    suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String? = null,
    ): SolidNetworkResponse<Unit>

    suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String? = null,
    ): SolidNetworkResponse<Unit>
}
