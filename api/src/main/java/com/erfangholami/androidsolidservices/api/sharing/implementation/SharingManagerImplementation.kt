package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.api.sharing.SharingProfile
import com.erfangholami.androidsolidservices.api.sharing.SolidShareProfile
import com.erfangholami.androidsolidservices.api.exceptions.toSolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CancellationException
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
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val givenEngine: GivenSharesEngine

    private constructor(resourceManager: SolidResourceManager, profile: SharingProfile) {
        this.rm = resourceManager
        this.profile = profile
        this.helper = SharingManagerHelper.getInstance(rm, profile)
        this.notifications = NotificationsManager.getInstance(rm)
        this.saiReader = SaiAccessGrantReader(rm)
        this.receivedEngine = ReceivedSharesEngine(rm, helper)
        this.scanner = PodShareScanner(rm, helper, profile)
        this.givenEngine = GivenSharesEngine(rm, helper, notifications, scanner)
    }

    override suspend fun getStoredGivenShares(webId: String): SolidResult<List<GivenShare>> =
        givenEngine.getStoredGivenShares(webId)

    override suspend fun refreshGivenShares(webId: String): SolidResult<List<GivenShare>> =
        givenEngine.refreshGivenShares(webId)

    override suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): SolidResult<List<GivenShare>> = givenEngine.getGivenSharesForResource(webId, resourceUri)

    override suspend fun rebuildGivenIndex(webId: String): SolidResult<List<GivenShare>> =
        givenEngine.rebuildGivenIndex(webId)

    override suspend fun repairOwnerControl(
        webId: String,
        resourceUri: String,
    ): SolidResult<Unit> = wrap {
        helper.reclaimOwnerControl(webId, encodeUriString(resourceUri).toString())
    }

    override suspend fun makePrivate(
        webId: String,
        resourceUri: String,
    ): SolidResult<Unit> = wrap {
        helper.makeOwnerOnly(webId, encodeUriString(resourceUri).toString())
    }

    override suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidResult<GivenShare> =
        givenEngine.createShare(webId, resourceUri, mode, receiver, notifyReceiver)

    override suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): SolidResult<GivenShare> =
        givenEngine.updateShare(webId, resourceUri, mode, receiver, notifyReceiver)

    override suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): SolidResult<Unit> = givenEngine.revokeShare(webId, resourceUri, receiver)

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

}

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
