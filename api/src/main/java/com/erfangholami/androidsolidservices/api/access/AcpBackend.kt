package com.erfangholami.androidsolidservices.api.access

import android.util.Log
import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.collapseByReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.ACP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import java.net.URI
import java.util.UUID

private const val TAG = "AcpBackend"

/**
 * ACP implementation of [AccessBackend].
 *
 * Emits the canonical "policy-per-(receiver, mode)" shape used by Inrupt
 * ESS and CSS in ACP mode:
 *
 * ```turtle
 * <>
 *     a acp:AccessControlResource ;
 *     acp:resource     <resource> ;
 *     acp:accessControl <#ac-abc> .
 *
 * <#ac-abc> a acp:AccessControl ; acp:apply <#policy-abc> .
 *
 * <#policy-abc>
 *     a acp:Policy ;
 *     acp:allow acl:Read ;
 *     acp:allOf <#matcher-abc> .
 *
 * <#matcher-abc>
 *     a acp:Matcher ;
 *     acp:agent <https://bob.example/profile#me> .
 * ```
 *
 * Owner self-rules use `acp:agent <ownerWebId>` and grant the full WAC
 * mode set. Public matchers use `acp:agent acp:PublicAgent`. ACP has no
 * native agent-group primitive (unlike WAC's `acl:agentGroup`), so group
 * receivers are rejected here rather than written as an `acp:vc` matcher
 * that would grant no one; share to explicit member WebIDs instead.
 *
 * Containers add an `acp:memberAccessControl <#ac-…>` triple to the ACR
 * mirroring `acp:accessControl`, so descendants inherit the same policies.
 *
 * ACR updates use conditional writes (If-Match / ETag), matching the
 * behaviour of [WacBackend].
 *
 * Spec: https://solidproject.org/TR/acp
 */
internal class AcpBackend(private val rm: SolidResourceManager) : AccessBackend {

    override suspend fun grant(
        webId: String,
        resourceUri: URI,
        mode: ShareMode,
        receiver: ShareReceiver,
        isContainer: Boolean,
        includeImpliedModes: Boolean,
    ) {
        rejectUnsupportedReceiver(receiver, resourceUri)
        val read = readAcr(webId, resourceUri).orThrowIfUnparseable(resourceUri)
        val keep = read.acr.getAllQuads().toMutableList()

        val priorMatcherSubjects = keep
            .filter { it.predicate == RDF.TYPE && it.`object` == ACP.MATCHER }
            .map { it.subject }
            .filter { matcherSubject -> matcherTargets(keep, matcherSubject, receiver) }
            .toSet()
        val priorPolicySubjects = keep
            .filter { it.predicate == ACP.ALL_OF && it.`object` in priorMatcherSubjects }
            .map { it.subject }
            .toSet()
        val priorAcSubjects = keep
            .filter { it.predicate == ACP.APPLY && it.`object` in priorPolicySubjects }
            .map { it.subject }
            .toSet()
        keep.removeAll { q ->
            q.subject in priorAcSubjects ||
                    q.subject in priorPolicySubjects ||
                    q.subject in priorMatcherSubjects ||
                    (q.predicate == ACP.ACCESS_CONTROL && q.`object` in priorAcSubjects) ||
                    (q.predicate == ACP.MEMBER_ACCESS_CONTROL && q.`object` in priorAcSubjects)
        }

        val acrUri = read.acrUri
        val freshAcr = SolidRDFResource(acrUri, "application/ld+json", keep, null)

        if (!hasOwnerSelfControl(freshAcr.getAllQuads(), webId)) {
            appendPolicy(
                acr = freshAcr,
                acrUri = acrUri,
                receiver = ShareReceiver.WebIdReceiver(webId),
                modes = setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
                isContainer = isContainer,
                ownerSelf = true,
            )
        }

        appendPolicy(
            acr = freshAcr,
            acrUri = acrUri,
            receiver = receiver,
            modes = if (includeImpliedModes) {
                mode.impliedAclModes()
            } else {
                setOf(mode.toAclPredicate())
            },
            isContainer = isContainer,
            ownerSelf = false,
        )

        writeAcr(webId, acrUri, resourceUri, freshAcr, ifMatch = read.etag)
    }

