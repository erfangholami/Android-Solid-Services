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
import java.util.UUID

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
        resourceUri: String,
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
                listOf(it.webId)
            } ?: emptyList(),
            agentClasses = if (receiver is ShareReceiver.Public) {
                listOf(ShareReceiver.Public.toRdfSubject())
            } else emptyList(),
            agentGroups = (receiver as? ShareReceiver.GroupReceiver)?.let {
                listOf(it.groupUri)
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
        resourceUri: String,
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
        resourceUri: String,
    ): List<GivenShare> {
        val read = readAcl(webId, resourceUri)
        val shares = mutableListOf<GivenShare>()
        val authorizations = baseAuthorizations(
            webId, resourceUri, read, isContainer = resourceUri.endsWith("/"),
        )
        authorizations.forEach { auth ->
            val applies =
                auth.accessTo.any { IriUtils.sameIri(it, resourceUri) } ||
                        auth.default.any { IriUtils.sameIri(it, resourceUri) }
            if (!applies) return@forEach
            if (isOwnerSelfRule(auth, webId)) return@forEach

            val mode = ShareMode.strongest(auth.modes) ?: return@forEach

            auth.agents.forEach { agent ->
                if (!IriUtils.sameIri(agent, webId)) {
                    shares += GivenShare(
                        receiver = ShareReceiver.WebIdReceiver(agent),
                        mode = mode,
                        resourceUri = resourceUri,
                    )
                }
            }
            auth.agentGroups.forEach { g ->
                shares += GivenShare(
                    receiver = ShareReceiver.GroupReceiver(g),
                    mode = mode,
                    resourceUri = resourceUri,
                )
            }
            auth.agentClasses.forEach { c ->
                if (IriUtils.sameIri(c, ShareReceiver.Public.toRdfSubject())) {
                    shares += GivenShare(
                        receiver = ShareReceiver.Public,
                        mode = mode,
                        resourceUri = resourceUri,
                    )
                }
            }
        }
        return shares.collapseByReceiver()
    }

    override suspend fun ensureOwnerOnly(
        webId: String,
        targetUri: String,
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
        targetUri: String,
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
        val aclUri: String,
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
        resourceUri: String,
        read: AclRead,
        isContainer: Boolean,
    ): List<AclAuthorization> {
        if (read.etag != null) return read.acl.getAuthorizations()
        return inheritedDefaultAuthorizations(webId, resourceUri, read.aclUri, isContainer)
    }

    private suspend fun inheritedDefaultAuthorizations(
        webId: String,
        resourceUri: String,
        aclUri: String,
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
        container: String,
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

    private fun parentContainerOf(uri: String): String? {
        val trimmed = if (uri.endsWith("/")) uri.dropLast(1) else uri
        val schemeEnd = uri.indexOf("://")
        val lastSlash = trimmed.lastIndexOf('/')
        if (schemeEnd < 0 || lastSlash <= schemeEnd + 2) return null
        return trimmed.substring(0, lastSlash + 1)
    }

    private suspend fun readAcl(webId: String, resourceUri: String): AclRead {
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
        aclUri: String,
        acl: SolidACLResource,
        ifMatch: String?,
    ) {
        val body = NTriples.serialize(acl.getAllQuads()).toByteArray(Charsets.UTF_8)
        val result = rm.putRaw(
            webId = webId,
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
                    throw SharingException.StaleAcl(aclUri)
                }
                error("ACL write failed: ${result.error.message}")
            }
        }
    }

    private fun ownerSelfAuth(
        webId: String,
        aclUri: String,
        targetUri: String,
        isContainer: Boolean,
    ): AclAuthorization = AclAuthorization(
        subject = "${aclUri}#owner",
        accessTo = listOf(targetUri),
        default = if (isContainer) listOf(targetUri) else emptyList(),
        modes = setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
        agents = listOf(webId),
    )

    private fun isOwnerSelfRule(auth: AclAuthorization, webId: String): Boolean =
        auth.agents.any { IriUtils.sameIri(it, webId) } &&
                auth.modes.containsAll(setOf(ACL.READ, ACL.WRITE, ACL.CONTROL))

    private fun narrowAuthorization(
        auth: AclAuthorization,
        resourceUri: String,
        receiver: ShareReceiver,
    ): List<AclAuthorization> {
        val accessToHasTarget = auth.accessTo.any { IriUtils.sameIri(it, resourceUri) }
        val defaultHasTarget = auth.default.any { IriUtils.sameIri(it, resourceUri) }
        val accessToOthers = auth.accessTo.filterNot { IriUtils.sameIri(it, resourceUri) }
        val defaultOthers = auth.default.filterNot { IriUtils.sameIri(it, resourceUri) }
        val hasOtherResources = accessToOthers.isNotEmpty() || defaultOthers.isNotEmpty()

        val agentsWithoutR = if (receiver is ShareReceiver.WebIdReceiver) {
            auth.agents.filterNot { IriUtils.sameIri(it, receiver.webId) }
        } else {
            auth.agents
        }
        val groupsWithoutR = if (receiver is ShareReceiver.GroupReceiver) {
            auth.agentGroups.filterNot { IriUtils.sameIri(it, receiver.groupUri) }
        } else {
            auth.agentGroups
        }
        val classesWithoutR = if (receiver is ShareReceiver.Public) {
            auth.agentClasses.filterNot {
                IriUtils.sameIri(it, ShareReceiver.Public.toRdfSubject())
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
        resourceUri: String,
        receiver: ShareReceiver,
    ): Boolean {
        val touchesResource =
            auth.accessTo.any { IriUtils.sameIri(it, resourceUri) } ||
                    auth.default.any { IriUtils.sameIri(it, resourceUri) }
        if (!touchesResource) return false
        return when (receiver) {
            is ShareReceiver.WebIdReceiver ->
                auth.agents.any { IriUtils.sameIri(it, receiver.webId) }

            is ShareReceiver.GroupReceiver ->
                auth.agentGroups.any { IriUtils.sameIri(it, receiver.groupUri) }

            is ShareReceiver.Public ->
                auth.agentClasses.any {
                    IriUtils.sameIri(it, ShareReceiver.Public.toRdfSubject())
                }
        }
    }
}
