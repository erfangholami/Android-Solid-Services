package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest

interface NotificationsRepository {

    suspend fun listNotifications(webId: String): SolidResult<List<ShareNotification>>

    suspend fun listRequests(webId: String): SolidResult<List<ShareRequest>>

    suspend fun compactInbox(
        webId: String,
        olderThanIso: String? = null,
    ): SolidResult<Int>

    suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ): SolidResult<Unit>

    suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ): SolidResult<Unit>

    suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String? = null,
    ): SolidResult<Unit>

    suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String? = null,
    ): SolidResult<Unit>
}
