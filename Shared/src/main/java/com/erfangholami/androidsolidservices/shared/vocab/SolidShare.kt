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
}
