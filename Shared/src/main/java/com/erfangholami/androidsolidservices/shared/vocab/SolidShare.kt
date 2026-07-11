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

    /**
     * Barcode symbology literal on a `schema:Ticket` (the name of a
     * `TicketBarcodeFormat` constant, e.g. `"QR_CODE"`, `"AZTEC"`).
     * Together with `schema:ticketToken` it lets the exact barcode the
     * issuer produced be re-rendered for gate scanners.
     */
    public const val BARCODE_FORMAT: String = "${NAMESPACE}barcodeFormat"

    /** Ticket category literal (the name of a `TicketCategory` constant, e.g. `"EVENT"`). */
    public const val CATEGORY: String = "${NAMESPACE}category"

    /** Provenance literal on a ticket (the name of a `TicketSource` constant, e.g. `"PKPASS"`). */
    public const val SOURCE: String = "${NAMESPACE}source"

    /** Links a `schema:Ticket` to its embedded `schema:Event` node. */
    public const val EVENT: String = "${NAMESPACE}event"

    /**
     * Links a ticket to the original imported artifact it was created from
     * (e.g. the `.pkpass` file), stored as a sibling binary resource.
     */
    public const val ARTIFACT: String = "${NAMESPACE}artifact"

    /** Cached issuer display name on a tickets-index row. */
    public const val ISSUER: String = "${NAMESPACE}issuer"
}
