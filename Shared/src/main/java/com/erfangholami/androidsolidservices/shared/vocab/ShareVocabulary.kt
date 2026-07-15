package com.erfangholami.androidsolidservices.shared.vocab

/**
 * The RDF vocabulary a sharing profile uses for its private given/received
 * index and its public catalog. Lets the sharing engine be retargeted to a
 * namespace other than SolidShare's without changing the index/catalog logic.
 *
 * The default is [SolidShareVocabulary].
 */
public interface ShareVocabulary {
    /** Namespace IRI the terms below belong to. */
    public val namespace: String

    /** `rdf:type` of a reified share record in an index. */
    public val shareType: String

    /** Predicate linking a share record to the shared resource IRI. */
    public val resource: String

    /** Predicate linking a given-share record to its receiver IRI. */
    public val receiver: String

    /** Predicate linking a received-share record to the owner WebID. */
    public val owner: String

    /** `rdf:type` of a public catalog entry. */
    public val catalogEntryType: String
}

/** The SolidShare vocabulary, hosted under `https://solidshare.app/ns#`. */
public object SolidShareVocabulary : ShareVocabulary {
    override val namespace: String = SolidShare.NAMESPACE
    override val shareType: String = SolidShare.SHARE
    override val resource: String = SolidShare.RESOURCE
    override val receiver: String = SolidShare.RECEIVER
    override val owner: String = SolidShare.OWNER
    override val catalogEntryType: String = "${SolidShare.NAMESPACE}CatalogEntry"
}
