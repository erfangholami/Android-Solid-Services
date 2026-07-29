package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.RDFS.COMMENT
import com.erfangholami.androidsolidservices.shared.vocab.RDFS.LABEL
import com.erfangholami.androidsolidservices.shared.vocab.RDFS.SUB_CLASS_OF

/**
 * RDF Schema (RDFS) vocabulary constants.
 * http://www.w3.org/2000/01/rdf-schema#
 *
 * Provides the basic class and property hierarchy terms used across RDF
 * vocabularies. Needed when reading or writing class/property definitions
 * embedded in Solid resources (e.g. [LABEL], [COMMENT], [SUB_CLASS_OF]).
 */
public object RDFS {
    public const val NAMESPACE: String = "http://www.w3.org/2000/01/rdf-schema#"
    public const val CLASS: String = "${NAMESPACE}Class"
    public const val LABEL: String = "${NAMESPACE}label"
    public const val COMMENT: String = "${NAMESPACE}comment"
    public const val SEE_ALSO: String = "${NAMESPACE}seeAlso"
    public const val IS_DEFINED_BY: String = "${NAMESPACE}isDefinedBy"
    public const val SUB_CLASS_OF: String = "${NAMESPACE}subClassOf"
    public const val SUB_PROPERTY_OF: String = "${NAMESPACE}subPropertyOf"
    public const val DOMAIN: String = "${NAMESPACE}domain"
    public const val RANGE: String = "${NAMESPACE}range"
    public const val MEMBER: String = "${NAMESPACE}member"
    public const val LITERAL: String = "${NAMESPACE}Literal"
    public const val DATATYPE: String = "${NAMESPACE}Datatype"
    public const val CONTAINER: String = "${NAMESPACE}Container"
    public const val CONTAINER_MEMBERSHIP_PROPERTY: String = "${NAMESPACE}ContainerMembershipProperty"
}
