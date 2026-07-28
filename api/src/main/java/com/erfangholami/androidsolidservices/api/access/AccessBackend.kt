package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver

internal interface AccessBackend {

    public suspend fun grant(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        isContainer: Boolean,
        includeImpliedModes: Boolean = true,
    )

    public suspend fun revoke(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
        isContainer: Boolean,
    )

    public suspend fun listShares(
        webId: String,
        resourceUri: String,
    ): List<GivenShare>

    public suspend fun ensureOwnerOnly(
        webId: String,
        targetUri: String,
        isContainer: Boolean,
    )

    public suspend fun reclaimOwnerControl(
        webId: String,
        targetUri: String,
        isContainer: Boolean,
    )
}
