package com.erfangholami.androidsolidservices.api.sharing.implementation

import com.erfangholami.androidsolidservices.api.access.AccessBackend
import com.erfangholami.androidsolidservices.api.access.AcpBackend
import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.access.pickBackend
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.CATALOG_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.GIVEN_SHARES_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.RECEIVED_SHARES_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.SHARES_CONTAINER_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.SOLIDSHARE_CONTAINER_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.rdf.sharing.GivenSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ReceivedSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-level helpers for the sharing pipeline:
 *
 * - pod-root resolution
 * - container and index file bootstrap
 * - container detection (via `Link: rel="type" <ldp:BasicContainer>`)
 * - delegation to an [AccessBackend] for grant / revoke / list
 * - N3-Patch driven updates of the given/received indexes, including a
 *   `vcard:Group` disambiguation marker for group receiver rows
 *
 * All methods either succeed or throw — callers wrap them in
 * [SolidNetworkResponse] at the public boundary.
 */
internal class SharingManagerHelper {

    companion object {
        private const val MAX_INDEX_PATCH_ATTEMPTS = 3

        @Volatile
        private var INSTANCE: SharingManagerHelper? = null

        fun getInstance(authenticator: Authenticator): SharingManagerHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerHelper(SolidResourceManager.getInstance(authenticator))
                    .also { INSTANCE = it }
            }

