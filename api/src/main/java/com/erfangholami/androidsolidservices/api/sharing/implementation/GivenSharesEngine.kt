package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.FOAF

internal class GivenSharesEngine(
    private val rm: SolidResourceManager,
    private val helper: SharingManagerHelper,
    private val notifications: NotificationsManager,
    private val scanner: PodShareScanner,
) {

    suspend fun getStoredGivenShares(
        webId: String,
    ): SolidResult<List<GivenShare>> = wrapSharing {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        helper.readGivenShares(webId, podRoot)
    }

    suspend fun refreshGivenShares(
        webId: String,
    ): SolidResult<List<GivenShare>> = wrapSharing {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val stored = helper.readGivenShares(webId, podRoot)

        val verified = mutableListOf<GivenShare>()
        val observedResourceUris = mutableSetOf<String>()
        stored.map { it.resourceUri }.distinct().forEach { resourceUri ->
            val live = runCatching {
                helper.getSharesFromAcl(webId, encodeUriString(resourceUri).toString())
            }.onSuccess {
                observedResourceUris += resourceUri
            }.onFailure { t ->
                Log.w(
                    TAG,
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

    suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidResult<List<GivenShare>> = wrapSharing {
        helper.getSharesFromAcl(webId, encodeUriString(resourceUri).toString())
    }

    suspend fun rebuildGivenIndex(
        webId: String,
    ): SolidResult<List<GivenShare>> = wrapSharing {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)

        val scan = scanner.scanPod(webId, podRoot)
        if (!scan.complete) {
            Log.w(
                TAG,
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

    suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidResult<GivenShare> = wrapSharing {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val canonicalUri = encodeUriString(resourceUri).toString()
        val canonicalReceiver = canonicalizeReceiver(webId, receiver)
        val hadGrantBefore = runCatching {
            helper.getSharesFromAcl(webId, canonicalUri).any {
                it.receiver.toRdfSubject() == canonicalReceiver.toRdfSubject()
            }
        }.getOrDefault(false)
        helper.grantAccess(webId, canonicalUri, mode, canonicalReceiver)
        writeOwnerProvenance(webId, canonicalUri)
        val share = GivenShare(canonicalReceiver, mode, canonicalUri, createdAt = nowIsoDateTime())
        runCatching {
            helper.replaceGivenShare(webId, podRoot, share)
        }.onFailure { t ->
            if (hadGrantBefore) {
                Log.w(
                    TAG,
                    "createShare: index write FAILED for $resourceUri, but the receiver held " +
                            "access before this call (mode change) — keeping the live grant " +
                            "rather than revoking it; the index will reconcile on the next " +
                            "successful write or rebuildGivenIndex.",
                    t,
                )
            } else {
                runCatching { helper.revokeAccess(webId, canonicalUri, canonicalReceiver) }.onFailure { rb ->
                    Log.e(
                        TAG,
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
                    TAG,
                    "createShare: failed to notify ${canonicalReceiver.webId} about $canonicalUri; " +
                            "share is created but receiver was not pinged.",
                    t,
                )
            }
        }
        share
    }

    suspend fun updateShare(
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
                        TAG,
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

    suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidResult<Unit> = wrapSharing {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val canonicalUri = encodeUriString(resourceUri).toString()
        helper.revokeAccess(webId, canonicalUri, receiver)
        runCatching {
            helper.removeGivenShare(webId, podRoot, canonicalUri, receiver)
        }.onFailure { t ->
            Log.w(
                TAG,
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
                    TAG,
                    "revokeShare: failed to notify ${receiver.webId} of undo for $resourceUri; " +
                            "ACL is revoked but receiver was not pinged.",
                    t,
                )
            }
        }
    }

    private suspend fun canonicalizeReceiver(
        viewerWebId: String,
        receiver: ShareReceiver,
    ): ShareReceiver {
        if (receiver !is ShareReceiver.WebIdReceiver) return receiver
        if (receiver.webId.contains('#')) return receiver
        val resolved = runCatching { resolveProfileWebId(viewerWebId, receiver.webId) }
            .onFailure { t ->
                Log.w(
                    TAG,
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
        val docStr = encodeUriString(docIri).toString()
        val rdf = runCatching {
            rm.readPublic(docStr, SolidRDFResource::class.java).getOrThrow()
        }.getOrElse {
            rm.read(viewerWebId, docStr, SolidRDFResource::class.java).getOrThrow()
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

    private suspend fun writeOwnerProvenance(webId: String, resourceUri: String) {
        runCatching {
            val rdf = rm.read(webId, resourceUri, SolidRDFResource::class.java).getOrThrow()
            val alreadyHasCreator = rdf.getAllQuads().any { it.predicate == DC.CREATOR }
            if (alreadyHasCreator) return
            val etag = rdf.getHeaders().getETag()
            val patch = N3Patch.build { insert(resourceUri, DC.CREATOR, webId) }
            rm.patch(webId, resourceUri, patch, ifMatch = etag).getOrThrow()
        }.onFailure { t ->
            Log.w(
                TAG,
                "writeOwnerProvenance: could not stamp dcterms:creator on $resourceUri " +
                        "(likely a non-RDF resource); the share link's owner hint covers this case.",
                t,
            )
        }
    }

    private companion object {
        const val TAG = "SharingManager"
    }
}
