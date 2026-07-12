package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.access.AclAuthorization
import com.erfangholami.androidsolidservices.shared.model.access.SolidACLResource
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.collapseByReceiver
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import java.net.URI
import java.util.UUID

/**
 * WAC implementation of [AccessBackend].
 *
 *  - ACL writes are **conditional** (`If-Match: <etag>`) when an ETag is
 *    available from the prior read. Concurrent writes are surfaced as
 *    [SharingException.StaleAcl] so the caller can retry instead of
 *    silently clobbering.
 *  - [listShares] returns one [GivenShare] per receiver, collapsed to the
 *    strongest granted mode (Write ⊇ Append ⊇ Read), so a single grant
 *    shows as one row rather than one row per `acl:mode`.
 *  - The owner's full Read/Write/Control rule is re-asserted on every
 *    write, so a write can never lock the owner out of their own resource.
 *
 * Spec: https://solidproject.org/TR/wac
 */
internal class WacBackend(private val rm: SolidResourceManager) : AccessBackend {

    private companion object {
        const val MAX_ANCESTOR_WALK = 32

        const val MAX_ACL_WRITE_ATTEMPTS = 3
    }

    private suspend fun withStaleAclRetry(block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                block()
                return
            } catch (e: SharingException.StaleAcl) {
                if (++attempt >= MAX_ACL_WRITE_ATTEMPTS) throw e
            }
        }
    }

    override suspend fun grant(
        webId: String,
        resourceUri: URI,
        mode: ShareMode,
        receiver: ShareReceiver,
        isContainer: Boolean,
        includeImpliedModes: Boolean,
    ): Unit = withStaleAclRetry {
        val read = readAcl(webId, resourceUri)

        val baseAuths = baseAuthorizations(webId, resourceUri, read, isContainer)

        val dropSubjects = baseAuths
            .filter { ruleMatches(it, resourceUri, receiver) || isOwnerSelfRule(it, webId) }
            .map { it.subject }
            .toSet()

        val grantRule = AclAuthorization(
            subject = "${read.aclUri}#share-${UUID.randomUUID()}",
            accessTo = listOf(resourceUri),
            default = if (isContainer) listOf(resourceUri) else emptyList(),
            modes = if (includeImpliedModes) {
                mode.impliedAclModes()
            } else {
                setOf(mode.toAclPredicate())
            },
            agents = (receiver as? ShareReceiver.WebIdReceiver)?.let {
                listOf(URI.create(it.webId))
            } ?: emptyList(),
            agentClasses = if (receiver is ShareReceiver.Public) {
                listOf(URI.create(ShareReceiver.Public.toRdfSubject()))
            } else emptyList(),
            agentGroups = (receiver as? ShareReceiver.GroupReceiver)?.let {
                listOf(URI.create(it.groupUri))
            } ?: emptyList(),
        )

        val rebuilt = rebuildAcl(
            read, baseAuths, dropSubjects,
            newAuths = listOf(
                ownerSelfAuth(webId, read.aclUri, resourceUri, isContainer),
                grantRule,
            ),
        )
        writeAcl(webId, read.aclUri, rebuilt, ifMatch = read.etag)
    }

    override suspend fun revoke(
        webId: String,
        resourceUri: URI,
        receiver: ShareReceiver,
        isContainer: Boolean,
    ): Unit = withStaleAclRetry {
        val read = readAcl(webId, resourceUri)
        val baseAuths = baseAuthorizations(webId, resourceUri, read, isContainer)
        val matched = baseAuths.filter { ruleMatches(it, resourceUri, receiver) }
        val dropSubjects = (
                matched.map { it.subject } +
                        baseAuths.filter { isOwnerSelfRule(it, webId) }.map { it.subject }
                ).toSet()
        val rebuilt = rebuildAcl(
            read, baseAuths, dropSubjects,
            newAuths = matched.flatMap { narrowAuthorization(it, resourceUri, receiver) } +
                    ownerSelfAuth(webId, read.aclUri, resourceUri, isContainer),
        )
        writeAcl(webId, read.aclUri, rebuilt, ifMatch = read.etag)
    }

    override suspend fun listShares(
        webId: String,
        resourceUri: URI,
    ): List<GivenShare> {
        val read = readAcl(webId, resourceUri)
        val shares = mutableListOf<GivenShare>()
        // When the resource has no ACL of its own, effective access is defined by the
        // nearest ancestor container's acl:default authorizations (WAC inheritance);
        // baseAuthorizations returns those, mapped onto this resource, in that case.
        val authorizations = baseAuthorizations(
            webId, resourceUri, read, isContainer = resourceUri.toString().endsWith("/"),
        )
        authorizations.forEach { auth ->
            val applies =
                auth.accessTo.any { IriUtils.sameIri(it.toString(), resourceUri.toString()) } ||
                        auth.default.any { IriUtils.sameIri(it.toString(), resourceUri.toString()) }
            if (!applies) return@forEach
            if (isOwnerSelfRule(auth, webId)) return@forEach

            val mode = ShareMode.strongest(auth.modes) ?: return@forEach

            auth.agents.forEach { agent ->
                if (!IriUtils.sameIri(agent.toString(), webId)) {
                    shares += GivenShare(
                        receiver = ShareReceiver.WebIdReceiver(agent.toString()),
                        mode = mode,
                        resourceUri = resourceUri.toString(),
                    )
                }
            }
            auth.agentGroups.forEach { g ->
                shares += GivenShare(
                    receiver = ShareReceiver.GroupReceiver(g.toString()),
                    mode = mode,
                    resourceUri = resourceUri.toString(),
                )
            }
            auth.agentClasses.forEach { c ->
                if (IriUtils.sameIri(c.toString(), ShareReceiver.Public.toRdfSubject())) {
                    shares += GivenShare(
                        receiver = ShareReceiver.Public,
                        mode = mode,
                        resourceUri = resourceUri.toString(),
                    )
                }
            }
        }
        return shares.collapseByReceiver()
    }

    override suspend fun ensureOwnerOnly(
        webId: String,
        targetUri: URI,
        isContainer: Boolean,
    ) {
        val read = readAcl(webId, targetUri)
        val auths = read.acl.getAuthorizations()
        val alreadyOwnerOnly = auths.isNotEmpty() && auths.all { isOwnerSelfRule(it, webId) }
        if (alreadyOwnerOnly) return

        val rebuilt = SolidACLResource(read.aclUri)
        rebuilt.addAuthorization(ownerSelfAuth(webId, read.aclUri, targetUri, isContainer))
        writeAcl(webId, read.aclUri, rebuilt, ifMatch = read.etag)
    }

    override suspend fun reclaimOwnerControl(
        webId: String,
        targetUri: URI,
        isContainer: Boolean,
    ): Unit = withStaleAclRetry {
        val read = readAcl(webId, targetUri)
        val baseAuths = baseAuthorizations(webId, targetUri, read, isContainer)
        val dropSubjects = baseAuths
            .filter { isOwnerSelfRule(it, webId) }
            .map { it.subject }
            .toSet()
        val rebuilt = rebuildAcl(
            read, baseAuths, dropSubjects,
            newAuths = listOf(ownerSelfAuth(webId, read.aclUri, targetUri, isContainer)),
        )
        writeAcl(webId, read.aclUri, rebuilt, ifMatch = read.etag)
    }

    private data class AclRead(
        val aclUri: URI,
        val acl: SolidACLResource,
        val etag: String?,
    )

    private fun rebuildAcl(
        read: AclRead,
        baseAuths: List<AclAuthorization>,
        dropSubjects: Set<String>,
        newAuths: List<AclAuthorization>,
    ): SolidACLResource {
        val preserved = read.acl.getAllQuads().filterNot { it.subject in dropSubjects }
        val rebuilt = SolidACLResource(read.aclUri, preserved, null)
        if (read.etag == null) {
            baseAuths
                .filterNot { it.subject in dropSubjects }
                .forEach { rebuilt.addAuthorization(it) }
        }
        newAuths.forEach { rebuilt.addAuthorization(it) }
        return rebuilt
    }

    private suspend fun baseAuthorizations(
        webId: String,
        resourceUri: URI,
        read: AclRead,
        isContainer: Boolean,
    ): List<AclAuthorization> {
        if (read.etag != null) return read.acl.getAuthorizations()
        return inheritedDefaultAuthorizations(webId, resourceUri, read.aclUri, isContainer)
    }

    private suspend fun inheritedDefaultAuthorizations(
        webId: String,
        resourceUri: URI,
        aclUri: URI,
        isContainer: Boolean,
    ): List<AclAuthorization> {
        var ancestor = parentContainerOf(resourceUri)
        var guard = 0
        while (ancestor != null && guard++ < MAX_ANCESTOR_WALK) {
            val defaults = runCatching { containerDefaultAuthorizations(webId, ancestor) }
                .getOrDefault(emptyList())
            if (defaults.isNotEmpty()) {
                return defaults.mapIndexed { i, auth ->
                    AclAuthorization(
                        subject = "$aclUri#inherited-$i",
                        accessTo = listOf(resourceUri),
                        default = if (isContainer) listOf(resourceUri) else emptyList(),
                        modes = auth.modes,
                        agents = auth.agents,
                        agentClasses = auth.agentClasses,
                        agentGroups = auth.agentGroups,
                        origins = auth.origins,
                    )
                }
            }
            val next = parentContainerOf(ancestor)
            if (next == ancestor) break
            ancestor = next
        }
        return emptyList()
    }

    private suspend fun containerDefaultAuthorizations(
        webId: String,
        container: URI,
    ): List<AclAuthorization> {
        val metadata = rm.head(webId, container).getOrThrow()
        val aclUri = metadata.aclUri ?: return emptyList()
        return when (val existing = rm.head(webId, aclUri)) {
            is SolidResult.Success -> {
                val acl = rm.read(webId, aclUri, SolidACLResource::class.java).getOrThrow()
                acl.getAuthorizations().filter { it.default.isNotEmpty() }
            }

            is SolidResult.Failure ->
                if (existing.error.isMissing()) emptyList()
                else error("ACL read for $aclUri failed: ${existing.error.message}")
        }
    }

    private fun parentContainerOf(uri: URI): URI? {
        val str = uri.toString()
        val trimmed = if (str.endsWith("/")) str.dropLast(1) else str
        val schemeEnd = str.indexOf("://")
        val lastSlash = trimmed.lastIndexOf('/')
        if (schemeEnd < 0 || lastSlash <= schemeEnd + 2) return null
        return runCatching { URI.create(trimmed.substring(0, lastSlash + 1)) }.getOrNull()
    }

    private suspend fun readAcl(webId: String, resourceUri: URI): AclRead {
        val metadata = rm.head(webId, resourceUri).getOrThrow()
        val aclUri = metadata.aclUri
            ?: error("Resource $resourceUri does not advertise an acl link")
        return when (val existing = rm.head(webId, aclUri)) {
            is SolidResult.Success -> {
                val acl = rm.read(webId, aclUri, SolidACLResource::class.java).getOrThrow()
                AclRead(aclUri, acl, existing.value.etag)
            }

            is SolidResult.Failure ->
                if (existing.error.isMissing()) {
                    AclRead(aclUri, SolidACLResource(aclUri), etag = null)
                } else {
                    error("ACL read for $aclUri failed: ${existing.error.message}")
                }
        }
    }

    private fun SolidError.isMissing(): Boolean =
        code == SolidErrorCode.NOT_FOUND

    private suspend fun writeAcl(
        webId: String,
        aclUri: URI,
        acl: SolidACLResource,
        ifMatch: String?,
    ) {
        val body = NTriples.serialize(acl.getAllQuads()).toByteArray(Charsets.UTF_8)
        val result = rm.putRaw(
            webid = webId,
            uri = aclUri,
            contentType = NTriples.MEDIA_TYPE,
            body = body,
            ifMatch = ifMatch,
            linkHeader = null,
        )
        when (result) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure -> {
                if (result.error.code == SolidErrorCode.PRECONDITION_FAILED) {
                    throw SharingException.StaleAcl(aclUri.toString())
                }
                error("ACL write failed: ${result.error.message}")
            }
        }
    }

    private fun ownerSelfAuth(
        webId: String,
        aclUri: URI,
        targetUri: URI,
        isContainer: Boolean,
    ): AclAuthorization = AclAuthorization(
        subject = "${aclUri}#owner",
        accessTo = listOf(targetUri),
        default = if (isContainer) listOf(targetUri) else emptyList(),
        modes = setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
        agents = listOf(URI.create(webId)),
    )

    private fun isOwnerSelfRule(auth: AclAuthorization, webId: String): Boolean =
        auth.agents.any { IriUtils.sameIri(it.toString(), webId) } &&
                auth.modes.containsAll(setOf(ACL.READ, ACL.WRITE, ACL.CONTROL))

    private fun narrowAuthorization(
        auth: AclAuthorization,
        resourceUri: URI,
        receiver: ShareReceiver,
    ): List<AclAuthorization> {
        val target = resourceUri.toString()
        val accessToHasTarget = auth.accessTo.any { IriUtils.sameIri(it.toString(), target) }
        val defaultHasTarget = auth.default.any { IriUtils.sameIri(it.toString(), target) }
        val accessToOthers = auth.accessTo.filterNot { IriUtils.sameIri(it.toString(), target) }
        val defaultOthers = auth.default.filterNot { IriUtils.sameIri(it.toString(), target) }
        val hasOtherResources = accessToOthers.isNotEmpty() || defaultOthers.isNotEmpty()

        val agentsWithoutR = if (receiver is ShareReceiver.WebIdReceiver) {
            auth.agents.filterNot { IriUtils.sameIri(it.toString(), receiver.webId) }
        } else {
            auth.agents
        }
        val groupsWithoutR = if (receiver is ShareReceiver.GroupReceiver) {
            auth.agentGroups.filterNot { IriUtils.sameIri(it.toString(), receiver.groupUri) }
        } else {
            auth.agentGroups
        }
        val classesWithoutR = if (receiver is ShareReceiver.Public) {
            auth.agentClasses.filterNot {
                IriUtils.sameIri(it.toString(), ShareReceiver.Public.toRdfSubject())
            }
        } else {
            auth.agentClasses
        }
        val hasOtherSubjects =
            agentsWithoutR.isNotEmpty() || groupsWithoutR.isNotEmpty() || classesWithoutR.isNotEmpty()

        val result = mutableListOf<AclAuthorization>()
        if (hasOtherResources) {
            result += auth.copy(
                subject = "${auth.subject}-keepres",
                accessTo = accessToOthers,
                default = defaultOthers,
            )
        }
        if ((accessToHasTarget || defaultHasTarget) && hasOtherSubjects) {
            result += auth.copy(
                subject = "${auth.subject}-keepsubj",
                accessTo = if (accessToHasTarget) listOf(resourceUri) else emptyList(),
                default = if (defaultHasTarget) listOf(resourceUri) else emptyList(),
                agents = agentsWithoutR,
                agentClasses = classesWithoutR,
                agentGroups = groupsWithoutR,
            )
        }
        return result
    }

    private fun ruleMatches(
        auth: AclAuthorization,
        resourceUri: URI,
        receiver: ShareReceiver,
    ): Boolean {
        val touchesResource =
            auth.accessTo.any { IriUtils.sameIri(it.toString(), resourceUri.toString()) } ||
                    auth.default.any { IriUtils.sameIri(it.toString(), resourceUri.toString()) }
        if (!touchesResource) return false
        return when (receiver) {
            is ShareReceiver.WebIdReceiver ->
                auth.agents.any { IriUtils.sameIri(it.toString(), receiver.webId) }

            is ShareReceiver.GroupReceiver ->
                auth.agentGroups.any { IriUtils.sameIri(it.toString(), receiver.groupUri) }

            is ShareReceiver.Public ->
                auth.agentClasses.any {
                    IriUtils.sameIri(it.toString(), ShareReceiver.Public.toRdfSubject())
                }
        }
    }
}
