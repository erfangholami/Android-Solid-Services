package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.collapseByOwner
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.ShareVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareVocabulary
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * RDF wrapper for `{podRoot}/solidshare/shares/received_shares.ttl`.
 *
 * Symmetric to `GivenSharesIndexRDF`: each received share is a reified record
 * node whose counterpart is the resource's owner, carrying the time the share
 * was recorded:
 *
 * ```turtle
 * <#share-…>
 *     rdf:type            solidshare:Share ;
 *     solidshare:resource <resourceUri> ;
 *     solidshare:owner    <ownerWebId> ;
 *     acl:mode            acl:Read ;
 *     dcterms:created     "2026-06-04T12:00:00Z"^^xsd:dateTime .
 * ```
 *
 * **Legacy reads.** Earlier versions stored a bare
 * `<ownerWebId> <acl:mode> <resourceUri>` triple with no node and no time;
 * [getShares] still surfaces those (with `addedAt = null`).
 */
public class ReceivedSharesIndexRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /**
     * One reified `solidshare:Share` record. [subject] is the node IRI (the
     * writer targets patch deletes at it); [mode] is the strongest `acl:mode`
     * asserted; [addedAt] is the record's `dcterms:created`, or `null`.
     */
    public data class Node(
        val subject: String,
        val ownerWebId: String,
        val resourceUri: String,
        val mode: ShareMode,
        val addedAt: String?,
    )

    /** The reified received-share records (node form only — excludes legacy flat rows). */
    public fun getShareNodes(vocab: ShareVocabulary = SolidShareVocabulary): List<Node> {
        val all = getAllQuads()
        return all.asSequence()
            .filter { it.predicate == RDF.TYPE && it.`object` == vocab.shareType }
            .map { it.subject }
            .distinct()
            .mapNotNull { subject ->
                val nodeQuads = all.filter { it.subject == subject }
                val resourceUri = nodeQuads.firstOrNull {
                    it.predicate == vocab.resource && !it.isLiteralObject
                }?.`object` ?: return@mapNotNull null
                val ownerWebId = nodeQuads.firstOrNull {
                    it.predicate == vocab.owner && !it.isLiteralObject
                }?.`object` ?: return@mapNotNull null
                val mode = ShareMode.strongest(
                    nodeQuads.filter { it.predicate == ACL.MODE }.map { it.`object` }.toSet(),
                ) ?: return@mapNotNull null
                Node(
                    subject = subject,
                    ownerWebId = ownerWebId,
                    resourceUri = resourceUri,
                    mode = mode,
                    addedAt = nodeQuads.firstOrNull { it.predicate == DC.CREATED }?.`object`,
                )
            }
            .toList()
    }

    /**
     * Shares stored in the legacy bare-triple form
     * `<ownerWebId> <acl:mode> <resourceUri>` (no record node, `addedAt = null`).
     */
    public fun getLegacyFlatShares(): List<ReceivedShare> =
        getAllQuads().mapNotNull { q ->
            if (q.isLiteralObject) return@mapNotNull null
            val mode = ShareMode.fromAclPredicate(q.predicate) ?: return@mapNotNull null
            ReceivedShare(
                ownerWebId = q.subject,
                mode = mode,
                resourceUri = q.`object`,
                addedAt = null,
            )
        }

    /**
     * Every received share, one row per `(ownerWebId, resourceUri)` at the
     * strongest mode: reified records first, then any legacy rows for pairs they
     * don't cover, folded via [collapseByOwner] so an owner never appears more
     * than once per resource.
     */
    public fun getShares(vocab: ShareVocabulary = SolidShareVocabulary): List<ReceivedShare> {
        val nodeShares = getShareNodes(vocab).map {
            ReceivedShare(it.ownerWebId, it.mode, it.resourceUri, it.addedAt)
        }
        val covered = nodeShares.map { it.ownerWebId to it.resourceUri }.toSet()
        val legacy = getLegacyFlatShares().filter { (it.ownerWebId to it.resourceUri) !in covered }
        return (nodeShares + legacy).collapseByOwner()
    }
}
