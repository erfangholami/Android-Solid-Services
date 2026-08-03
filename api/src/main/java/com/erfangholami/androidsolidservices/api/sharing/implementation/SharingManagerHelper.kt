package com.erfangholami.androidsolidservices.api.sharing.implementation

import com.erfangholami.androidsolidservices.api.access.AccessBackend
import com.erfangholami.androidsolidservices.api.access.AcpBackend
import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.access.pickBackend
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.api.sharing.SharingProfile
import com.erfangholami.androidsolidservices.api.sharing.SolidShareProfile
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3PatchBuilder
import com.erfangholami.androidsolidservices.shared.rdf.sharing.GivenSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.sharing.ReceivedSharesIndexRDF
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

internal class SharingManagerHelper {

    companion object {
        private const val MAX_INDEX_PATCH_ATTEMPTS = 3
        private const val INDEX_PATCH_BACKOFF_MS = 100L
        private const val INDEX_PATCH_JITTER_MS = 150L

        @Volatile
        private var instance: SharingManagerHelper? = null

        internal fun resetForTest() {
            instance = null
        }

        fun getInstance(
            authenticator: Authenticator,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManagerHelper =
            instance ?: synchronized(this) {
                instance ?: SharingManagerHelper(
                    SolidResourceManager.getInstance(authenticator), profile,
                ).also { instance = it }
            }

        fun getInstance(
            resourceManager: SolidResourceManager,
            profile: SharingProfile = SolidShareProfile,
        ): SharingManagerHelper =
            instance ?: synchronized(this) {
                instance ?: SharingManagerHelper(resourceManager, profile).also { instance = it }
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

    private suspend fun registerSharesContainer(webId: String, containerUri: String) {
        TypeIndexResolver.addInstanceContainer(
            resourceManager = rm,
            webIdString = webId,
            forClass = SolidShare.SHARE,
            containerUri = containerUri,
            isPrivate = true,
        )
    }

    suspend fun ensureSolidshareContainer(webId: String, podRoot: String) {
        ensureContainer(webId, solidshareContainerUri(podRoot))
    }

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

    suspend fun replaceGivenShare(webId: String, podRoot: String, share: GivenShare) {
        setShareModesForReceiver(
            webId, podRoot,
            resourceUri = share.resourceUri,
            receiver = share.receiver,
            modes = setOf(share.mode),
            createdAt = share.createdAt,
            resourceType = share.resourceType,
            resourceName = share.resourceName,
        )
    }

    suspend fun setShareModesForReceiver(
        webId: String,
        podRoot: String,
        resourceUri: String,
        receiver: ShareReceiver,
        modes: Set<ShareMode>,
        createdAt: String?,
        resourceType: String? = null,
        resourceName: String? = null,
    ) {
        val uri = givenSharesUri(podRoot)
        val receiverIri = receiver.toRdfSubject()
        val write = GivenShareWrite(modes, createdAt, resourceType, resourceName)
        patchIndexWithRetry(webId, uri) {
            val index = readGivenIndex(webId, podRoot)
            val diff = givenShareDiff(index, uri, resourceUri, receiver, write)
            val patch = diff?.let { buildGivenSharePatch(it, resourceUri, receiverIri) }
            patch to index.getHeaders().getETag()
        }
    }

    private class GivenShareWrite(
        val modes: Set<ShareMode>,
        val createdAt: String?,
        val resourceType: String?,
        val resourceName: String?,
    )

    private class GivenShareDiff(
        val subject: String,
        val creatingNode: Boolean,
        val legacyForPair: List<GivenShare>,
        val modesToInsert: Set<ShareMode>,
        val modesToDelete: Set<ShareMode>,
        val createdToInsert: String?,
        val typeChange: Pair<String?, String>?,
        val nameChange: Pair<String?, String>?,
        val needGroupMarker: Boolean,
    ) {
        val hasWork: Boolean
            get() = modesToInsert.isNotEmpty() || modesToDelete.isNotEmpty() ||
                legacyForPair.isNotEmpty() || creatingNode || needGroupMarker ||
                createdToInsert != null || typeChange != null || nameChange != null
    }

    private fun givenShareDiff(
        index: GivenSharesIndexRDF,
        indexUri: String,
        resourceUri: String,
        receiver: ShareReceiver,
        write: GivenShareWrite,
    ): GivenShareDiff? {
        val receiverIri = receiver.toRdfSubject()
        val nodes = index.getShareNodes(vocabulary)
        val legacy = index.getLegacyFlatShares()
        val node = nodes.firstOrNull {
            it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
        }
        val legacyForPair = legacy.filter {
            it.receiver.toRdfSubject() == receiverIri && it.resourceUri == resourceUri
        }
        val existingModes = node?.modes ?: emptySet()
        val creatingNode = node == null
        val createdToInsert = when {
            creatingNode -> write.createdAt
            node.createdAt == null -> write.createdAt
            else -> null
        }
        val groupReferencedElsewhere = nodes.any {
            it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
        } || legacy.any {
            it.receiver.toRdfSubject() == receiverIri && it.resourceUri != resourceUri
        }
        return GivenShareDiff(
            subject = node?.subject ?: shareNodeIri(indexUri, receiverIri, resourceUri),
            creatingNode = creatingNode,
            legacyForPair = legacyForPair,
            modesToInsert = write.modes - existingModes,
            modesToDelete = existingModes - write.modes,
            createdToInsert = createdToInsert,
            typeChange = tripleChange(node?.resourceType, write.resourceType),
            nameChange = tripleChange(node?.resourceName, write.resourceName),
            needGroupMarker = receiver is ShareReceiver.GroupReceiver &&
                creatingNode && !groupReferencedElsewhere,
        ).takeIf { it.hasWork }
    }

    private fun buildGivenSharePatch(
        diff: GivenShareDiff,
        resourceUri: String,
        receiverIri: String,
    ): N3Patch = N3Patch.build {
        diff.legacyForPair.forEach { delete(receiverIri, it.mode.toAclPredicate(), resourceUri) }
        if (diff.creatingNode) {
            insert(diff.subject, RDF.TYPE, vocabulary.shareType)
            insert(diff.subject, vocabulary.resource, resourceUri)
            insert(diff.subject, vocabulary.receiver, receiverIri)
        }
        diff.modesToInsert.forEach { insert(diff.subject, ACL.MODE, it.toAclPredicate()) }
        diff.modesToDelete.forEach { delete(diff.subject, ACL.MODE, it.toAclPredicate()) }
        diff.createdToInsert?.let {
            insertLiteral(diff.subject, DC.CREATED, it, datatype = XSD.DATE_TIME)
        }
        replaceIri(diff.subject, vocabulary.resourceType, diff.typeChange)
        replaceLiteral(diff.subject, DC.TITLE, diff.nameChange)
        if (diff.needGroupMarker) insert(receiverIri, RDF.TYPE, VCARD.GROUP)
    }

    private fun tripleChange(existing: String?, requested: String?): Pair<String?, String>? =
        when {
            requested == null || requested == existing -> null
            else -> existing to requested
        }

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
                        n.resourceType?.let { delete(n.subject, vocabulary.resourceType, it) }
                        n.resourceName?.let { deleteLiteral(n.subject, DC.TITLE, it) }
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

    suspend fun replaceReceivedShare(webId: String, podRoot: String, share: ReceivedShare) {
        val uri = receivedSharesUri(podRoot)
        patchIndexWithRetry(webId, uri) {
            val index = readReceivedIndex(webId, podRoot)
            receivedSharePatch(index, uri, share) to index.getHeaders().getETag()
        }
    }

    private fun receivedSharePatch(
        index: ReceivedSharesIndexRDF,
        indexUri: String,
        share: ReceivedShare,
    ): N3Patch? {
        val ownerIri = share.ownerWebId
        val node = index.getShareNodes(vocabulary).firstOrNull {
            it.ownerWebId == ownerIri && it.resourceUri == share.resourceUri
        }
        val legacyForPair = index.getLegacyFlatShares().filter {
            it.ownerWebId == ownerIri && it.resourceUri == share.resourceUri
        }
        if (node == null) {
            return buildNewReceivedNodePatch(
                subject = shareNodeIri(indexUri, ownerIri, share.resourceUri),
                share = share,
                legacyForPair = legacyForPair,
            )
        }
        val modeChanged = node.mode != share.mode
        val fillingAdded = node.addedAt == null && share.addedAt != null
        val typeChange = tripleChange(node.resourceType, share.resourceType)
        val nameChange = tripleChange(node.resourceName, share.resourceName)
        val hasWork = modeChanged || legacyForPair.isNotEmpty() ||
            fillingAdded || typeChange != null || nameChange != null
        if (!hasWork) return null
        return N3Patch.build {
            legacyForPair.forEach { delete(ownerIri, it.mode.toAclPredicate(), share.resourceUri) }
            if (modeChanged) {
                delete(node.subject, ACL.MODE, node.mode.toAclPredicate())
                insert(node.subject, ACL.MODE, share.mode.toAclPredicate())
            }
            if (fillingAdded) {
                insertLiteral(node.subject, DC.CREATED, share.addedAt!!, datatype = XSD.DATE_TIME)
            }
            replaceIri(node.subject, vocabulary.resourceType, typeChange)
            replaceLiteral(node.subject, DC.TITLE, nameChange)
        }
    }

    private fun buildNewReceivedNodePatch(
        subject: String,
        share: ReceivedShare,
        legacyForPair: List<ReceivedShare>,
    ): N3Patch = N3Patch.build {
        legacyForPair.forEach {
            delete(share.ownerWebId, it.mode.toAclPredicate(), share.resourceUri)
        }
        insert(subject, RDF.TYPE, vocabulary.shareType)
        insert(subject, vocabulary.resource, share.resourceUri)
        insert(subject, vocabulary.owner, share.ownerWebId)
        insert(subject, ACL.MODE, share.mode.toAclPredicate())
        share.addedAt?.let { insertLiteral(subject, DC.CREATED, it, datatype = XSD.DATE_TIME) }
        share.resourceType?.let { insert(subject, vocabulary.resourceType, it) }
        share.resourceName?.let { insertLiteral(subject, DC.TITLE, it) }
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
                        n.resourceType?.let { delete(n.subject, vocabulary.resourceType, it) }
                        n.resourceName?.let { deleteLiteral(n.subject, DC.TITLE, it) }
                    }
                    legacyForPair.forEach { delete(ownerWebId, it.mode.toAclPredicate(), resourceUri) }
                }
            }
            patch to index.getHeaders().getETag()
        }
    }

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

internal sealed interface ReceivedAccess {
    data class Granted(val mode: ShareMode, val owner: String?) : ReceivedAccess
    data object Denied : ReceivedAccess
    data object Unknown : ReceivedAccess
}

internal fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"

private fun N3PatchBuilder.replaceIri(
    subject: String,
    predicate: String,
    change: Pair<String?, String>?,
) {
    val (old, new) = change ?: return
    old?.let { delete(subject, predicate, it) }
    insert(subject, predicate, new)
}

private fun N3PatchBuilder.replaceLiteral(
    subject: String,
    predicate: String,
    change: Pair<String?, String>?,
) {
    val (old, new) = change ?: return
    old?.let { deleteLiteral(subject, predicate, it) }
    insertLiteral(subject, predicate, new)
}
