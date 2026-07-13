package com.erfangholami.androidsolidservices.api.sharing.implementation

import com.erfangholami.androidsolidservices.api.access.AccessBackend
import com.erfangholami.androidsolidservices.api.access.AcpBackend
import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.access.pickBackend
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.AccessProbe
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.api.sharing.SharingProfile
import com.erfangholami.androidsolidservices.api.sharing.SolidShareProfile
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.rdf.sharing.GivenSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ReceivedSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.delay

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
 * [SolidResult] at the public boundary.
 */
internal class SharingManagerHelper {

    companion object {
        private const val MAX_INDEX_PATCH_ATTEMPTS = 3
        private const val INDEX_PATCH_BACKOFF_MS = 100L
        private const val INDEX_PATCH_JITTER_MS = 150L

        @Volatile
        private var INSTANCE: SharingManagerHelper? = null

        /** Clears the process-global singleton so a test gets a fresh, isolated instance. */
        internal fun resetForTest() {
            INSTANCE = null
        }

        fun getInstance(
            authenticator: Authenticator,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManagerHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerHelper(
                    SolidResourceManager.getInstance(authenticator), profile,
                ).also { INSTANCE = it }
            }

        fun getInstance(
            resourceManager: SolidResourceManager,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManagerHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharingManagerHelper(resourceManager, profile).also { INSTANCE = it }
            }
    }

    val rm: SolidResourceManager
    private val profile: SharingProfile
    private val wacBackend: WacBackend
    private val acpBackend: AcpBackend

    private val layout get() = profile.storageLayout
    private val vocabulary get() = profile.vocabulary

    private val podRootCache = ConcurrentHashMap<String, String>()
    private val sharesContainerReady = ConcurrentHashMap<String, Boolean>()

    private constructor(resourceManager: SolidResourceManager, profile: SharingProfile) {
        this.rm = resourceManager
        this.profile = profile
        this.wacBackend = WacBackend(rm)
        this.acpBackend = AcpBackend(rm)
    }

    suspend fun getPodRoot(webId: String): String {
        podRootCache[webId]?.let { return it }
        val profile = rm.read(webId, webId, WebId::class.java).getOrThrow()
        val storage = profile.getStorages().firstOrNull()
            ?: StorageDiscovery.discover(rm, webId)
            ?: error("Could not discover a storage for $webId")
        return storage.ensureTrailingSlash()
            .also { podRootCache[webId] = it }
    }

    fun givenSharesUri(podRoot: String): String = layout.givenIndex(podRoot)

    fun receivedSharesUri(podRoot: String): String = layout.receivedIndex(podRoot)

    fun sharesContainerUri(podRoot: String): String = layout.sharesContainer(podRoot)

    fun solidshareContainerUri(podRoot: String): String = layout.rootContainer(podRoot)

    fun catalogUri(podRoot: String): String = layout.catalog(podRoot)

    suspend fun ensurePrivateSharesContainer(webId: String, podRoot: String) {
        if (sharesContainerReady[webId] == true) return
        ensureContainer(webId, solidshareContainerUri(podRoot))
        val containerUri = sharesContainerUri(podRoot)
        ensureContainer(webId, containerUri)
        backendFor(webId, containerUri).ensureOwnerOnly(
            webId = webId, targetUri = containerUri, isContainer = true,
        )
        ensureEmptyRdf(webId, givenSharesUri(podRoot))
        ensureEmptyRdf(webId, receivedSharesUri(podRoot))
        runCatching { registerSharesContainer(webId, containerUri) }
        sharesContainerReady[webId] = true
    }

    /**
     * Registers the shares container as a `solid:instanceContainer` for
     * `solidshare:Share` in the owner's private type index, so the shares
     * bookkeeping is discoverable there (alongside contacts and tickets)
     * rather than only at the fixed `solidshare/` path. Idempotent, and
     * best-effort: a failure here never blocks the sharing flow.
     */
    private suspend fun registerSharesContainer(webId: String, containerUri: String) {
        val typeIndex = TypeIndexResolver.getPrivateTypeIndex(rm, webId)
        if (typeIndex.containsResource(containerUri)) return
        typeIndex.addInstanceContainer(SolidShare.SHARE, containerUri)
        rm.update(webId, typeIndex).getOrThrow()
    }

