package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SAI
import com.erfangholami.androidsolidservices.shared.vocab.ShareNotificationVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareNotificationVocabulary
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * RDF wrapper for an `solidshare:AccessRequest` inbox notification — the
 * payload sent by a requester to a resource owner's LDN inbox:
 *
 * ```turtle
 * <#req>
 *     rdf:type                interop:AccessRequest ;
 *     as:actor                <{requesterWebId}> ;
 *     as:object               <{resourceUri}> ;
 *     as:target               <{ownerWebId}> ;
 *     acl:mode                acl:Read ;
 *     as:summary              "Please share these photos with me." ;
 *     as:published            "…"^^xsd:dateTime .
 * ```
 *
 * The activity is typed with the SAI term `interop:AccessRequest`; a
 * `solidshare:AccessRequest` type is also recognised. The requested mode is
 * read from the standard WAC `acl:mode` IRI ([aclModes]), falling back to a
 * `solidshare:requestedMode` literal ([requestedMode]).
 *
 * The reflective constructor required by the Solid resource parser is
 * provided.
 */
public class ShareRequestRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /** Subject of the first AccessRequest in the document, or null. */
    public fun requestSubject(): String? = getAllQuads()
        .firstOrNull {
            it.predicate == RDF.TYPE &&
                    (it.`object` == SAI.ACCESS_REQUEST || it.`object` == SolidShare.ACCESS_REQUEST)
        }
        ?.subject

    public fun actor(): String? = forSubject(AS.ACTOR)
    public fun activityObject(): String? = forSubject(AS.OBJECT)
    public fun target(): String? = forSubject(AS.TARGET)

    /** Fallback requested-mode literal (`solidshare:requestedMode "read"`). */
    public fun requestedMode(
        vocab: ShareNotificationVocabulary = SolidShareNotificationVocabulary,
    ): String? = forSubject(vocab.requestedModeLiteral)

    /** Standard WAC `acl:mode` IRIs requested. */
    public fun aclModes(): Set<String> {
        val subject = requestSubject() ?: return emptySet()
        return getAllQuads()
            .filter { it.subject == subject && it.predicate == ACL.MODE }
            .map { it.`object` }
            .toSet()
    }

    public fun summary(): String? = forSubject(AS.SUMMARY)
    public fun published(): String? = forSubject(AS.PUBLISHED)

    private fun forSubject(predicate: String): String? {
        val subject = requestSubject() ?: return null
        return getAllQuads()
            .firstOrNull { it.subject == subject && it.predicate == predicate }
            ?.`object`
    }
}
