package com.erfangholami.androidsolidservices.shared.model.access

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.util.tryParseUri
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * Represents a Web Access Control (WAC) ACL resource.
 *
 * An ACL resource is an RDF document containing one or more
 * `acl:Authorization` rules that govern access to the associated
 * subject resource or a container's member resources.
 *
 * Advertised via `Link: <acl-uri>; rel="acl"` on the subject resource.
 *
 * Spec: https://solidproject.org/TR/wac
 */
public class SolidACLResource : SolidRDFResource {

    public constructor(identifier: String) : this(identifier, null, null)

    public constructor(identifier: String, quads: List<RdfQuad>?, headers: SolidHeaders?) :
            this(identifier, "application/ld+json", quads, headers)

    public constructor(
        identifier: String,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers)

    /**
     * Returns all `acl:Authorization` instances in this ACL document.
     */
    public fun getAuthorizations(): List<AclAuthorization> {
        val authSubjects = quads
            .filter { it.predicate == RDF.TYPE && it.`object` == ACL.AUTHORIZATION }
            .map { it.subject }
            .distinct()

        return authSubjects.map { subjectIri ->
            val forSubject = quads.filter { it.subject == subjectIri }

            AclAuthorization(
                subject = subjectIri,

                accessTo = forSubject
                    .filter { it.predicate == ACL.ACCESS_TO }
                    .mapNotNull { tryParseUri(it.`object`, "SolidACLResource.accessTo")?.toString() },

                default = forSubject
                    .filter { it.predicate == ACL.DEFAULT }
                    .mapNotNull { tryParseUri(it.`object`, "SolidACLResource.default")?.toString() },

                modes = forSubject
                    .filter { it.predicate == ACL.MODE }
                    .map { it.`object` }
                    .toSet(),

                agents = forSubject
                    .filter { it.predicate == ACL.AGENT }
                    .mapNotNull { tryParseUri(it.`object`, "SolidACLResource.agents")?.toString() },

                agentClasses = forSubject
                    .filter { it.predicate == ACL.AGENT_CLASS }
                    .mapNotNull { tryParseUri(it.`object`, "SolidACLResource.agentClasses")?.toString() },

                agentGroups = forSubject
                    .filter { it.predicate == ACL.AGENT_GROUP }
                    .mapNotNull { tryParseUri(it.`object`, "SolidACLResource.agentGroups")?.toString() },

                origins = forSubject
                    .filter { it.predicate == ACL.ORIGIN }
                    .mapNotNull { tryParseUri(it.`object`, "SolidACLResource.origins")?.toString() },
            )
        }
    }

    /**
     * Adds a new authorization to this ACL document.
     */
    public fun addAuthorization(authorization: AclAuthorization) {
        val subject = authorization.subject
        addQuad(subject, RDF.TYPE, ACL.AUTHORIZATION, maxNumber = Int.MAX_VALUE)
        authorization.accessTo.forEach {
            addQuad(
                subject,
                ACL.ACCESS_TO,
                it.toString(),
                maxNumber = Int.MAX_VALUE
            )
        }
        authorization.default.forEach {
            addQuad(
                subject,
                ACL.DEFAULT,
                it.toString(),
                maxNumber = Int.MAX_VALUE
            )
        }
        authorization.modes.forEach { addQuad(subject, ACL.MODE, it, maxNumber = Int.MAX_VALUE) }
        authorization.agents.forEach {
            addQuad(
                subject,
                ACL.AGENT,
                it.toString(),
                maxNumber = Int.MAX_VALUE
            )
        }
        authorization.agentClasses.forEach {
            addQuad(
                subject,
                ACL.AGENT_CLASS,
                it.toString(),
                maxNumber = Int.MAX_VALUE
            )
        }
        authorization.agentGroups.forEach {
            addQuad(
                subject,
                ACL.AGENT_GROUP,
                it.toString(),
                maxNumber = Int.MAX_VALUE
            )
        }
        authorization.origins.forEach {
            addQuad(
                subject,
                ACL.ORIGIN,
                it.toString(),
                maxNumber = Int.MAX_VALUE
            )
        }
    }
}