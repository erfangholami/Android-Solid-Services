package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantDirection
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantSource
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantStatus
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.rdf.sharing.CatalogRDF
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI

internal class SharingManagerImplementation : SharingManager {

    companion object {
        private const val SHARING_LOG_TAG = "SharingManager"

        private const val MAX_CONCURRENT_NODE_READS = 8

        @Volatile
        private var INSTANCE: SharingManager? = null

        fun getInstance(authenticator: Authenticator): SharingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerImplementation(authenticator).also { INSTANCE = it }
            }

        fun getInstance(resourceManager: SolidResourceManager): SharingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerImplementation(resourceManager).also { INSTANCE = it }
            }
    }

    private val helper: SharingManagerHelper
    private val rm: SolidResourceManager
    private val notifications: NotificationsManager
    private val saiReader: SaiAccessGrantReader

    private constructor(authenticator: Authenticator) {
        this.rm = SolidResourceManager.getInstance(authenticator)
        this.helper = SharingManagerHelper.getInstance(rm)
        this.notifications = NotificationsManager.getInstance(rm)
        this.saiReader = SaiAccessGrantReader(rm)
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.rm = resourceManager
        this.helper = SharingManagerHelper.getInstance(rm)
        this.notifications = NotificationsManager.getInstance(rm)
        this.saiReader = SaiAccessGrantReader(rm)
    }

    override suspend fun getStoredGivenShares(
        webId: String,
    ): SolidNetworkResponse<List<GivenShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        helper.readGivenIndex(webId, podRoot).getShares()
    }

    override suspend fun refreshGivenShares(
        webId: String,
    ): SolidNetworkResponse<List<GivenShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val stored = helper.readGivenIndex(webId, podRoot).getShares()

        val verified = mutableListOf<GivenShare>()
        val observedResourceUris = mutableSetOf<String>()
        stored.map { it.resourceUri }.distinct().forEach { resourceUri ->
            val live = runCatching {
                helper.getSharesFromAcl(webId, encodeUriString(resourceUri))
            }.onSuccess {
                observedResourceUris += resourceUri
            }.onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "refreshGivenShares: ACL read failed for $resourceUri; " +
                            "skipping prune for this resource to avoid losing index rows.",
                    t,
                )
            }.getOrDefault(emptyList())
            verified += live
        }

        val verifiedByPair: Map<Pair<String, String>, Set<ShareMode>> =
            verified.groupBy { it.receiver.toRdfSubject() to it.resourceUri }
                .mapValues { (_, list) -> list.map { it.mode }.toSet() }

        val storedPairs = stored.map { it.receiver.toRdfSubject() to it.resourceUri }.toSet()
        val storedPairToReceiver = stored.associate {
            (it.receiver.toRdfSubject() to it.resourceUri) to it.receiver
        }

        verifiedByPair.forEach { (pair, modes) ->
            val receiver = storedPairToReceiver[pair]
                ?: verified.first {
                    it.receiver.toRdfSubject() == pair.first && it.resourceUri == pair.second
                }.receiver
            helper.setShareModesForReceiver(
                webId, podRoot,
                resourceUri = pair.second,
                receiver = receiver,
                modes = modes,
            )
        }

        (storedPairs - verifiedByPair.keys).forEach { pair ->
            if (pair.second !in observedResourceUris) return@forEach
            val receiver = storedPairToReceiver[pair] ?: return@forEach
            helper.removeGivenShare(webId, podRoot, pair.second, receiver)
        }

        verified.distinct()
    }

    override suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<List<GivenShare>> = wrap {
        helper.getSharesFromAcl(webId, encodeUriString(resourceUri))
    }

    override suspend fun rebuildGivenIndex(
        webId: String,
    ): SolidNetworkResponse<List<GivenShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)

        val scan = scanPod(webId, podRoot)
        if (!scan.complete) {
            Log.w(
                SHARING_LOG_TAG,
                "rebuildGivenIndex: pod scan under $podRoot was partial — some " +
                        "resources could not be read (e.g. a deleted/locked ACL returning " +
                        "403). Rebuilt the index from the readable resources; stored rows " +
                        "for the unreadable ones were preserved.",
            )
        }

        val previous = helper.readGivenIndex(webId, podRoot).getShares()
        previous.map { it.receiver.toRdfSubject() to it.resourceUri }.distinct().forEach { pair ->
            if (pair.second !in scan.observedResources) return@forEach
            val receiver = previous.first {
                it.receiver.toRdfSubject() == pair.first && it.resourceUri == pair.second
            }.receiver
            helper.removeGivenShare(webId, podRoot, pair.second, receiver)
        }
        scan.shares.groupBy { it.receiver.toRdfSubject() to it.resourceUri }
            .forEach { (pair, list) ->
                helper.setShareModesForReceiver(
                    webId, podRoot,
                    resourceUri = pair.second,
                    receiver = list.first().receiver,
                    modes = list.map { it.mode }.toSet(),
                )
            }
        helper.readGivenIndex(webId, podRoot).getShares()
    }

    override suspend fun repairOwnerControl(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit> = wrap {
        helper.reclaimOwnerControl(webId, encodeUriString(resourceUri))
    }

    private data class NodeObservation(
        /** Share observations read from this node's effective ACL. */
        val shares: List<GivenShare>,
        /** Child URIs to enqueue as the next BFS frontier (empty for non-containers). */
        val children: List<URI>,
        /**
         * `true` if this node's effective ACL was read authoritatively (a 2xx
         * ACL, or a positive 404/410 meaning "no own ACL"). Only such resources
         * are eligible for index pruning; a node we couldn't read stays `false`
         * so its stored index rows are preserved. A successful ACL read counts
         * as observed even if a subsequent container listing fails.
         */
        val observed: Boolean,
        /** `false` if any part of this node's observation was incomplete. */
        val complete: Boolean,
    )

    private data class PodScan(
        /** Every share observed across the entire tree. */
        val shares: List<GivenShare>,
        /**
         * URIs whose effective ACL was read authoritatively. Only these are
         * reconciled against the stored index; unread resources keep their rows.
         */
        val observedResources: Set<String>,
        /**
         * `true` only if the entire tree was fully observed. A single unreadable
         * branch flips it to `false`, allowing [rebuildGivenIndex] to log the gap.
         */
        val complete: Boolean,
    )

    private suspend fun scanPod(webId: String, root: URI): PodScan =
        scanFrontier(webId, listOf(root), Semaphore(MAX_CONCURRENT_NODE_READS))

    private suspend fun scanFrontier(
        webId: String,
        frontier: List<URI>,
        gate: Semaphore,
    ): PodScan {
        if (frontier.isEmpty()) return PodScan(emptyList(), emptySet(), complete = true)

        val observations = coroutineScope {
            frontier.map { node ->
                async { gate.withPermit { visitNode(webId, node) } }
            }.awaitAll()
        }

        val observedHere = frontier.zip(observations)
            .filter { (_, obs) -> obs.observed }
            .map { (node, _) -> node.toString() }
            .toSet()

        val deeper = scanFrontier(webId, observations.flatMap { it.children }, gate)
        return PodScan(
            shares = observations.flatMap { it.shares } + deeper.shares,
            observedResources = observedHere + deeper.observedResources,
            complete = observations.all { it.complete } && deeper.complete,
        )
    }

    private suspend fun visitNode(webId: String, node: URI): NodeObservation {
        val nodeStr = node.toString()
        if (nodeStr.contains("/solidshare/")) {
            return NodeObservation(emptyList(), emptyList(), observed = false, complete = true)
        }

        var complete = true
        var observed = true
        val live = runCatching { helper.getSharesFromAcl(webId, node) }
            .onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "scanPod: ACL read failed for $nodeStr (e.g. 403 from a deleted/" +
                            "locked ACL); skipping this resource and preserving its stored " +
                            "index rows. The rest of the pod is still walked.",
                    t,
                )
                complete = false
                observed = false
            }
            .getOrDefault(emptyList())

        if (!nodeStr.endsWith("/")) return NodeObservation(live, emptyList(), observed, complete)

        val container = runCatching {
            rm.read(webId, node, SolidContainer::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "scanPod: container listing failed for $nodeStr; its subtree is " +
                        "unobserved (scan is partial), but the rest of the pod is still walked.",
                t,
            )
        }.getOrNull() ?: return NodeObservation(live, emptyList(), observed, complete = false)

        val children = container.getContained().mapNotNull { ref ->
            runCatching { URI.create(ref.identifier) }
                .onFailure { t ->
                    Log.w(
                        SHARING_LOG_TAG,
                        "scanPod: malformed child URI '${ref.identifier}'; scan is partial.",
                        t,
                    )
                    complete = false
                }
                .getOrNull()
        }
        return NodeObservation(live, children, observed, complete)
    }

    override suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidNetworkResponse<GivenShare> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val uri = encodeUriString(resourceUri)
        val canonicalUri = uri.toString()
        helper.grantAccess(webId, uri, mode, receiver)
        writeOwnerProvenance(webId, uri)
        val share = GivenShare(receiver, mode, canonicalUri)
        runCatching {
            helper.replaceGivenShare(webId, podRoot, share)
        }.onFailure { t ->
            runCatching { helper.revokeAccess(webId, uri, receiver) }.onFailure { rb ->
                Log.e(
                    SHARING_LOG_TAG,
                    "createShare: index write FAILED and rollback of the WAC grant for " +
                            "$resourceUri also failed — ACL and index are now divergent; a " +
                            "rebuildGivenIndex is required to reconcile.",
                    rb,
                )
            }
            throw t
        }
        if (notifyReceiver && receiver is ShareReceiver.WebIdReceiver) {
            runCatching {
                notifications.sendOffer(webId, receiver.webId, canonicalUri, mode)
            }.onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "createShare: failed to notify ${receiver.webId} about $canonicalUri; " +
                            "share is created but receiver was not pinged.",
                    t,
                )
            }
        }
        share
    }

    override suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<GivenShare> =
        createShare(webId, resourceUri, mode, receiver, notifyReceiver = false)

    override suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidNetworkResponse<Unit> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val uri = encodeUriString(resourceUri)
        val canonicalUri = uri.toString()
        helper.revokeAccess(webId, uri, receiver)
        runCatching {
            helper.removeGivenShare(webId, podRoot, canonicalUri, receiver)
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "revokeShare: ACL revoke succeeded but index removal for $resourceUri " +
                        "failed; the stale row will be reconciled by the next refreshGivenShares.",
                t,
            )
        }
        if (receiver is ShareReceiver.WebIdReceiver) {
            runCatching {
                notifications.sendUndo(webId, receiver.webId, canonicalUri)
            }.onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "revokeShare: failed to notify ${receiver.webId} of undo for $resourceUri; " +
                            "ACL is revoked but receiver was not pinged.",
                    t,
                )
            }
        }
    }

    override suspend fun getStoredReceivedShares(
        webId: String,
    ): SolidNetworkResponse<List<ReceivedShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        helper.readReceivedIndex(webId, podRoot).getShares()
    }

    override suspend fun refreshReceivedShares(
        webId: String,
    ): SolidNetworkResponse<List<ReceivedShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val stored = helper.readReceivedIndex(webId, podRoot).getShares()
        val verified = mutableListOf<ReceivedShare>()
        stored.forEach { share ->
            val access = runCatching {
                helper.probeReceivedAccess(webId, encodeUriString(share.resourceUri))
            }.getOrElse { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "refreshReceivedShares: probe threw for ${share.resourceUri}; " +
                            "keeping stored row to avoid losing index entry on transient failure.",
                    t,
                )
                ReceivedAccess.Unknown
            }
            when (access) {
                is ReceivedAccess.Granted -> {
                    val refreshed = ReceivedShare(
                        ownerWebId = access.owner ?: share.ownerWebId,
                        mode = access.mode,
                        resourceUri = share.resourceUri,
                    )
                    verified += refreshed
                    helper.replaceReceivedShare(webId, podRoot, refreshed)
                }

                ReceivedAccess.Denied -> {
                    helper.removeReceivedShare(
                        webId, podRoot, share.resourceUri, share.ownerWebId,
                    )
                }

                ReceivedAccess.Unknown -> verified += share
            }
        }
        verified
    }

    override suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String?,
    ): SolidNetworkResponse<ReceivedShare?> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val uri = encodeUriString(resourceUri)
        val canonicalUri = uri.toString()
        val hintedOwner = ownerHint?.takeIf { IriUtils.isValid(it) }
        when (val access = helper.probeReceivedAccess(webId, uri)) {
            is ReceivedAccess.Granted -> {
                val ownerWebId = hintedOwner ?: access.owner ?: resolveOwner(webId, uri)
                val share = ReceivedShare(
                    ownerWebId = ownerWebId,
                    mode = access.mode,
                    resourceUri = canonicalUri,
                )
                helper.replaceReceivedShare(webId, podRoot, share)
                share
            }

            ReceivedAccess.Denied -> {
                val stored = helper.readReceivedIndex(webId, podRoot).getShares()
                val matching = stored.filter { it.resourceUri == canonicalUri }
                if (matching.isEmpty()) {
                    throw SharingException.AccessDenied(
                        resourceUri = canonicalUri,
                        ownerWebId = resolveOwner(webId, uri),
                    )
                }
                matching.forEach { row ->
                    helper.removeReceivedShare(
                        webId, podRoot, row.resourceUri, row.ownerWebId,
                    )
                }
                null
            }

            ReceivedAccess.Unknown -> {
                helper.readReceivedIndex(webId, podRoot).getShares()
                    .firstOrNull { it.resourceUri == canonicalUri }
                    ?: throw SharingException.AccessIndeterminate(canonicalUri)
            }
        }
    }

    override suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidNetworkResponse<Unit> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        helper.removeReceivedShare(
            webId, podRoot, encodeUriString(resourceUri).toString(), ownerWebId,
        )
    }

    override suspend fun getAccessGrants(
        webId: String,
    ): SolidNetworkResponse<List<AccessGrant>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)

        val given = helper.readGivenIndex(webId, podRoot).getShares().map { share ->
            AccessGrant(
                direction = AccessGrantDirection.GIVEN,
                counterpartWebId = share.receiver.toRdfSubject(),
                resourceUri = share.resourceUri,
                mode = share.mode,
                status = AccessGrantStatus.ACTIVE,
                source = AccessGrantSource.APP_INDEX,
                grantedAt = null,
                requestUri = null,
            )
        }

        val received = helper.readReceivedIndex(webId, podRoot).getShares().map { share ->
            AccessGrant(
                direction = AccessGrantDirection.RECEIVED,
                counterpartWebId = share.ownerWebId,
                resourceUri = share.resourceUri,
                mode = share.mode,
                status = AccessGrantStatus.ACTIVE,
                source = AccessGrantSource.APP_INDEX,
                grantedAt = null,
                requestUri = null,
            )
        }

        val requests = notifications.listRequests(webId).getOrThrow().map { request ->
            AccessGrant(
                direction = AccessGrantDirection.INCOMING_REQUEST,
                counterpartWebId = request.requesterWebId,
                resourceUri = request.resourceUri,
                mode = request.requestedMode,
                status = AccessGrantStatus.PENDING,
                source = AccessGrantSource.APP_INDEX,
                grantedAt = request.publishedAt,
                requestUri = request.requestUri,
            )
        }

        val sai = saiReader.readAccessGrants(webId)

        (given + received + requests + sai).distinctBy {
            listOf(it.direction, it.counterpartWebId, it.resourceUri, it.mode)
        }
    }

    override suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): SolidNetworkResponse<GivenShare> = withContext(Dispatchers.IO) {
        val result = createShare(
            webId = webId,
            resourceUri = request.resourceUri,
            mode = request.requestedMode,
            receiver = ShareReceiver.WebIdReceiver(request.requesterWebId),
            notifyReceiver = false,
        )
        if (result is SolidNetworkResponse.Success) {
            runCatching {
                notifications.sendAccept(
                    ownerWebId = webId,
                    requesterWebId = request.requesterWebId,
                    resourceUri = request.resourceUri,
                    mode = request.requestedMode,
                    requestUri = request.requestUri,
                ).getOrThrow()
            }.onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "acceptShareRequest: grant recorded but failed to notify " +
                            "${request.requesterWebId} about ${request.resourceUri}.",
                    t,
                )
            }
            runCatching {
                notifications.recordDecisionGranted(
                    ownerWebId = webId,
                    requesterWebId = request.requesterWebId,
                    resourceUri = request.resourceUri,
                    mode = request.requestedMode,
                    requestUri = request.requestUri,
                ).getOrThrow()
            }.onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "acceptShareRequest: grant succeeded but failed to record the decision in " +
                            "$webId's own inbox; the owner's notifications screen won't show it.",
                    t,
                )
            }
        }
        result
    }

    override suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String?,
    ): SolidNetworkResponse<Unit> = wrap {
        runCatching {
            notifications.sendReject(
                ownerWebId = webId,
                requesterWebId = request.requesterWebId,
                resourceUri = request.resourceUri,
                reason = reason,
            ).getOrThrow()
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "rejectShareRequest: reject recorded but failed to notify " +
                        "${request.requesterWebId} about ${request.resourceUri}.",
                t,
            )
        }
        runCatching {
            notifications.recordDecisionRejected(
                ownerWebId = webId,
                requesterWebId = request.requesterWebId,
                resourceUri = request.resourceUri,
                mode = request.requestedMode,
                reason = reason,
            ).getOrThrow()
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "rejectShareRequest: declined but failed to record the decision in " +
                        "$webId's own inbox; the owner's notifications screen won't show it.",
                t,
            )
        }
    }

    override suspend fun publishCatalogEntry(
        webId: String,
        entry: CatalogEntry,
    ): SolidNetworkResponse<Unit> = wrap {
        val normalizedEntry =
            entry.copy(resourceUri = encodeUriString(entry.resourceUri).toString())
        val podRoot = helper.getPodRoot(webId)
        helper.ensureSolidshareContainer(webId, podRoot)
        val catalogUri = helper.catalogUri(podRoot)
        val existing: CatalogRDF? =
            when (val read = rm.read(webId, catalogUri, CatalogRDF::class.java)) {
                is SolidNetworkResponse.Success -> read.data
                is SolidNetworkResponse.Error ->
                    if (read.errorCode == 404 || read.errorCode == 410) {
                        null
                    } else {
                        error(
                            "publishCatalogEntry: catalog read for $catalogUri failed: " +
                                    "${read.errorCode} ${read.errorMessage}",
                        )
                    }

                is SolidNetworkResponse.Exception -> throw read.exception
            }
        val current = existing ?: CatalogRDF(catalogUri, "application/ld+json", null, null)
        val survivingQuads =
            current.getAllQuads().filterNot { it.subject == normalizedEntry.resourceUri }
        val rebuilt = CatalogRDF(catalogUri, "application/ld+json", survivingQuads, null)
        rebuilt.addEntry(normalizedEntry)

        if (existing != null) {
            rm.update(webId, rebuilt).getOrThrow()
        } else {
            rm.create(webId, rebuilt).getOrThrow()
        }
        helper.grantAccess(webId, catalogUri, ShareMode.READ, ShareReceiver.Public)
    }

    override suspend fun removeCatalogEntry(
        webId: String,
        resourceUri: String,
    ): SolidNetworkResponse<Unit> = wrap {
        val podRoot = helper.getPodRoot(webId)
        val catalogUri = helper.catalogUri(podRoot)
        val current = runCatching {
            rm.read(webId, catalogUri, CatalogRDF::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "removeCatalogEntry: catalog read failed for $catalogUri; " +
                        "nothing to remove.",
                t,
            )
        }.getOrNull() ?: return@wrap
        val canonicalUri = encodeUriString(resourceUri).toString()
        val survivingQuads = current.getAllQuads().filterNot { it.subject == canonicalUri }
        if (survivingQuads.size == current.getAllQuads().size) return@wrap
        val rebuilt = CatalogRDF(catalogUri, "application/ld+json", survivingQuads, null)
        rm.update(webId, rebuilt).getOrThrow()
    }

    override suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): SolidNetworkResponse<List<CatalogEntry>> = wrap {
        val ownerPodRoot = helper.getPodRoot(ownerWebId)
        val catalogUri = helper.catalogUri(ownerPodRoot)
        val catalog = runCatching {
            rm.read(viewerWebId, catalogUri, CatalogRDF::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "getOwnerCatalog: catalog read failed for $catalogUri (viewer=$viewerWebId); " +
                        "returning empty.",
                t,
            )
        }.getOrNull() ?: return@wrap emptyList()
        catalog.getEntries()
    }

    override fun getShareDeepLink(resourceUri: String, ownerWebId: String?): String =
        ShareLinkCodec.deepLink(resourceUri, ownerWebId)

    override fun parseShareDeepLink(deepLink: String): ParsedShareLink? =
        ShareLinkCodec.parse(deepLink)

    override fun getShareBareUrl(resourceUri: String): String =
        ShareLinkCodec.bareUrl(resourceUri)

    private suspend fun <T> wrap(block: suspend () -> T): SolidNetworkResponse<T> =
        withContext(Dispatchers.IO) {
            try {
                SolidNetworkResponse.Success(block())
            } catch (e: Exception) {
                SolidNetworkResponse.Exception(e)
            }
        }

    private suspend fun resolveOwner(webId: String, resourceUri: URI): String {
        runCatching {
            val rdf = rm.read(webId, resourceUri, SolidRDFResource::class.java).getOrThrow()
            rdf.getAllQuads().firstOrNull { it.predicate == DC.CREATOR }?.`object`
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "resolveOwner: dcterms:creator lookup failed for $resourceUri; falling through.",
                t,
            )
        }.getOrNull()?.takeIf { IriUtils.isValid(it) }?.let { return it }

        runCatching {
            val head = rm.head(webId, resourceUri)
            val storageDescUri = (head as? SolidNetworkResponse.Success)?.data
                ?.storageDescriptionUri ?: return@runCatching null
            val storageRdf = rm.read(webId, storageDescUri, SolidRDFResource::class.java)
                .getOrThrow()
            storageRdf.getAllQuads()
                .firstOrNull { it.predicate == Solid.OWNER }
                ?.`object`
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "resolveOwner: solid:owner lookup failed for $resourceUri; " +
                        "falling back to scheme://authority guess.",
                t,
            )
        }.getOrNull()?.takeIf { IriUtils.isValid(it) }?.let { return it }

        val origin = "${resourceUri.scheme}://${resourceUri.authority}"
        Log.w(
            SHARING_LOG_TAG,
            "resolveOwner: no WebID signal for $resourceUri; using pod origin '$origin' as a " +
                    "display-only owner identifier (not a real WebID).",
        )
        return origin
    }

    private suspend fun writeOwnerProvenance(webId: String, resourceUri: URI) {
        runCatching {
            val rdf = rm.read(webId, resourceUri, SolidRDFResource::class.java).getOrThrow()
            val alreadyHasCreator = rdf.getAllQuads().any { it.predicate == DC.CREATOR }
            if (alreadyHasCreator) return
            val etag = rdf.getHeaders().getETag()
            val patch = N3Patch.build { insert(resourceUri.toString(), DC.CREATOR, webId) }
            rm.patch(webId, resourceUri, patch, ifMatch = etag).getOrThrow()
        }.onFailure { t ->
            Log.w(
                SHARING_LOG_TAG,
                "writeOwnerProvenance: could not stamp dcterms:creator on $resourceUri " +
                        "(likely a non-RDF resource); the share link's owner hint covers this case.",
                t,
            )
        }
    }

}
