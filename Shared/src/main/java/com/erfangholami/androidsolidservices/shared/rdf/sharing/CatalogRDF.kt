package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.ShareVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareVocabulary
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF wrapper for `{podRoot}/solidshare/catalog.ttl`, the owner-published
 * list of resources they may consider sharing on request.
 *
 * Each entry is a small named description:
 *
 * ```turtle
 * <{resourceUri}>
 *     rdf:type           solidshare:CatalogEntry ;
 *     dcterms:title      "Trip photos" ;
 *     dcterms:description "Photos from Crete, summer 2026." ;
 *     foaf:depiction     <https://alice.pod/avatars/trip.jpg> .
 * ```
 *
 * The catalog itself is publicly readable; the listed resources remain
 * private until granted.
 *
 * The reflective constructor required by the Solid resource parser is
 * provided.
 */
public class CatalogRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /** Returns every entry recorded in the catalog. */
    public fun getEntries(vocab: ShareVocabulary = SolidShareVocabulary): List<CatalogEntry> {
        val entrySubjects = getAllQuads()
            .filter { it.predicate == RDF.TYPE && it.`object` == vocab.catalogEntryType }
            .map { it.subject }
            .distinct()
        return entrySubjects.map { subject ->
            CatalogEntry(
                resourceUri = subject,
                title = forSubject(subject, DC.TITLE) ?: "",
                description = forSubject(subject, DC.DESCRIPTION),
                depictionUri = forSubject(subject, FOAF.DEPICTION),
            )
        }
    }

    /** Writes the triples for [entry] into the catalog document. */
    public fun addEntry(entry: CatalogEntry, vocab: ShareVocabulary = SolidShareVocabulary) {
        addQuad(entry.resourceUri, RDF.TYPE, vocab.catalogEntryType)
        addQuadLiteral(entry.resourceUri, DC.TITLE, entry.title)
        entry.description?.let { addQuadLiteral(entry.resourceUri, DC.DESCRIPTION, it) }
        entry.depictionUri?.let { addQuad(entry.resourceUri, FOAF.DEPICTION, it) }
    }

    private fun forSubject(subject: String, predicate: String): String? =
        getAllQuads().firstOrNull { it.subject == subject && it.predicate == predicate }?.`object`
}
