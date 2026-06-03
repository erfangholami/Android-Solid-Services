package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF wrapper for `{podRoot}/solidshare/shares/given_shares.ttl`.
 *
 * Each share is represented as a single triple:
 *   `<receiver> <acl:Read|Append|Write> <resourceUri> .`
 *
 * Group receivers carry an additional marker triple to disambiguate them
 * from WebIDs, since both are bare IRIs otherwise:
 *   `<receiver> rdf:type vcard:Group .`
 *
 * - subject  = receiver (WebID, group URI, or `foaf:Agent` for public)
 * - predicate = WAC mode
 * - object   = resource URI
 *
 * The reflective constructor required by the Solid resource parser is provided.
 */
public class GivenSharesIndexRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /** Returns every share triple in the document. */
    public fun getShares(): List<GivenShare> {
        val all = getAllQuads()
        val groupSubjects: Set<String> = all
            .asSequence()
            .filter { it.predicate == RDF.TYPE && it.`object` == VCARD.GROUP }
            .map { it.subject }
            .toSet()
        return all.mapNotNull { q ->
            val mode = ShareMode.fromAclPredicate(q.predicate) ?: return@mapNotNull null
            val receiver = ShareReceiver.from(
                rdfSubject = q.subject,
                isGroup = q.subject in groupSubjects,
            )
            GivenShare(
                receiver = receiver,
                mode = mode,
                resourceUri = q.`object`,
            )
        }
    }
}