    override suspend fun revoke(
        webId: String,
        resourceUri: URI,
        receiver: ShareReceiver,
        isContainer: Boolean,
    ) {
        val read = readAcr(webId, resourceUri).orThrowIfUnparseable(resourceUri)
        val quads = read.acr.getAllQuads().toMutableList()

        val matcherSubjects = quads
            .filter { it.predicate == RDF.TYPE && it.`object` == ACP.MATCHER }
            .map { it.subject }
            .filter { matcherTargets(quads, it, receiver) }
            .toSet()
        val policySubjects = quads
            .filter { it.predicate == ACP.ALL_OF && it.`object` in matcherSubjects }
            .map { it.subject }
            .toSet()
        val acSubjects = quads
            .filter { it.predicate == ACP.APPLY && it.`object` in policySubjects }
            .map { it.subject }
            .toSet()
        quads.removeAll { q ->
            q.subject in acSubjects ||
                    q.subject in policySubjects ||
                    q.subject in matcherSubjects ||
                    (q.predicate == ACP.ACCESS_CONTROL && q.`object` in acSubjects) ||
                    (q.predicate == ACP.MEMBER_ACCESS_CONTROL && q.`object` in acSubjects)
        }

        val freshAcr = SolidRDFResource(read.acrUri, "application/ld+json", quads, null)
        if (!hasOwnerSelfControl(freshAcr.getAllQuads(), webId)) {
            appendPolicy(
                acr = freshAcr,
                acrUri = read.acrUri,
                receiver = ShareReceiver.WebIdReceiver(webId),
                modes = setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
                isContainer = isContainer,
                ownerSelf = true,
            )
        }
        writeAcr(webId, read.acrUri, resourceUri, freshAcr, ifMatch = read.etag)
    }