        fun getInstance(resourceManager: SolidResourceManager): SharingManagerHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerHelper(resourceManager).also { INSTANCE = it }
            }
    }

    val rm: SolidResourceManager
    private val wacBackend: WacBackend
    private val acpBackend: AcpBackend

    private val podRootCache = ConcurrentHashMap<String, URI>()
    private val sharesContainerReady = ConcurrentHashMap<String, Boolean>()

    private constructor(resourceManager: SolidResourceManager) {
        this.rm = resourceManager
        this.wacBackend = WacBackend(rm)
        this.acpBackend = AcpBackend(rm)
    }

    suspend fun getPodRoot(webId: String): URI {
        podRootCache[webId]?.let { return it }
        val profile = rm.read(webId, URI.create(webId), WebId::class.java).getOrThrow()
        val storage = profile.getStorages().firstOrNull()
            ?: error("WebID profile has no pim:storage entry")
        return URI.create(storage.toString().ensureTrailingSlash())
            .also { podRootCache[webId] = it }
    }

    fun givenSharesUri(podRoot: URI): URI =
        URI.create("${podRoot}${SHARES_CONTAINER_NAME}${GIVEN_SHARES_FILE_NAME}")

    fun receivedSharesUri(podRoot: URI): URI =
        URI.create("${podRoot}${SHARES_CONTAINER_NAME}${RECEIVED_SHARES_FILE_NAME}")

    fun sharesContainerUri(podRoot: URI): URI =
        URI.create("${podRoot}${SHARES_CONTAINER_NAME}")

    fun solidshareContainerUri(podRoot: URI): URI =
        URI.create("${podRoot}${SOLIDSHARE_CONTAINER_NAME}")

    fun catalogUri(podRoot: URI): URI =
        URI.create("${podRoot}${SOLIDSHARE_CONTAINER_NAME}${CATALOG_FILE_NAME}")

    suspend fun ensurePrivateSharesContainer(webId: String, podRoot: URI) {
        if (sharesContainerReady[webId] == true) return
        ensureContainer(webId, solidshareContainerUri(podRoot))
        val containerUri = sharesContainerUri(podRoot)
        ensureContainer(webId, containerUri)
        backendFor(webId, containerUri).ensureOwnerOnly(
            webId = webId, targetUri = containerUri, isContainer = true,
        )
        ensureEmptyRdf(webId, givenSharesUri(podRoot))
        ensureEmptyRdf(webId, receivedSharesUri(podRoot))
        sharesContainerReady[webId] = true
    }

    suspend fun ensureSolidshareContainer(webId: String, podRoot: URI) {
        ensureContainer(webId, solidshareContainerUri(podRoot))
    }

    /** Creates the LDN inbox container if it doesn't already exist. */
    suspend fun ensureInboxContainer(webId: String, inboxUri: URI) {
        ensureContainer(webId, inboxUri)
    }

    private suspend fun ensureContainer(webId: String, containerUri: URI) {
        when (val head = rm.head(webId, containerUri)) {
            is SolidNetworkResponse.Success -> return
            is SolidNetworkResponse.Error ->
                if (!head.isMissing()) error(
                    "HEAD $containerUri failed: ${head.errorCode} ${head.errorMessage}",
                )

            is SolidNetworkResponse.Exception -> throw head.exception
        }
        rm.create(webId, SolidContainer(containerUri)).getOrThrow()
    }

    private suspend fun ensureEmptyRdf(webId: String, uri: URI) {
        when (val head = rm.head(webId, uri)) {
            is SolidNetworkResponse.Success -> return
            is SolidNetworkResponse.Error ->
                if (!head.isMissing()) error(
                    "HEAD $uri failed: ${head.errorCode} ${head.errorMessage}",
                )

            is SolidNetworkResponse.Exception -> throw head.exception
        }
        rm.create(
            webId,
            GivenSharesIndexRDF(
                identifier = uri,
                contentType = "application/ld+json",
                quads = null,
                headers = null,
            ),
        ).getOrThrow()
    }

    private fun SolidNetworkResponse.Error<*>.isMissing(): Boolean =
        errorCode == 404 || errorCode == 410

    /**
     * Returns `true` if the server advertises [resourceUri] as an LDP
     * container (BasicContainer or any subtype). Used to choose between
     * `acl:accessTo` and `acl:default` when authoring an authorization.
     */
    suspend fun isContainer(webId: String, resourceUri: URI): Boolean {
        val head = rm.head(webId, resourceUri)
        if (head !is SolidNetworkResponse.Success) {
            return resourceUri.toString().endsWith("/")
        }
        return head.data.isContainer()
    }

    private fun SolidMetadata.isContainer(): Boolean {
        val containerTypes = setOf(
            LDP.BASIC_CONTAINER,
            LDP.CONTAINER,
            LDP.DIRECT_CONTAINER,
            LDP.INDIRECT_CONTAINER,
        )
        return linkTypes.any { it.toString() in containerTypes }
    }

    /**
     * Picks the right [AccessBackend] for [resourceUri] based on the Link
     * headers returned by HEAD. Defaults to WAC if HEAD fails so the caller
     * always gets a non-throwing reference.
     */
    suspend fun backendFor(webId: String, resourceUri: URI): AccessBackend {
        val metadata = when (val head = rm.head(webId, resourceUri)) {
            is SolidNetworkResponse.Success -> head.data
            else -> return wacBackend
        }
        return pickBackend(metadata, resourceUri, wacBackend, acpBackend)
    }

    suspend fun grantAccess(
        webId: String,
        resourceUri: URI,
        mode: ShareMode,
        receiver: ShareReceiver,
    ) {
        val metadata = (rm.head(webId, resourceUri) as? SolidNetworkResponse.Success)?.data
        val isContainer = metadata?.isContainer() ?: resourceUri.toString().endsWith("/")
        val backend = if (metadata != null) {
            pickBackend(metadata, resourceUri, wacBackend, acpBackend)
        } else {
            wacBackend
        }
        backend.grant(
            webId = webId,
            resourceUri = resourceUri,
            mode = mode,
            receiver = receiver,
            isContainer = isContainer,
        )
    }

    suspend fun revokeAccess(
        webId: String,
        resourceUri: URI,
        receiver: ShareReceiver,
    ) {
        val metadata = (rm.head(webId, resourceUri) as? SolidNetworkResponse.Success)?.data
        val isContainer = metadata?.isContainer() ?: resourceUri.toString().endsWith("/")
        val backend = if (metadata != null) {
            pickBackend(metadata, resourceUri, wacBackend, acpBackend)
        } else {
            wacBackend
        }
        backend.revoke(webId, resourceUri, receiver, isContainer)
    }

    /**
     * Recovers owner access to [resourceUri] after an ACL/ACR edit locked the
     * owner out: re-asserts the owner's Read/Write/Control additively (other
     * shares are kept). A successful HEAD is required — it selects the right
     * backend (WAC vs ACP) and detects whether the target is a container. If
     * the HEAD itself is denied, the ACL/ACR can't be discovered through the
     * app and the lockout must be cleared with the pod provider's tooling.
     */
    suspend fun reclaimOwnerControl(webId: String, resourceUri: URI) {
        val metadata = when (val head = rm.head(webId, resourceUri)) {
            is SolidNetworkResponse.Success -> head.data
            is SolidNetworkResponse.Error -> error(
                "Cannot read $resourceUri to repair owner access (HTTP ${head.errorCode}). " +
                        "If 401/403, the owner can no longer reach this resource's ACL through " +
                        "the app; clear the lockout via the pod provider's tooling.",
            )

            is SolidNetworkResponse.Exception -> throw head.exception
        }
        pickBackend(metadata, resourceUri, wacBackend, acpBackend)
            .reclaimOwnerControl(webId, resourceUri, metadata.isContainer())
    }

    suspend fun getSharesFromAcl(webId: String, resourceUri: URI): List<GivenShare> =
        backendFor(webId, resourceUri).listShares(webId, resourceUri)

    suspend fun readGivenIndex(webId: String, podRoot: URI): GivenSharesIndexRDF =
        rm.read(webId, givenSharesUri(podRoot), GivenSharesIndexRDF::class.java).getOrThrow()

    suspend fun readReceivedIndex(webId: String, podRoot: URI): ReceivedSharesIndexRDF =
        rm.read(webId, receivedSharesUri(podRoot), ReceivedSharesIndexRDF::class.java).getOrThrow()

    /**
     * Replaces all modes for `(receiver, share.resourceUri)` in the index with
     * a single triple for [share]. Collapses any multi-mode rows for the pair
     * because callers are stating their desired single mode.
     */
    suspend fun replaceGivenShare(webId: String, podRoot: URI, share: GivenShare) {
        setShareModesForReceiver(
            webId, podRoot,
            resourceUri = share.resourceUri,
            receiver = share.receiver,
            modes = setOf(share.mode),
        )
    }

    /** Sets the index to record exactly [modes] for `(receiver, resourceUri)`. */
    suspend fun setShareModesForReceiver(
        webId: String,
        podRoot: URI,
        resourceUri: String,
        receiver: ShareReceiver,
        modes: Set<ShareMode>,
    ) {
        val uri = givenSharesUri(podRoot)
        val receiverIri = receiver.toRdfSubject()
        patchIndexWithRetry(webId, uri) {
            val index = readGivenIndex(webId, podRoot)
            val current = index.getShares()
            val existingForPair = current.filter {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
            }
            val existingModes = existingForPair.map { it.mode }.toSet()
            val toDelete = existingModes - modes
            val toInsert = modes - existingModes
            val needGroupMarker = receiver is ShareReceiver.GroupReceiver &&
                    existingForPair.isEmpty() &&
                    current.none {
                        it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
                    }
            val patch = if (toDelete.isEmpty() && toInsert.isEmpty() && !needGroupMarker) {
                null
            } else {
                N3Patch.build {
                    toDelete.forEach { mode ->
                        delete(receiverIri, mode.toAclPredicate(), resourceUri)
                    }
                    toInsert.forEach { mode ->
                        insert(receiverIri, mode.toAclPredicate(), resourceUri)
                    }
                    if (needGroupMarker) insert(receiverIri, RDF.TYPE, VCARD.GROUP)
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    /**
     * Removes all index rows for `(receiver, resourceUri)`. If the receiver
     * is a [ShareReceiver.GroupReceiver] and this was the last reference to
     * that group anywhere in the index, the `rdf:type vcard:Group` marker
     * is dropped too so the file doesn't accumulate orphans.
     */
    suspend fun removeGivenShare(
        webId: String,
        podRoot: URI,
        resourceUri: String,
        receiver: ShareReceiver,
    ) {
        val uri = givenSharesUri(podRoot)
        val receiverIri = receiver.toRdfSubject()
        patchIndexWithRetry(webId, uri) {
            val index = readGivenIndex(webId, podRoot)
            val current = index.getShares()
            val existingModes = current
                .filter {
                    it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
                }
                .map { it.mode }
                .distinct()
            val stillReferencedAfter = current.any {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
            }
            val patch = if (existingModes.isEmpty()) {
                null
            } else {
                N3Patch.build {
                    existingModes.forEach { mode ->
                        delete(receiverIri, mode.toAclPredicate(), resourceUri)
                    }
                    if (receiver is ShareReceiver.GroupReceiver && !stillReferencedAfter) {
                        delete(receiverIri, RDF.TYPE, VCARD.GROUP)
                    }
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    suspend fun replaceReceivedShare(webId: String, podRoot: URI, share: ReceivedShare) {
        val uri = receivedSharesUri(podRoot)
        patchIndexWithRetry(webId, uri) {
            val index = readReceivedIndex(webId, podRoot)
            val existing = index.getShares().firstOrNull {
                it.ownerWebId == share.ownerWebId && it.resourceUri == share.resourceUri
            }
            val patch = when {
                existing != null && existing.mode == share.mode -> null
                else -> N3Patch.build {
                    if (existing != null) {
                        delete(share.ownerWebId, existing.mode.toAclPredicate(), share.resourceUri)
                    }
                    insert(share.ownerWebId, share.mode.toAclPredicate(), share.resourceUri)
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    suspend fun removeReceivedShare(
        webId: String,
        podRoot: URI,
        resourceUri: String,
        ownerWebId: String,
    ) {
        val uri = receivedSharesUri(podRoot)
        patchIndexWithRetry(webId, uri) {
            val index = readReceivedIndex(webId, podRoot)
            val existing = index.getShares().firstOrNull {
                it.ownerWebId == ownerWebId && it.resourceUri == resourceUri
            }
            val patch = existing?.let {
                N3Patch.build {
                    delete(ownerWebId, it.mode.toAclPredicate(), resourceUri)
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    private suspend fun patchIndexWithRetry(
        webId: String,
        uri: URI,
        build: suspend () -> Pair<N3Patch?, String?>,
    ) {
        var attempt = 0
        while (true) {
            val (patch, etag) = build()
            if (patch == null) return
            when (val r = rm.patch(webId, uri, patch, ifMatch = etag)) {
                is SolidNetworkResponse.Success -> return
                is SolidNetworkResponse.Error -> {
                    if (r.errorCode == 412 && ++attempt < MAX_INDEX_PATCH_ATTEMPTS) continue
                    error("Index patch for $uri failed: ${r.errorCode} ${r.errorMessage}")
                }

                is SolidNetworkResponse.Exception -> throw r.exception
            }
        }
    }

    /**
     * HEADs [resourceUri] from the receiver's perspective and reports access as
     * a tri-state:
     *
     *  - [ReceivedAccess.Granted] — a confirmed grant (strongest observed mode +
     *    `solid:owner` link if any). A successful HEAD proves at least Read, so
     *    an absent `WAC-Allow` header (permitted by spec) is reported as
     *    `Granted(READ)`, not a revocation.
     *  - [ReceivedAccess.Denied] — authoritative no-access: 403, 404/410, or a
     *    `WAC-Allow` header listing no recognized mode.
     *  - [ReceivedAccess.Unknown] — cannot authoritatively decide (401
     *    token-refresh blip, 5xx, transport/parse exception). Callers must keep
     *    stored rows and must not surface AccessDenied in this case.
     */
    suspend fun probeReceivedAccess(
        webId: String,
        resourceUri: URI,
    ): ReceivedAccess {
        val metadata = when (val head = rm.head(webId, resourceUri)) {
            is SolidNetworkResponse.Success -> head.data
            is SolidNetworkResponse.Error ->
                return if (head.errorCode == 403 || head.isMissing()) {
                    ReceivedAccess.Denied
                } else {
                    ReceivedAccess.Unknown
                }

            is SolidNetworkResponse.Exception -> return ReceivedAccess.Unknown
        }
        val owner = metadata.ownerUri?.toString()
        val wac = metadata.wacAllow
            ?: return ReceivedAccess.Granted(ShareMode.READ, owner)
        val combined = wac.userModes + wac.publicModes
        return when {
            combined.contains("write") -> ReceivedAccess.Granted(ShareMode.WRITE, owner)
            combined.contains("append") -> ReceivedAccess.Granted(ShareMode.APPEND, owner)
            combined.contains("read") -> ReceivedAccess.Granted(ShareMode.READ, owner)
            else -> ReceivedAccess.Denied
        }
    }
}

/** Tri-state result of [SharingManagerHelper.probeReceivedAccess]. */
internal sealed interface ReceivedAccess {
    data class Granted(val mode: ShareMode, val owner: String?) : ReceivedAccess
    data object Denied : ReceivedAccess
    data object Unknown : ReceivedAccess
}

internal fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"
