package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Linked Data Platform (LDP) vocabulary constants.
 * https://www.w3.org/ns/ldp#
 */
public object LDP {
    public const val NAMESPACE: String = "http://www.w3.org/ns/ldp#"

    public const val RESOURCE: String = "${NAMESPACE}Resource"
    public const val RDF_SOURCE: String = "${NAMESPACE}RDFSource"
    public const val NON_RDF_SOURCE: String = "${NAMESPACE}NonRDFSource"
    public const val CONTAINER: String = "${NAMESPACE}Container"
    public const val BASIC_CONTAINER: String = "${NAMESPACE}BasicContainer"
    public const val DIRECT_CONTAINER: String = "${NAMESPACE}DirectContainer"
    public const val INDIRECT_CONTAINER: String = "${NAMESPACE}IndirectContainer"

    /** Links a container to the resources it contains. */
    public const val CONTAINS: String = "${NAMESPACE}contains"

    /** Container membership predicate (DirectContainer / IndirectContainer). */
    public const val MEMBERSHIP_RESOURCE: String = "${NAMESPACE}membershipResource"
    public const val HAS_MEMBER_RELATION: String = "${NAMESPACE}hasMemberRelation"
    public const val IS_MEMBER_OF_RELATION: String = "${NAMESPACE}isMemberOfRelation"
    public const val INSERTED_CONTENT_RELATION: String = "${NAMESPACE}insertedContentRelation"
    public const val MEMBER: String = "${NAMESPACE}member"

    public const val PAGE_SEQUENCE: String = "${NAMESPACE}pageSequence"
    public const val NEXT_PAGE: String = "${NAMESPACE}nextPage"
    public const val PAGE: String = "${NAMESPACE}Page"

    public const val INBOX: String = "${NAMESPACE}inbox"
    public const val CONSTRAINED_BY: String = "${NAMESPACE}constrainedBy"
}
