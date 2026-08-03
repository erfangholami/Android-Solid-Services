package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.nowIsoDateTime
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal class ReceivedSharesEngine(
    private val rm: SolidResourceManager,
    private val helper: SharingManagerHelper,
) {

    private val receivedIndexLocks = ConcurrentHashMap<String, Mutex>()

    private fun receivedIndexLock(webId: String): Mutex =
        receivedIndexLocks.computeIfAbsent(webId) { Mutex() }

    suspend fun getStoredReceivedShares(
        webId: String,
    ): SolidResult<List<ReceivedShare>> = wrapSharing {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        helper.readReceivedShares(webId, podRoot)
    }

    suspend fun refreshReceivedShares(
        webId: String,
    ): SolidResult<List<ReceivedShare>> = wrapSharing {
        receivedIndexLock(webId).withLock {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val stored = helper.readReceivedShares(webId, podRoot)
        val verified = mutableListOf<ReceivedShare>()
        stored.forEach { share ->
            val access = runCatching {
                helper.probeReceivedAccess(webId, encodeUriString(share.resourceUri).toString())
            }.getOrElse { t ->
                Log.w(
                    TAG,
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
                        addedAt = share.addedAt,
                        resourceType = share.resourceType,
                        resourceName = share.resourceName,
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
    }

    suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String?,
        resourceType: String?,
        resourceName: String?,
    ): SolidResult<ReceivedShare?> = wrapSharing {
        receivedIndexLock(webId).withLock {
        val podRoot = helper.getPodRoot(webId)
        helper.ensurePrivateSharesContainer(webId, podRoot)
        val canonicalUri = encodeUriString(resourceUri).toString()
        val hintedOwner = ownerHint?.takeIf { IriUtils.isValid(it) }
        when (val access = helper.probeReceivedAccess(webId, canonicalUri)) {
            is ReceivedAccess.Granted -> {
                val ownerWebId = hintedOwner ?: access.owner ?: resolveOwner(webId, canonicalUri)
                val share = ReceivedShare(
                    ownerWebId = ownerWebId,
                    mode = access.mode,
                    resourceUri = canonicalUri,
                    addedAt = nowIsoDateTime(),
                    resourceType = resourceType,
                    resourceName = resourceName,
                )
                helper.replaceReceivedShare(webId, podRoot, share)
                share
            }

            ReceivedAccess.Denied -> {
                val stored = helper.readReceivedShares(webId, podRoot)
                val matching = stored.filter { it.resourceUri == canonicalUri }
                if (matching.isEmpty()) {
                    throw SharingException.AccessDenied(
                        resourceUri = canonicalUri,
                        ownerWebId = resolveOwner(webId, canonicalUri),
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
                helper.readReceivedShares(webId, podRoot)
                    .firstOrNull { it.resourceUri == canonicalUri }
                    ?: throw SharingException.AccessIndeterminate(canonicalUri)
            }
        }
        }
    }

    suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): SolidResult<Unit> = wrapSharing {
        receivedIndexLock(webId).withLock {
            val podRoot = helper.getPodRoot(webId)
            helper.ensurePrivateSharesContainer(webId, podRoot)
            helper.removeReceivedShare(
                webId, podRoot, encodeUriString(resourceUri).toString(), ownerWebId,
            )
        }
    }

    suspend fun syncReceivedShares(
        webId: String,
        notifications: List<ShareNotification>,
    ): SolidResult<List<ReceivedShare>> = wrapSharing {
        receivedIndexLock(webId).withLock {
            val podRoot = helper.getPodRoot(webId)
            helper.ensurePrivateSharesContainer(webId, podRoot)
            collapseToTerminalReceivedNotifications(notifications).forEach { n ->
                applyReceivedShareNotification(webId, podRoot, n)
            }
            helper.readReceivedShares(webId, podRoot)
        }
    }

    private suspend fun applyReceivedShareNotification(
        webId: String,
        podRoot: String,
        n: ShareNotification,
    ) {
        when (n.type) {
            ShareNotificationType.OFFER,
            ShareNotificationType.ACCEPTED,
            ShareNotificationType.UPDATED,
                -> runCatching {
                val resourceUri = encodeUriString(n.resourceUri).toString()
                val access = helper.probeReceivedAccess(webId, resourceUri)
                if (access is ReceivedAccess.Denied) return@runCatching
                val granted = access as? ReceivedAccess.Granted
                helper.replaceReceivedShare(
                    webId, podRoot,
                    ReceivedShare(
                        ownerWebId = granted?.owner ?: n.ownerWebId,
                        mode = n.mode ?: granted?.mode ?: ShareMode.READ,
                        resourceUri = resourceUri,
                        addedAt = n.publishedAt ?: nowIsoDateTime(),
                        resourceType = n.resourceType,
                        resourceName = n.resourceName,
                    ),
                )
            }.onFailure { t ->
                Log.w(
                    TAG,
                    "syncReceivedShares: grant sync failed for ${n.resourceUri}; " +
                            "received-index may be stale until next refreshReceivedShares.",
                    t,
                )
            }

            ShareNotificationType.UNDO -> runCatching {
                helper.removeReceivedShare(
                    webId, podRoot, encodeUriString(n.resourceUri).toString(), n.ownerWebId,
                )
            }.onFailure { t ->
                Log.w(
                    TAG,
                    "syncReceivedShares: UNDO sync failed for ${n.resourceUri}; " +
                            "received-index may still list this share.",
                    t,
                )
            }

            ShareNotificationType.REJECT,
            ShareNotificationType.DECISION_GRANTED,
            ShareNotificationType.DECISION_REJECTED,
                -> Unit
        }
    }

    private suspend fun resolveOwner(webId: String, resourceUri: String): String {
        runCatching {
            val rdf = rm.read(webId, resourceUri, SolidRDFResource::class.java).getOrThrow()
            rdf.getAllQuads().firstOrNull { it.predicate == DC.CREATOR }?.`object`
        }.onFailure { t ->
            Log.w(TAG, "resolveOwner: dcterms:creator lookup failed for $resourceUri; falling through.", t)
        }.getOrNull()?.takeIf { IriUtils.isValid(it) }?.let { return it }

        runCatching {
            val head = rm.head(webId, resourceUri)
            val storageDescUri = (head as? SolidResult.Success)?.value
                ?.storageDescriptionUri ?: return@runCatching null
            val storageRdf = rm.read(webId, storageDescUri, SolidRDFResource::class.java)
                .getOrThrow()
            storageRdf.getAllQuads()
                .firstOrNull { it.predicate == Solid.OWNER }
                ?.`object`
        }.onFailure { t ->
            Log.w(
                TAG,
                "resolveOwner: solid:owner lookup failed for $resourceUri; " +
                        "falling back to scheme://authority guess.",
                t,
            )
        }.getOrNull()?.takeIf { IriUtils.isValid(it) }?.let { return it }

        val parsed = encodeUriString(resourceUri)
        val origin = "${parsed.scheme}://${parsed.authority}"
        Log.w(
            TAG,
            "resolveOwner: no WebID signal for $resourceUri; using pod origin '$origin' as a " +
                    "display-only owner identifier (not a real WebID).",
        )
        return origin
    }

    private companion object {
        const val TAG = "SharingManager"
    }
}
