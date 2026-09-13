package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.access.AccessBackend
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver

internal data class RecordedGrant(
    val resourceUri: String,
    val mode: ShareMode,
    val receiver: ShareReceiver,
    val isContainer: Boolean,
    val includeImpliedModes: Boolean,
)

internal class RecordingAccessBackend : AccessBackend {

    val grants: MutableList<RecordedGrant> = mutableListOf()

    override suspend fun grant(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        isContainer: Boolean,
        includeImpliedModes: Boolean,
    ) {
        grants += RecordedGrant(resourceUri, mode, receiver, isContainer, includeImpliedModes)
    }

    override suspend fun revoke(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
        isContainer: Boolean,
    ): Unit = error("not exercised by this test")

    override suspend fun listShares(webId: String, resourceUri: String): List<GivenShare> =
        emptyList()

    override suspend fun ensureOwnerOnly(
        webId: String,
        targetUri: String,
        isContainer: Boolean,
    ): Unit = error("not exercised by this test")

    override suspend fun reclaimOwnerControl(
        webId: String,
        targetUri: String,
        isContainer: Boolean,
    ): Unit = error("not exercised by this test")
}
