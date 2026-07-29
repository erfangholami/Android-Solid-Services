package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.collapseByReceiver
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.ShareVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.VCARD

/**
 * RDF wrapper for `{podRoot}/solidshare/shares/given_shares.ttl`.
 *
 * Each share is a reified record node so it can carry a creation timestamp:
 *
 * ```turtle
 * <#share-…>
 *     rdf:type            solidshare:Share ;
 *     solidshare:resource <resourceUri> ;
 *     solidshare:receiver <receiver> ;
 *     acl:mode            acl:Read , acl:Append ;
 *     dcterms:created     "2026-06-04T12:00:00Z"^^xsd:dateTime .
 * ```
 *
 * One record holds every mode granted to a `(receiver, resourceUri)` pair, so
 * the pair carries a single timestamp. Group receivers keep the marker triple
 * `<receiver> rdf:type vcard:Group` (shared with the legacy format) to
 * disambiguate a group URI from a WebID.
 *
 * **Legacy reads.** Files written by earlier versions stored a share as a bare
 * `<receiver> <acl:Read|Append|Write> <resourceUri>` triple with no node and no
 * time. [getShares] still surfaces those (with `createdAt = null`) so old
 * indexes and ACL-rebuilt indexes keep working; the writer migrates a pair to
 * node form the next time it is touched.
 *
 * The reflective constructor required by the Solid resource parser is provided.
 */
public class GivenSharesIndexRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /**
     * One reified `solidshare:Share` record. [subject] is the node IRI (the
     * writer targets patch deletes at it); [modes] are every `acl:mode` asserted
     * on it; [createdAt] is the record's `dcterms:created`, or `null` if absent.
     */
    public data class Node(
        val subject: String,
        val receiver: ShareReceiver,
        val resourceUri: String,
        val modes: Set<ShareMode>,
        val createdAt: String?,
    )

    /** The reified share records (node form only — excludes any legacy flat rows). */
    public fun getShareNodes(vocab: ShareVocabulary = SolidShareVocabulary): List<Node> {
        val all = getAllQuads()
        val groupSubjects = groupSubjects(all)
        return all.asSequence()
            .filter { it.predicate == RDF.TYPE && it.`object` == vocab.shareType }
            .map { it.subject }
            .distinct()
            .mapNotNull { subject ->
                val nodeQuads = all.filter { it.subject == subject }
                val resourceUri = nodeQuads.firstOrNull {
                    it.predicate == vocab.resource && !it.isLiteralObject
                }?.`object` ?: return@mapNotNull null
                val receiverIri = nodeQuads.firstOrNull {
                    it.predicate == vocab.receiver && !it.isLiteralObject
                }?.`object` ?: return@mapNotNull null
                val modes = nodeQuads
                    .filter { it.predicate == ACL.MODE }
                    .mapNotNull { ShareMode.fromAclPredicate(it.`object`) }
                    .toSet()
                if (modes.isEmpty()) return@mapNotNull null
                Node(
                    subject = subject,
                    receiver = ShareReceiver.from(
                        rdfSubject = receiverIri,
                        isGroup = receiverIri in groupSubjects,
                    ),
                    resourceUri = resourceUri,
                    modes = modes,
                    createdAt = nodeQuads.firstOrNull { it.predicate == DC.CREATED }?.`object`,
                )
            }
            .toList()
    }

    /**
     * Shares stored in the legacy bare-triple form
     * `<receiver> <acl:mode> <resourceUri>` (no record node, `createdAt = null`).
     */
    public fun getLegacyFlatShares(): List<GivenShare> {
        val all = getAllQuads()
        val groupSubjects = groupSubjects(all)
        return all.mapNotNull { q ->
            if (q.isLiteralObject) return@mapNotNull null
            val mode = ShareMode.fromAclPredicate(q.predicate) ?: return@mapNotNull null
            GivenShare(
                receiver = ShareReceiver.from(q.subject, isGroup = q.subject in groupSubjects),
                mode = mode,
                resourceUri = q.`object`,
                createdAt = null,
            )
        }
    }

    /**
     * Every share, one row per `(receiver, resourceUri)` at the strongest mode:
     * reified records first, then any legacy rows for pairs they don't cover. The
     * several implied acl:modes a grant writes (e.g. Read+Append for "Add") are
     * folded into the single logical level via [collapseByReceiver], so a receiver
     * never appears more than once per resource.
     */
    public fun getShares(vocab: ShareVocabulary = SolidShareVocabulary): List<GivenShare> {
        val nodeShares = getShareNodes(vocab).flatMap { node ->
            node.modes.map { mode ->
                GivenShare(node.receiver, mode, node.resourceUri, node.createdAt)
            }
        }
        val coveredPairs = nodeShares
            .map { it.receiver.toRdfSubject() to it.resourceUri }
            .toSet()
        val legacy = getLegacyFlatShares().filter {
            (it.receiver.toRdfSubject() to it.resourceUri) !in coveredPairs
        }
        return (nodeShares + legacy).collapseByReceiver()
    }

    private fun groupSubjects(all: List<RdfQuad>): Set<String> = all
        .asSequence()
        .filter { it.predicate == RDF.TYPE && it.`object` == VCARD.GROUP }
        .map { it.subject }
        .toSet()
}
