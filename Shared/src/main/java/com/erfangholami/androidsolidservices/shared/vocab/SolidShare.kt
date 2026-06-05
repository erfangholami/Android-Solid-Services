package com.erfangholami.androidsolidservices.shared.vocab

/**
 * SolidShare-specific vocabulary used by the sharing pipeline.
 *
 * Carried in LDN inbox notifications (`as:Offer`) to tell the receiver the
 * access mode the owner granted. Not part of any external spec; the choice
 * to host the namespace under `solidshare.com` is consistent with the
 * project naming.
 */
public object SolidShare {
    public const val NAMESPACE: String = "https://solidshare.com/ns#"

    /** Access mode literal on an Offer notification: "read" | "append" | "write". */
    public const val MODE: String = "${NAMESPACE}mode"

    /**
     * Activity type carried on a request-to-share notification:
     * `<#req> rdf:type solidshare:AccessRequest`.
     * Sent by a requester to a resource owner's LDN inbox.
     */
    public const val ACCESS_REQUEST: String = "${NAMESPACE}AccessRequest"

    /** Mode literal on an AccessRequest: "read" | "append" | "write". */
    public const val REQUESTED_MODE: String = "${NAMESPACE}requestedMode"

    /**
     * `rdf:type` of a reified share record in the given/received index. Each
     * record bundles a resource, a counterpart, one or more `acl:mode`s, and a
     * `dcterms:created` timestamp on one subject node — so a share carries the
     * moment it was made. See `GivenSharesIndexRDF` / `ReceivedSharesIndexRDF`.
     */
    public const val SHARE: String = "${NAMESPACE}Share"

    /** The shared resource IRI on a [SHARE] record. */
    public const val RESOURCE: String = "${NAMESPACE}resource"

    /**
     * The receiver IRI on a given-share [SHARE] record: a WebID, a
     * `vcard:Group` URI, or `foaf:Agent` for public.
     */
    public const val RECEIVER: String = "${NAMESPACE}receiver"

    /** The owner WebID on a received-share [SHARE] record. */
    public const val OWNER: String = "${NAMESPACE}owner"
}
