package com.erfangholami.androidsolidservices.shared.model.access

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.util.tryParseUri
import com.erfangholami.androidsolidservices.shared.vocab.ACP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * Represents an ACP Access Control Resource (ACR).
 *
 * An ACR is linked to its subject resource via `Link: rel="acl"` and
 * carries `rdf:type acp:AccessControlResource`. It contains
 * `acp:AccessControl` nodes that reference `acp:Policy` documents.
 *
 * Spec: https://solidproject.org/TR/acp
 */
public class SolidACR : SolidRDFResource {

    public constructor(identifier: String) : this(identifier, null, null)

    public constructor(identifier: String, quads: List<RdfQuad>?, headers: SolidHeaders?) :
            this(identifier, "application/ld+json", quads, headers)

    public constructor(
        identifier: String,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers)

    /** Returns `true` if the quad store declares `rdf:type acp:AccessControlResource`. */
    public fun isACR(): Boolean =
        quads.any { it.predicate == RDF.TYPE && it.`object` == ACP.ACCESS_CONTROL_RESOURCE }

    /**
     * Returns all `acp:AccessControl` IRIs referenced directly via
     * `acp:accessControl` on this ACR.
     */
    public fun getAccessControls(): List<String> =
        quads
            .filter { it.predicate == ACP.ACCESS_CONTROL }
            .mapNotNull { tryParseUri(it.`object`, "SolidACR.getAccessControls")?.toString() }

    /**
     * Returns all `acp:AccessControl` IRIs that apply transitively to
     * member resources via `acp:memberAccessControl`.
     */
    public fun getMemberAccessControls(): List<String> =
        quads
            .filter { it.predicate == ACP.MEMBER_ACCESS_CONTROL }
            .mapNotNull { tryParseUri(it.`object`, "SolidACR.getMemberAccessControls")?.toString() }

    /**
     * Returns all `acp:Policy` IRIs referenced by any access control in
     * this ACR via `acp:apply`.
     */
    public fun getPolicies(): List<String> =
        quads
            .filter { it.predicate == ACP.APPLY }
            .mapNotNull { tryParseUri(it.`object`, "SolidACR.getPolicies")?.toString() }

    /**
     * Returns the access modes granted by a policy identified by [policyIri].
     */
    public fun getAllowedModes(policyIri: String): Set<String> =
        quads
            .filter { it.subject == policyIri && it.predicate == ACP.ALLOW }
            .map { it.`object` }
            .toSet()

    /**
     * Returns the access modes denied by a policy identified by [policyIri].
     */
    public fun getDeniedModes(policyIri: String): Set<String> =
        quads
            .filter { it.subject == policyIri && it.predicate == ACP.DENY }
            .map { it.`object` }
            .toSet()
}