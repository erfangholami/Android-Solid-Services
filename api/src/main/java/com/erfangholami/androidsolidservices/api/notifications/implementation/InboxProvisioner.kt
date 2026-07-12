package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.access.AcpBackend
import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.access.pickBackend
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import java.net.URI

internal class InboxProvisioner(private val rm: SolidResourceManager) {

    private val wacBackend = WacBackend(rm)
    private val acpBackend = AcpBackend(rm)

    suspend fun podRoot(webId: String): URI {
        val profile = rm.read(webId, URI.create(webId), WebId::class.java).getOrThrow()
        val storage = profile.getStorages().firstOrNull()
            ?: StorageDiscovery.discover(rm, webId)
            ?: error("Could not discover a storage for $webId")
        val root = storage.toString().let { if (it.endsWith("/")) it else "$it/" }
        return URI.create(root)
    }

    suspend fun ensureContainer(webId: String, containerUri: URI) {
        rm.ensureContainer(webId, containerUri).getOrThrow()
    }

    suspend fun grantPublicAppend(webId: String, inboxUri: URI) {
        val metadata = (rm.head(webId, inboxUri) as? SolidResult.Success)?.value
        val backend = if (metadata != null) {
            pickBackend(metadata, inboxUri, wacBackend, acpBackend)
        } else {
            wacBackend
        }
        backend.grant(
            webId = webId,
            resourceUri = inboxUri,
            mode = ShareMode.APPEND,
            receiver = ShareReceiver.Public,
            isContainer = true,
            includeImpliedModes = false,
        )
    }
}