    suspend fun ensureSolidshareContainer(webId: String, podRoot: String) {
        ensureContainer(webId, solidshareContainerUri(podRoot))
    }

    /** Creates the LDN inbox container if it doesn't already exist. */
    suspend fun ensureInboxContainer(webId: String, inboxUri: String) {
        ensureContainer(webId, inboxUri)
    }

    private suspend fun ensureContainer(webId: String, containerUri: String) {
        rm.ensureContainer(webId, containerUri).getOrThrow()
    }

    private suspend fun ensureEmptyRdf(webId: String, uri: String) {
        when (val head = rm.head(webId, uri)) {
            is SolidResult.Success -> return
            is SolidResult.Failure ->
                if (!head.error.isMissing()) throw head.error.asException()
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

    private fun SolidError.isMissing(): Boolean =
        code == SolidErrorCode.NOT_FOUND

    /**
     * Returns `true` if the server advertises [resourceUri] as an LDP
     * container (BasicContainer or any subtype). Used to choose between
     * `acl:accessTo` and `acl:default` when authoring an authorization.
     */
    suspend fun isContainer(webId: String, resourceUri: String): Boolean {
        val head = rm.head(webId, resourceUri)
        if (head !is SolidResult.Success) {
            return resourceUri.endsWith("/")
        }
        return head.value.isContainer()
    }

    private fun SolidMetadata.isContainer(): Boolean {
        val containerTypes = setOf(
            LDP.BASIC_CONTAINER,
            LDP.CONTAINER,
            LDP.DIRECT_CONTAINER,
            LDP.INDIRECT_CONTAINER,
        )
        return linkTypes.any { it in containerTypes }
    }

    /**
     * Picks the right [AccessBackend] for [resourceUri] based on the Link
     * headers returned by HEAD. Defaults to WAC if HEAD fails so the caller
     * always gets a non-throwing reference.
     */
    suspend fun backendFor(webId: String, resourceUri: String): AccessBackend {
        val metadata = when (val head = rm.head(webId, resourceUri)) {
            is SolidResult.Success -> head.value
            else -> return wacBackend
        }
        return pickBackend(metadata, resourceUri, wacBackend, acpBackend)
    }

    suspend fun grantAccess(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        includeImpliedModes: Boolean = true,
    ) {
        val metadata = (rm.head(webId, resourceUri) as? SolidResult.Success)?.value
        val isContainer = metadata?.isContainer() ?: resourceUri.endsWith("/")
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
            includeImpliedModes = includeImpliedModes,
        )
    }

    suspend fun revokeAccess(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ) {
        val metadata = (rm.head(webId, resourceUri) as? SolidResult.Success)?.value
        val isContainer = metadata?.isContainer() ?: resourceUri.endsWith("/")
        val backend = if (metadata != null) {
            pickBackend(metadata, resourceUri, wacBackend, acpBackend)
        } else {
            wacBackend
        }
        backend.revoke(webId, resourceUri, receiver, isContainer)
    }

    suspend fun makeOwnerOnly(webId: String, resourceUri: String) {
        val metadata = (rm.head(webId, resourceUri) as? SolidResult.Success)?.value
        val isContainer = metadata?.isContainer() ?: resourceUri.endsWith("/")
        val backend = if (metadata != null) {
            pickBackend(metadata, resourceUri, wacBackend, acpBackend)
        } else {
            wacBackend
        }
        backend.ensureOwnerOnly(webId, resourceUri, isContainer)
    }

    /**
     * Recovers owner access to [resourceUri] after an ACL/ACR edit locked the
     * owner out: re-asserts the owner's Read/Write/Control additively (other
     * shares are kept). A successful HEAD is required — it selects the right
     * backend (WAC vs ACP) and detects whether the target is a container. If
     * the HEAD itself is denied, the ACL/ACR can't be discovered through the
     * app and the lockout must be cleared with the pod provider's tooling.
     */
    suspend fun reclaimOwnerControl(webId: String, resourceUri: String) {
        val metadata = when (val head = rm.head(webId, resourceUri)) {
            is SolidResult.Success -> head.value
            is SolidResult.Failure -> error(
                "Cannot read $resourceUri to repair owner access (${head.error.message}). " +
                        "If 401/403, the owner can no longer reach this resource's ACL through " +
                        "the app; clear the lockout via the pod provider's tooling.",
            )
        }
        pickBackend(metadata, resourceUri, wacBackend, acpBackend)
            .reclaimOwnerControl(webId, resourceUri, metadata.isContainer())
    }

    suspend fun getSharesFromAcl(webId: String, resourceUri: String): List<GivenShare> =
        backendFor(webId, resourceUri).listShares(webId, resourceUri)

    suspend fun readGivenIndex(webId: String, podRoot: String): GivenSharesIndexRDF =
        rm.read(webId, givenSharesUri(podRoot), GivenSharesIndexRDF::class.java).getOrThrow()

    suspend fun readReceivedIndex(webId: String, podRoot: String): ReceivedSharesIndexRDF =
        rm.read(webId, receivedSharesUri(podRoot), ReceivedSharesIndexRDF::class.java).getOrThrow()

    suspend fun readGivenShares(webId: String, podRoot: String): List<GivenShare> =
        readGivenIndex(webId, podRoot).getShares(vocabulary)

    suspend fun readReceivedShares(webId: String, podRoot: String): List<ReceivedShare> =
        readReceivedIndex(webId, podRoot).getShares(vocabulary)

    /**
     * Replaces the record for `(receiver, share.resourceUri)` in the index with
     * a single-mode record for [share], carrying [GivenShare.createdAt]. Any
     * existing record's `dcterms:created` is preserved (so a mode change keeps
     * the original time).
     */
    suspend fun replaceGivenShare(webId: String, podRoot: String, share: GivenShare) {
        setShareModesForReceiver(
            webId, podRoot,
            resourceUri = share.resourceUri,
            receiver = share.receiver,
            modes = setOf(share.mode),
            createdAt = share.createdAt,
        )
    }

    /**
     * Records exactly [modes] for `(receiver, resourceUri)` as a reified
     * `solidshare:Share` node. An existing node's `dcterms:created` is kept; a
     * new node is stamped with [createdAt] (may be `null` when unknown, e.g. a
     * pair reconstructed from an ACL scan). Any legacy bare-triple rows for the
     * pair are migrated into the node.
     */
    suspend fun setShareModesForReceiver(
        webId: String,
        podRoot: String,
        resourceUri: String,
        receiver: ShareReceiver,
        modes: Set<ShareMode>,
        createdAt: String?,
    ) {
        val uri = givenSharesUri(podRoot)
        val receiverIri = receiver.toRdfSubject()
        patchIndexWithRetry(webId, uri) {
            val index = readGivenIndex(webId, podRoot)
            val nodes = index.getShareNodes(vocabulary)
            val legacy = index.getLegacyFlatShares()
            val node = nodes.firstOrNull {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
            }
            val legacyForPair = legacy.filter {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
            }
            val existingModes = node?.modes ?: emptySet()
            val modesToInsert = modes - existingModes
            val modesToDelete = existingModes - modes
            val creatingNode = node == null
            val createdToInsert = when {
                creatingNode -> createdAt
                node.createdAt == null -> createdAt
                else -> null
            }
            val groupReferencedElsewhere = nodes.any {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
            } || legacy.any {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
            }
            val needGroupMarker = receiver is ShareReceiver.GroupReceiver &&
                    creatingNode && !groupReferencedElsewhere
            val hasWork = modesToInsert.isNotEmpty() || modesToDelete.isNotEmpty() ||
                    legacyForPair.isNotEmpty() || creatingNode || needGroupMarker ||
                    createdToInsert != null
            val patch = if (!hasWork) {
                null
            } else {
                val subject = node?.subject ?: shareNodeIri(uri, receiverIri, resourceUri)
                N3Patch.build {
                    legacyForPair.forEach { delete(receiverIri, it.mode.toAclPredicate(), resourceUri) }
                    if (creatingNode) {
                        insert(subject, RDF.TYPE, vocabulary.shareType)
                        insert(subject, vocabulary.resource, resourceUri)
                        insert(subject, vocabulary.receiver, receiverIri)
                    }
                    modesToInsert.forEach { insert(subject, ACL.MODE, it.toAclPredicate()) }
                    modesToDelete.forEach { delete(subject, ACL.MODE, it.toAclPredicate()) }
                    createdToInsert?.let {
                        insertLiteral(subject, DC.CREATED, it, datatype = XSD.DATE_TIME)
                    }
                    if (needGroupMarker) insert(receiverIri, RDF.TYPE, VCARD.GROUP)
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    /**
     * Removes the record for `(receiver, resourceUri)` — the reified node and
     * any legacy bare-triple rows. If the receiver is a
     * [ShareReceiver.GroupReceiver] and this was its last reference anywhere in
     * the index, the `rdf:type vcard:Group` marker is dropped too.
     */
    suspend fun removeGivenShare(
        webId: String,
        podRoot: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ) {
        val uri = givenSharesUri(podRoot)
        val receiverIri = receiver.toRdfSubject()
        patchIndexWithRetry(webId, uri) {
            val index = readGivenIndex(webId, podRoot)
            val nodes = index.getShareNodes(vocabulary)
            val legacy = index.getLegacyFlatShares()
            val node = nodes.firstOrNull {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
            }
            val legacyForPair = legacy.filter {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
            }
            val stillReferencedAfter = nodes.any {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
            } || legacy.any {
                it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
            }
            val patch = if (node == null && legacyForPair.isEmpty()) {
                null
            } else {
                N3Patch.build {
                    node?.let { n ->
                        delete(n.subject, RDF.TYPE, vocabulary.shareType)
                        delete(n.subject, vocabulary.resource, resourceUri)
                        delete(n.subject, vocabulary.receiver, receiverIri)
                        n.modes.forEach { delete(n.subject, ACL.MODE, it.toAclPredicate()) }
                        n.createdAt?.let {
                            deleteLiteral(n.subject, DC.CREATED, it, datatype = XSD.DATE_TIME)
                        }
                    }
                    legacyForPair.forEach { delete(receiverIri, it.mode.toAclPredicate(), resourceUri) }
                    if (receiver is ShareReceiver.GroupReceiver && !stillReferencedAfter) {
                        delete(receiverIri, RDF.TYPE, VCARD.GROUP)
                    }
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    /**
     * Records [share] in the received index as a reified `solidshare:Share`
     * node owned by [ReceivedShare.ownerWebId], carrying [ReceivedShare.addedAt].
     * An existing record's time is preserved; only the mode is updated when it
     * differs. Legacy bare-triple rows for the pair are migrated.
     */
    suspend fun replaceReceivedShare(webId: String, podRoot: String, share: ReceivedShare) {
        val uri = receivedSharesUri(podRoot)
        val ownerIri = share.ownerWebId
        patchIndexWithRetry(webId, uri) {
            val index = readReceivedIndex(webId, podRoot)
            val node = index.getShareNodes(vocabulary).firstOrNull {
                it.ownerWebId == ownerIri && it.resourceUri == share.resourceUri
            }
            val legacyForPair = index.getLegacyFlatShares().filter {
                it.ownerWebId == ownerIri && it.resourceUri == share.resourceUri
            }
            val creatingNode = node == null
            val modeChanged = node != null && node.mode != share.mode
            val fillingAdded = node != null && node.addedAt == null && share.addedAt != null
            val hasWork = creatingNode || modeChanged || legacyForPair.isNotEmpty() || fillingAdded
            val patch = if (!hasWork) {
                null
            } else {
                val subject = node?.subject ?: shareNodeIri(uri, ownerIri, share.resourceUri)
                N3Patch.build {
                    legacyForPair.forEach { delete(ownerIri, it.mode.toAclPredicate(), share.resourceUri) }
                    if (creatingNode) {
                        insert(subject, RDF.TYPE, vocabulary.shareType)
                        insert(subject, vocabulary.resource, share.resourceUri)
                        insert(subject, vocabulary.owner, ownerIri)
                        insert(subject, ACL.MODE, share.mode.toAclPredicate())
                        share.addedAt?.let {
                            insertLiteral(subject, DC.CREATED, it, datatype = XSD.DATE_TIME)
                        }
                    } else {
                        if (modeChanged) {
                            delete(subject, ACL.MODE, node.mode.toAclPredicate())
                            insert(subject, ACL.MODE, share.mode.toAclPredicate())
                        }
                        if (fillingAdded) {
                            insertLiteral(subject, DC.CREATED, share.addedAt!!, datatype = XSD.DATE_TIME)
                        }
                    }
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    suspend fun removeReceivedShare(
        webId: String,
        podRoot: String,
        resourceUri: String,
        ownerWebId: String,
    ) {
        val uri = receivedSharesUri(podRoot)
        patchIndexWithRetry(webId, uri) {
            val index = readReceivedIndex(webId, podRoot)
            val node = index.getShareNodes(vocabulary).firstOrNull {
                it.ownerWebId == ownerWebId && it.resourceUri == resourceUri
            }
            val legacyForPair = index.getLegacyFlatShares().filter {
                it.ownerWebId == ownerWebId && it.resourceUri == resourceUri
            }
            val patch = if (node == null && legacyForPair.isEmpty()) {
                null
            } else {
                N3Patch.build {
                    node?.let { n ->
                        delete(n.subject, RDF.TYPE, vocabulary.shareType)
                        delete(n.subject, vocabulary.resource, resourceUri)
                        delete(n.subject, vocabulary.owner, ownerWebId)
                        delete(n.subject, ACL.MODE, n.mode.toAclPredicate())
                        n.addedAt?.let {
                            deleteLiteral(n.subject, DC.CREATED, it, datatype = XSD.DATE_TIME)
                        }
                    }
                    legacyForPair.forEach { delete(ownerWebId, it.mode.toAclPredicate(), resourceUri) }
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

    /**
     * A stable record-node IRI for a `(counterpart, resource)` pair, as a
     * fragment on the index document. Deterministic so re-creating the same pair
     * reuses the node; callers prefer an already-parsed node's subject when one
     * exists.
     */
    private fun shareNodeIri(indexUri: String, counterpartIri: String, resourceUri: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$counterpartIri|$resourceUri".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(20)
        return "$indexUri#share-$hex"
    }

    private suspend fun patchIndexWithRetry(
        webId: String,
        uri: String,
        build: suspend () -> Pair<N3Patch?, String?>,
    ) {
        var attempt = 0
        while (true) {
            val (patch, etag) = build()
            if (patch == null) return
            when (val r = rm.patch(webId, uri, patch, ifMatch = etag)) {
                is SolidResult.Success -> return
                is SolidResult.Failure -> {
                    if (r.error.code == SolidErrorCode.PRECONDITION_FAILED &&
                        ++attempt < MAX_INDEX_PATCH_ATTEMPTS
                    ) {
                        delay(INDEX_PATCH_BACKOFF_MS * attempt + Random.nextLong(INDEX_PATCH_JITTER_MS))
                        continue
                    }
                    error("Index patch for $uri failed: ${r.error.message}")
                }
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
        resourceUri: String,
    ): ReceivedAccess = when (val probe = rm.probeAccess(webId, resourceUri)) {
        is SolidResult.Failure -> ReceivedAccess.Unknown
        is SolidResult.Success -> when (val access = probe.value) {
            is AccessProbe.Accessible ->
                ReceivedAccess.Granted(ShareMode.strongest(access.modes) ?: ShareMode.READ, access.ownerWebId)

            AccessProbe.Denied -> ReceivedAccess.Denied
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

/** The current instant as an `xsd:dateTime` literal in UTC, seconds precision (`…Z`). */
internal fun nowIsoDateTime(): String =
    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