    override suspend fun listShares(
        webId: String,
        resourceUri: URI,
    ): List<GivenShare> {
        val read = readAcr(webId, resourceUri).orThrowIfUnparseable(resourceUri)
        val quads = read.acr.getAllQuads()
        val shares = mutableListOf<GivenShare>()

        val acSubjects = quads
            .filter { it.predicate == RDF.TYPE && it.`object` == ACP.ACCESS_CONTROL_TYPE }
            .map { it.subject }
            .distinct()

        acSubjects.forEach { ac ->
            val policySubjects = quads
                .filter { it.subject == ac && it.predicate == ACP.APPLY }
                .map { it.`object` }
            policySubjects.forEach { policy ->
                val modes = quads
                    .filter { it.subject == policy && it.predicate == ACP.ALLOW }
                    .mapNotNull { ShareMode.fromAclPredicate(it.`object`) }
                if (modes.isEmpty()) return@forEach
                val matcherSubjects = quads
                    .filter { it.subject == policy && it.predicate == ACP.ALL_OF }
                    .map { it.`object` }
                matcherSubjects.forEach { matcher ->
                    val receivers = receiversFromMatcher(quads, matcher, webId)
                    receivers.forEach { receiver ->
                        modes.forEach { mode ->
                            shares += GivenShare(
                                receiver = receiver,
                                mode = mode,
                                resourceUri = resourceUri.toString(),
                            )
                        }
                    }
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
        val metadataResp = rm.head(webId, targetUri)
        if (metadataResp is SolidResult.Success &&
            isAlreadyOwnerOnly(metadataResp.value.wacAllow)
        ) return

        val read = readAcr(webId, targetUri).orThrowIfUnparseable(targetUri)
        if (hasOwnerSelfControl(read.acr.getAllQuads(), webId)) return
        appendPolicy(
            acr = read.acr,
            acrUri = read.acrUri,
            receiver = ShareReceiver.WebIdReceiver(webId),
            modes = setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
            isContainer = isContainer,
            ownerSelf = true,
        )
        writeAcr(webId, read.acrUri, targetUri, read.acr, ifMatch = read.etag)
    }

    override suspend fun reclaimOwnerControl(
        webId: String,
        targetUri: URI,
        isContainer: Boolean,
    ) {
        val read = readAcr(webId, targetUri).orThrowIfUnparseable(targetUri)
        if (hasOwnerSelfControl(read.acr.getAllQuads(), webId)) return
        appendPolicy(
            acr = read.acr,
            acrUri = read.acrUri,
            receiver = ShareReceiver.WebIdReceiver(webId),
            modes = setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
            isContainer = isContainer,
            ownerSelf = true,
        )
        writeAcr(webId, read.acrUri, targetUri, read.acr, ifMatch = read.etag)
    }

    private data class AcrRead(
        val acrUri: URI,
        val acr: SolidRDFResource,
        val etag: String?,
        /**
         * `true` when the ACR endpoint returned a body we could **not** parse
         * (so [acr] is an empty placeholder). An existing-but-unparseable ACR is
         * *indeterminate*, not "empty": both read ([listShares]) and write
         * (grant / revoke / ensureOwnerOnly / reclaimOwnerControl) paths
         * fail-fast via [orThrowIfUnparseable] rather than act on it. Reading it
         * as "no shares" would prune live rows; writing a fresh document over it
         * would silently strip every co-receiver's grant.
         */
        val parseFailed: Boolean = false,
    )

    /**
     * Guards against acting on an ACR that exists but could not be parsed (e.g.
     * a Turtle-serialised ACR before the Turtle reader lands, or malformed
     * JSON-LD). Surfaces a typed failure instead of silently dropping grants.
     */
    private fun AcrRead.orThrowIfUnparseable(resourceUri: URI): AcrRead {
        if (parseFailed) {
            throw SharingException.UnsupportedAuthBackend(
                resourceUri.toString(), backend = "ACP (unreadable ACR)",
            )
        }
        return this
    }

    /**
     * ACP has no native agent-group primitive (unlike WAC's `acl:agentGroup`),
     * so a group receiver can't be expressed as a matcher that actually grants
     * its members access. Reject it explicitly rather than write an `acp:vc`
     * matcher that matches no one.
     */
    private fun rejectUnsupportedReceiver(receiver: ShareReceiver, resourceUri: URI) {
        if (receiver is ShareReceiver.GroupReceiver) {
            throw SharingException.UnsupportedAuthBackend(
                resourceUri.toString(), backend = "ACP (group receivers)",
            )
        }
    }

    private suspend fun readAcr(webId: String, resourceUri: URI): AcrRead {
        val metadata = rm.head(webId, resourceUri).getOrThrow()
        val acrUri = metadata.aclUri
            ?: throw SharingException.UnsupportedAuthBackend(
                resourceUri.toString(), backend = "ACP",
            )
        val existing = rm.head(webId, acrUri)
        if (existing !is SolidResult.Success) {
            return AcrRead(
                acrUri = acrUri,
                acr = SolidRDFResource(acrUri, "application/ld+json", emptyList(), null),
                etag = null,
            )
        }
        val parsed = runCatching {
            rm.read(webId, acrUri, SolidRDFResource::class.java).getOrThrow()
        }.getOrElse { e ->
            Log.w(
                TAG,
                "ACR read failed at $acrUri (${e.javaClass.simpleName}: ${e.message}). " +
                        "Falling back to empty ACR; writeAcr will PUT a fresh document.",
            )
            null
        }
        return AcrRead(
            acrUri = acrUri,
            acr = parsed ?: SolidRDFResource(acrUri, "application/ld+json", emptyList(), null),
            etag = if (parsed != null) existing.value.etag else null,
            parseFailed = parsed == null,
        )
    }

    private suspend fun writeAcr(
        webId: String,
        acrUri: URI,
        resourceUri: URI,
        acr: SolidRDFResource,
        ifMatch: String?,
    ) {
        addQuadOnce(acr, acrUri.toString(), RDF.TYPE, ACP.ACCESS_CONTROL_RESOURCE)
        addQuadOnce(acr, acrUri.toString(), ACP.RESOURCE, resourceUri.toString())
        val body = NTriples.serialize(acr.getAllQuads()).toByteArray(Charsets.UTF_8)
        val result = rm.putRaw(
            webid = webId,
            uri = acrUri,
            contentType = NTriples.MEDIA_TYPE,
            body = body,
            ifMatch = ifMatch,
            linkHeader = null,
        )
        when (result) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure -> {
                if (result.error.code == SolidErrorCode.PRECONDITION_FAILED) {
                    throw SharingException.StaleAcl(acrUri.toString())
                }
                error("ACR write failed: ${result.error.message}")
            }
        }
    }

    private fun appendPolicy(
        acr: SolidRDFResource,
        acrUri: URI,
        receiver: ShareReceiver,
        modes: Set<String>,
        isContainer: Boolean,
        ownerSelf: Boolean,
    ) {
        val suffix = if (ownerSelf) "owner-${UUID.randomUUID()}" else UUID.randomUUID().toString()
        val ac = "${acrUri}#ac-$suffix"
        val policy = "${acrUri}#policy-$suffix"
        val matcher = "${acrUri}#matcher-$suffix"

        acr.addQuad(acrUri.toString(), ACP.ACCESS_CONTROL, ac, maxNumber = Int.MAX_VALUE)
        if (isContainer) {
            acr.addQuad(acrUri.toString(), ACP.MEMBER_ACCESS_CONTROL, ac, maxNumber = Int.MAX_VALUE)
        }

        addQuadOnce(acr, ac, RDF.TYPE, ACP.ACCESS_CONTROL_TYPE)
        addQuadOnce(acr, ac, ACP.APPLY, policy)

        addQuadOnce(acr, policy, RDF.TYPE, ACP.POLICY)
        modes.forEach { acr.addQuad(policy, ACP.ALLOW, it, maxNumber = Int.MAX_VALUE) }
        addQuadOnce(acr, policy, ACP.ALL_OF, matcher)

        addQuadOnce(acr, matcher, RDF.TYPE, ACP.MATCHER)
        when (receiver) {
            is ShareReceiver.WebIdReceiver ->
                acr.addQuad(matcher, ACP.AGENT, receiver.webId, maxNumber = Int.MAX_VALUE)

            is ShareReceiver.GroupReceiver ->
                error("ACP group receivers are rejected in grant() before reaching appendPolicy")

            is ShareReceiver.Public ->
                acr.addQuad(matcher, ACP.AGENT, ACP.PUBLIC_AGENT, maxNumber = Int.MAX_VALUE)
        }
    }

    private fun addQuadOnce(
        acr: SolidRDFResource,
        subject: String,
        predicate: String,
        obj: String,
    ) {
        val alreadyThere = acr.getAllQuads().any {
            it.subject == subject && it.predicate == predicate && it.`object` == obj
        }
        if (!alreadyThere) acr.addQuad(subject, predicate, obj, maxNumber = Int.MAX_VALUE)
    }

    private fun hasOwnerSelfControl(
        quads: List<com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad>,
        webId: String,
    ): Boolean {
        val matcherSubjects = quads
            .filter { it.predicate == ACP.AGENT && it.`object` == webId }
            .map { it.subject }
            .filter { ms ->
                quads.any {
                    it.subject == ms && it.predicate == RDF.TYPE && it.`object` == ACP.MATCHER
                }
            }
            .toSet()
        val policySubjects = quads
            .filter { it.predicate == ACP.ALL_OF && it.`object` in matcherSubjects }
            .map { it.subject }
            .toSet()
        return policySubjects.any { policy ->
            val allowed = quads
                .filter { it.subject == policy && it.predicate == ACP.ALLOW }
                .map { it.`object` }
                .toSet()
            allowed.containsAll(setOf(ACL.READ, ACL.WRITE, ACL.CONTROL))
        }
    }

    private fun matcherTargets(
        quads: List<com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad>,
        matcher: String,
        receiver: ShareReceiver,
    ): Boolean = when (receiver) {
        is ShareReceiver.WebIdReceiver ->
            quads.any { it.subject == matcher && it.predicate == ACP.AGENT && it.`object` == receiver.webId }

        is ShareReceiver.GroupReceiver ->
            quads.any { it.subject == matcher && it.predicate == ACP.VC && it.`object` == receiver.groupUri }

        is ShareReceiver.Public ->
            quads.any { it.subject == matcher && it.predicate == ACP.AGENT && it.`object` == ACP.PUBLIC_AGENT }
    }

    private fun receiversFromMatcher(
        quads: List<com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad>,
        matcher: String,
        webId: String,
    ): List<ShareReceiver> = buildList {
        val agents = quads
            .filter { it.subject == matcher && it.predicate == ACP.AGENT }
            .map { it.`object` }
        agents.forEach { agent ->
            if (agent == webId) return@forEach
            if (agent == ACP.PUBLIC_AGENT) add(ShareReceiver.Public)
            else add(ShareReceiver.WebIdReceiver(agent))
        }
        // acp:vc is the verifiable-credential matcher, not an agent group: it is
        // deliberately not surfaced as a receiver (mapping it to a group both
        // misrepresents a real VC matcher and resurrects legacy no-op group rows).
    }
}
