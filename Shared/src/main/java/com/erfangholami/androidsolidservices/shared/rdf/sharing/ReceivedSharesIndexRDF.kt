package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF wrapper for `{podRoot}/solidshare/shares/received_shares.ttl`.
 *
 * Each received share is represented as a triple:
 *   `<ownerWebId> <acl:Read|Append|Write> <resourceUri> .`
 */
public class ReceivedSharesIndexRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /** Returns every received-share triple in the document. */
    public fun getShares(): List<ReceivedShare> {
        return getAllQuads().mapNotNull { q ->
            val mode = ShareMode.fromAclPredicate(q.predicate) ?: return@mapNotNull null
            ReceivedShare(
                ownerWebId = q.subject,
                mode = mode,
                resourceUri = q.`object`,
            )
        }
    }
}
