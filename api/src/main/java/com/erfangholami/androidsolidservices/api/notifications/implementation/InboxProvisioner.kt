package com.erfangholami.androidsolidservices.api.notifications.implementation

import com.erfangholami.androidsolidservices.api.access.AccessBackend
import com.erfangholami.androidsolidservices.api.access.AcpBackend
import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.access.pickBackend
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.result.SolidResult

internal class InboxProvisioner(
    private val rm: SolidResourceManager,
    private val backendFor: suspend (webId: String, resourceUri: String) -> AccessBackend =
        defaultBackendFor(rm),
) {

    suspend fun podRoot(webId: String): String {
        val profile = rm.read(webId, webId, WebId::class.java).getOrThrow()
        val storage = profile.getStorages().firstOrNull()
            ?: StorageDiscovery.discover(rm, webId)
            ?: error("Could not discover a storage for $webId")
        return if (storage.endsWith("/")) storage else "$storage/"
    }

    suspend fun ensureContainer(webId: String, containerUri: String) {
        rm.ensureContainer(webId, containerUri).getOrThrow()
    }

    suspend fun grantPublicAppend(webId: String, inboxUri: String) {
        backendFor(webId, inboxUri).grant(
            webId = webId,
            resourceUri = inboxUri,
            mode = ShareMode.APPEND,
            receiver = ShareReceiver.Public,
            isContainer = true,
            includeImpliedModes = false,
        )
    }

    /**
     * Makes [docUri] readable by anyone unless it already is, so a link it advertises — the
     * `ldp:inbox` of a WebID whose own document is read-only, as on Inrupt — can be discovered
     * by senders. Returns `true` when a grant was written, `false` when the document was public
     * already.
     */
    suspend fun ensurePublicRead(webId: String, docUri: String): Boolean {
        if (rm.headPublic(docUri) is SolidResult.Success) return false
        backendFor(webId, docUri).grant(
            webId = webId,
            resourceUri = docUri,
            mode = ShareMode.READ,
            receiver = ShareReceiver.Public,
            isContainer = false,
            includeImpliedModes = false,
        )
        return true
    }

    companion object {
        fun defaultBackendFor(
            rm: SolidResourceManager,
        ): suspend (webId: String, resourceUri: String) -> AccessBackend {
            val wac = WacBackend(rm)
            val acp = AcpBackend(rm)
            return { webId, resourceUri ->
                val metadata = (rm.head(webId, resourceUri) as? SolidResult.Success)?.value
                if (metadata != null) pickBackend(metadata, resourceUri, wac, acp) else wac
            }
        }
    }
}
