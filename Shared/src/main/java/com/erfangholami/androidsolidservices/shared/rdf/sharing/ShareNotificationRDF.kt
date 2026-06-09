package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.ShareNotificationVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareNotificationVocabulary
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF wrapper for one item in an LDN inbox — read as an `as:Offer`,
 * `as:Undo`, or `as:Reject` activity:
 *
 * ```turtle
 * <#offer>
 *     rdf:type           as:Offer ;
 *     as:actor           <{ownerWebId}> ;
 *     as:object          <{resourceUri}> ;
 *     as:target          <{receiverWebId}> ;
 *     acl:mode           acl:Read ;
 *     as:published       "2026-05-29T10:00:00Z"^^xsd:dateTime .
 * ```
 *
 * Any of `as:Offer`, `as:Accept`, `as:Undo`, or `as:Reject` are accepted.
 * Notifications without a recognised type are returned with [activityType] =
 * null and should be skipped by callers. The access mode is read from the
 * standard WAC `acl:mode` IRI ([aclModes]); a `solidshare:mode` string
 * literal ([mode]) is also accepted as a fallback.
 *
 * The reflective constructor required by the Solid resource parser is
 * provided.
 */
public class ShareNotificationRDF : SolidRDFResource {

    private companion object {
        val RECOGNISED_TYPES = setOf(AS.OFFER, AS.ACCEPT, AS.UNDO, AS.REJECT)
    }

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /** Subject of the first recognised activity in the document, or null. */
    public fun activitySubject(): String? = getAllQuads()
        .firstOrNull {
            it.predicate == RDF.TYPE && it.`object` in RECOGNISED_TYPES
        }
        ?.subject

    /** Full type IRI of the activity, or null. */
    public fun activityType(): String? {
        val subject = activitySubject() ?: return null
        return getAllQuads()
            .firstOrNull {
                it.subject == subject && it.predicate == RDF.TYPE &&
                        it.`object` in RECOGNISED_TYPES
            }
            ?.`object`
    }

    public fun actor(): String? = forActivity(AS.ACTOR)
    public fun activityObject(): String? = forActivity(AS.OBJECT)
    public fun target(): String? = forActivity(AS.TARGET)

    /** Fallback access-mode literal (`solidshare:mode "read"`), if present. */
    public fun mode(
        vocab: ShareNotificationVocabulary = SolidShareNotificationVocabulary,
    ): String? = forActivity(vocab.modeLiteral)

    /** Standard WAC `acl:mode` IRIs asserted on the activity. */
    public fun aclModes(): Set<String> {
        val subject = activitySubject() ?: return emptySet()
        return getAllQuads()
            .filter { it.subject == subject && it.predicate == ACL.MODE }
            .map { it.`object` }
            .toSet()
    }

    /** `as:inReplyTo` — the AccessRequest this activity answers, if any. */
    public fun inReplyTo(): String? = forActivity(AS.IN_REPLY_TO)

    public fun summary(): String? = forActivity(AS.SUMMARY)
    public fun published(): String? = forActivity(AS.PUBLISHED)

    private fun forActivity(predicate: String): String? {
        val subject = activitySubject() ?: return null
        return getAllQuads()
            .firstOrNull { it.subject == subject && it.predicate == predicate }
            ?.`object`
    }
}
