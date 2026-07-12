package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.api.sharing.SharingProfile
import com.erfangholami.androidsolidservices.api.sharing.SolidShareProfile
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.api.exceptions.toSolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CancellationException
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
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.rdf.sharing.CatalogRDF
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class SharingManagerImplementation : SharingManager {

    companion object {
        private const val SHARING_LOG_TAG = "SharingManager"

        @Volatile
        private var INSTANCE: SharingManager? = null

        /** Clears the process-global singleton so a test gets a fresh, isolated instance. */
        internal fun resetForTest() {
            INSTANCE = null
        }

        fun getInstance(
            authenticator: Authenticator,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerImplementation(
                    SolidResourceManager.getInstance(authenticator), profile,
                ).also { INSTANCE = it }
            }

        fun getInstance(
            resourceManager: SolidResourceManager,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerImplementation(resourceManager, profile)
                    .also { INSTANCE = it }
            }
    }

    private val rm: SolidResourceManager
    private val profile: SharingProfile
    private val helper: SharingManagerHelper
    private val notifications: NotificationsManager
    private val saiReader: SaiAccessGrantReader
    private val receivedEngine: ReceivedSharesEngine
    private val scanner: PodShareScanner

    private constructor(resourceManager: SolidResourceManager, profile: SharingProfile) {
        this.rm = resourceManager
        this.profile = profile
        this.helper = SharingManagerHelper.getInstance(rm, profile)
        this.notifications = NotificationsManager.getInstance(rm)
        this.saiReader = SaiAccessGrantReader(rm)
        this.receivedEngine = ReceivedSharesEngine(rm, helper)
        this.scanner = PodShareScanner(rm, helper, profile)
    }

    override suspend fun getStoredGivenShares(
        webId: String,
    ): SolidResult<List<GivenShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        helper.readGivenShares(webId, podRoot)
    }

    override suspend fun refreshGivenShares(
        webId: String,
    ): SolidResult<List<GivenShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val stored = helper.readGivenShares(webId, podRoot)

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
        val storedPairToCreated = stored
            .groupBy { it.receiver.toRdfSubject() to it.resourceUri }
            .mapValues { (_, list) -> list.firstNotNullOfOrNull { it.createdAt } }

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
                createdAt = storedPairToCreated[pair],
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
    ): SolidResult<List<GivenShare>> = wrap {
        helper.getSharesFromAcl(webId, encodeUriString(resourceUri))
    }

    override suspend fun rebuildGivenIndex(
        webId: String,
    ): SolidResult<List<GivenShare>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)

        val scan = scanner.scanPod(webId, podRoot)
        if (!scan.complete) {
            Log.w(
                SHARING_LOG_TAG,
                "rebuildGivenIndex: pod scan under $podRoot was partial — some " +
                        "resources could not be read (e.g. a deleted/locked ACL returning " +
                        "403). Rebuilt the index from the readable resources; stored rows " +
                        "for the unreadable ones were preserved.",
            )
        }

        val previous = helper.readGivenShares(webId, podRoot)
        val previousPairToCreated = previous
            .groupBy { it.receiver.toRdfSubject() to it.resourceUri }
            .mapValues { (_, list) -> list.firstNotNullOfOrNull { it.createdAt } }
        previous.map { it.receiver.toRdfSubject() to it.resourceUri }.distinct().forEach { pair ->
            val resourceUri = pair.second
            // Observed rows are re-asserted from the live ACL below; excluded rows
            // are dropped outright (and never re-added). Anything else — an
            // unreadable or unreached resource — keeps its row.
            val prune = resourceUri in scan.observedResources || scanner.isExcludedFromScan(resourceUri)
            if (!prune) return@forEach
            val receiver = previous.first {
                it.receiver.toRdfSubject() == pair.first && it.resourceUri == resourceUri
            }.receiver
            helper.removeGivenShare(webId, podRoot, resourceUri, receiver)
        }
        scan.shares.groupBy { it.receiver.toRdfSubject() to it.resourceUri }
            .forEach { (pair, list) ->
                helper.setShareModesForReceiver(
                    webId, podRoot,
                    resourceUri = pair.second,
                    receiver = list.first().receiver,
                    modes = list.map { it.mode }.toSet(),
                    createdAt = previousPairToCreated[pair],
                )
            }
        helper.readGivenShares(webId, podRoot)
    }

    override suspend fun repairOwnerControl(
        webId: String,
        resourceUri: String,
    ): SolidResult<Unit> = wrap {
        helper.reclaimOwnerControl(webId, encodeUriString(resourceUri))
    }

    override suspend fun makePrivate(
        webId: String,
        resourceUri: String,
    ): SolidResult<Unit> = wrap {
        helper.makeOwnerOnly(webId, encodeUriString(resourceUri))
    }

    override suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidResult<GivenShare> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val uri = encodeUriString(resourceUri)
        val canonicalUri = uri.toString()
        val canonicalReceiver = canonicalizeReceiver(webId, receiver)
        val hadGrantBefore = runCatching {
            helper.getSharesFromAcl(webId, uri).any {
                it.receiver.toRdfSubject() == canonicalReceiver.toRdfSubject()
            }
        }.getOrDefault(false)
        helper.grantAccess(webId, uri, mode, canonicalReceiver)
        writeOwnerProvenance(webId, uri)
        val share = GivenShare(canonicalReceiver, mode, canonicalUri, createdAt = nowIsoDateTime())
        runCatching {
            helper.replaceGivenShare(webId, podRoot, share)
        }.onFailure { t ->
            if (hadGrantBefore) {
                Log.w(
                    SHARING_LOG_TAG,
                    "createShare: index write FAILED for $resourceUri, but the receiver held " +
                            "access before this call (mode change) — keeping the live grant " +
                            "rather than revoking it; the index will reconcile on the next " +
                            "successful write or rebuildGivenIndex.",
                    t,
                )
            } else {
                runCatching { helper.revokeAccess(webId, uri, canonicalReceiver) }.onFailure { rb ->
                    Log.e(
                        SHARING_LOG_TAG,
                        "createShare: index write FAILED and rollback of the WAC grant for " +
                                "$resourceUri also failed — ACL and index are now divergent; a " +
                                "rebuildGivenIndex is required to reconcile.",
                        rb,
                    )
                }
            }
            throw t
        }
        if (notifyReceiver && canonicalReceiver is ShareReceiver.WebIdReceiver) {
            runCatching {
                notifications.sendOffer(webId, canonicalReceiver.webId, canonicalUri, mode)
            }.onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "createShare: failed to notify ${canonicalReceiver.webId} about $canonicalUri; " +
                            "share is created but receiver was not pinged.",
                    t,
                )
            }
        }
        share
    }

    /**
     * Resolves a [ShareReceiver.WebIdReceiver] whose IRI is a bare profile
     * **document** URL (no fragment, e.g. `…/card`) to the real WebID it
     * describes (e.g. `…/card#me`), via the profile's `foaf:primaryTopic` /
     * `foaf:isPrimaryTopicOf`.
     *
     * WAC matches `acl:agent` against the receiver's *authenticated* WebID,
     * which is fragment-qualified — so granting to the bare document URL
     * silently grants no access, and a later accept-request (which carries the
     * receiver's real WebID) creates a second, duplicate index row. Resolving
     * up front grants the right agent and keeps a single record.
     *
     * Already-fragment-qualified WebIDs and non-WebID receivers pass through
     * untouched; any resolution failure falls back to the original IRI so a
     * share is never blocked.
     */
    private suspend fun canonicalizeReceiver(
        viewerWebId: String,
        receiver: ShareReceiver,
    ): ShareReceiver {
        if (receiver !is ShareReceiver.WebIdReceiver) return receiver
        if (receiver.webId.contains('#')) return receiver
        val resolved = runCatching { resolveProfileWebId(viewerWebId, receiver.webId) }
            .onFailure { t ->
                Log.w(
                    SHARING_LOG_TAG,
                    "canonicalizeReceiver: could not resolve a fragment-qualified WebID for " +
                            "${receiver.webId}; sharing with it as-is (WAC may not match the " +
                            "receiver's real WebID).",
                    t,
                )
            }
            .getOrNull()
        return if (resolved != null) ShareReceiver.WebIdReceiver(resolved) else receiver
    }

    private suspend fun resolveProfileWebId(viewerWebId: String, docIri: String): String? {
        val docUri = encodeUriString(docIri)
        val docStr = docUri.toString()
        val rdf = runCatching {
            rm.readPublic(docUri, SolidRDFResource::class.java).getOrThrow()
        }.getOrElse {
            rm.read(viewerWebId, docUri, SolidRDFResource::class.java).getOrThrow()
        }
        val candidates = rdf.getAllQuads().mapNotNull { q ->
            when {
                q.predicate == FOAF.PRIMARY_TOPIC && !q.isLiteralObject -> q.`object`
                q.predicate == FOAF.IS_PRIMARY_TOPIC_OF && !q.isLiteralObject -> q.subject
                else -> null
            }
        }
        val sameDocFragment = candidates.firstOrNull {
            it != docStr && it.substringBefore('#') == docStr
        }
        return (sameDocFragment ?: candidates.firstOrNull { it != docStr })
            ?.takeIf { IriUtils.isValid(it) }
    }

    override suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidResult<GivenShare> {
        val result = createShare(webId, resourceUri, mode, receiver, notifyReceiver = false)
        if (notifyReceiver && result is SolidResult.Success) {
            val updated = result.value
            val updatedReceiver = updated.receiver
            if (updatedReceiver is ShareReceiver.WebIdReceiver) {
                runCatching {
                    notifications.sendUpdate(
                        webId, updatedReceiver.webId, updated.resourceUri, mode,
                    ).getOrThrow()
                }.onFailure { t ->
                    Log.w(
                        SHARING_LOG_TAG,
                        "updateShare: access changed for ${updated.resourceUri} but failed to " +
                                "notify ${updatedReceiver.webId} of the new level; the receiver's " +
                                "view will sync on their next refresh.",
                        t,
                    )
                }
            }
        }
        return result
    }

    override suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidResult<Unit> = wrap {
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

    override suspend fun getStoredReceivedShares(webId: String): SolidResult<List<ReceivedShare>> =
        receivedEngine.getStoredReceivedShares(webId)

    override suspend fun refreshReceivedShares(webId: String): SolidResult<List<ReceivedShare>> =
        receivedEngine.refreshReceivedShares(webId)

    override suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String?,
    ): SolidResult<ReceivedShare?> = receivedEngine.addReceivedShare(webId, resourceUri, ownerHint)

    override suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidResult<Unit> = receivedEngine.removeReceivedShare(webId, resourceUri, ownerWebId)

    override suspend fun syncReceivedShares(
        webId: String,
        notifications: List<ShareNotification>,
    ): SolidResult<List<ReceivedShare>> = receivedEngine.syncReceivedShares(webId, notifications)

    override suspend fun getAccessGrants(
        webId: String,
    ): SolidResult<List<AccessGrant>> = wrap {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)

        val given = helper.readGivenShares(webId, podRoot).map { share ->
            AccessGrant(
                direction = AccessGrantDirection.GIVEN,
                counterpartWebId = share.receiver.toRdfSubject(),
                resourceUri = share.resourceUri,
                mode = share.mode,
                status = AccessGrantStatus.ACTIVE,
                source = AccessGrantSource.APP_INDEX,
                grantedAt = share.createdAt,
                requestUri = null,
            )
        }

        val received = helper.readReceivedShares(webId, podRoot).map { share ->
            AccessGrant(
                direction = AccessGrantDirection.RECEIVED,
                counterpartWebId = share.ownerWebId,
                resourceUri = share.resourceUri,
                mode = share.mode,
                status = AccessGrantStatus.ACTIVE,
                source = AccessGrantSource.APP_INDEX,
                grantedAt = share.addedAt,
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
    ): SolidResult<GivenShare> = withContext(Dispatchers.IO) {
        val result = createShare(
            webId = webId,
            resourceUri = request.resourceUri,
            mode = request.requestedMode,
            receiver = ShareReceiver.WebIdReceiver(request.requesterWebId),
            notifyReceiver = false,
        )
        if (result is SolidResult.Success) {
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
    ): SolidResult<Unit> = wrap {
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
    ): SolidResult<Unit> = wrap {
        if (!profile.catalogEnabled) return@wrap
        val normalizedEntry =
            entry.copy(resourceUri = encodeUriString(entry.resourceUri).toString())
        val podRoot = helper.getPodRoot(webId)
        helper.ensureSolidshareContainer(webId, podRoot)
        val catalogUri = helper.catalogUri(podRoot)
        val existing: CatalogRDF? =
            when (val read = rm.read(webId, catalogUri, CatalogRDF::class.java)) {
                is SolidResult.Success -> read.value
                is SolidResult.Failure ->
                    if (read.error.code == SolidErrorCode.NOT_FOUND) {
                        null
                    } else {
                        error(
                            "publishCatalogEntry: catalog read for $catalogUri failed: " +
                                    "${read.error.message}",
                        )
                    }
            }
        val current = existing ?: CatalogRDF(catalogUri, "application/ld+json", null, null)
        val survivingQuads =
            current.getAllQuads().filterNot { it.subject == normalizedEntry.resourceUri }
        val rebuilt = CatalogRDF(catalogUri, "application/ld+json", survivingQuads, null)
        rebuilt.addEntry(normalizedEntry, profile.vocabulary)

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
    ): SolidResult<Unit> = wrap {
        if (!profile.catalogEnabled) return@wrap
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
    ): SolidResult<List<CatalogEntry>> = wrap {
        if (!profile.catalogEnabled) return@wrap emptyList()
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
        catalog.getEntries(profile.vocabulary)
    }

    override fun getShareDeepLink(resourceUri: String, ownerWebId: String?): String =
        profile.linkCodec.deepLink(resourceUri, ownerWebId)

    override fun parseShareDeepLink(deepLink: String): ParsedShareLink? =
        profile.linkCodec.parse(deepLink)

    override fun getShareBareUrl(resourceUri: String): String =
        profile.linkCodec.bareUrl(resourceUri)

    private suspend fun <T> wrap(block: suspend () -> T): SolidResult<T> = wrapSharing(block)

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

/**
 * Runs a sharing operation on [Dispatchers.IO], returning its value as [SolidResult.Success] or
 * mapping a thrown exception to a typed [SolidResult.Failure] (cancellation propagates). Shared by
 * the sharing facade and its engines so every operation has one failure contract.
 */
internal suspend fun <T> wrapSharing(block: suspend () -> T): SolidResult<T> =
    withContext(Dispatchers.IO) {
        try {
            SolidResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SolidResult.Failure(e.toSolidError())
        }
    }

private val PRESENCE_CHANGING_TYPES = setOf(
    ShareNotificationType.OFFER,
    ShareNotificationType.ACCEPTED,
    ShareNotificationType.UPDATED,
    ShareNotificationType.UNDO,
)

internal fun collapseToTerminalReceivedNotifications(
    notifications: List<ShareNotification>,
): List<ShareNotification> =
    notifications
        .filter { it.type in PRESENCE_CHANGING_TYPES }
        .groupBy { IriUtils.canonical(it.ownerWebId) to IriUtils.canonical(it.resourceUri) }
        .values
        .mapNotNull { group -> group.maxWithOrNull(NOTIFICATION_RECENCY) }

private fun ShareNotification.isGrant(): Boolean = type != ShareNotificationType.UNDO

internal val NOTIFICATION_RECENCY: Comparator<ShareNotification> =
    compareBy<ShareNotification>(
        { parseNotificationInstant(it.publishedAt) },
        { if (it.isGrant()) 1 else 0 },
        { it.notificationUri },
    )

internal fun parseNotificationInstant(value: String?): Instant {
    if (value.isNullOrBlank()) return Instant.MIN
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()
        ?: Instant.MIN
}
