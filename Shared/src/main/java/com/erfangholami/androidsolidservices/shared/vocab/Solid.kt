package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.Solid.OIDC_ISSUER
import com.erfangholami.androidsolidservices.shared.vocab.Solid.STORAGE_DESCRIPTION


/**
 * Solid Terms vocabulary constants.
 * http://www.w3.org/ns/solid/terms#
 *
 * Core Solid-specific terms covering authentication discovery ([OIDC_ISSUER]),
 * storage description ([STORAGE_DESCRIPTION]), type indexes, N3 Patch documents,
 * and access-control agent classes.
 */
public object Solid {
    public const val NAMESPACE: String = "http://www.w3.org/ns/solid/terms#"

    //Authentication
    /** OpenID Connect issuer for a WebID. */
    public const val OIDC_ISSUER: String = "${NAMESPACE}oidcIssuer"

    /** Client identifier registration document. */
    public const val OIDC_REGISTRATION: String = "${NAMESPACE}oidcRegistration"

    //Storage
    /** Links a resource to a storage description resource. */
    public const val STORAGE_DESCRIPTION: String = "${NAMESPACE}storageDescription"

    /** Identifies the owner of a storage. */
    public const val OWNER: String = "${NAMESPACE}owner"

    //Type Index
    /** Links a WebID to a non-public type index (access-controlled). */
    public const val PRIVATE_TYPE_INDEX: String = "${NAMESPACE}privateTypeIndex"

    /** Links a WebID to a publicly readable type index. */
    public const val PUBLIC_TYPE_INDEX: String = "${NAMESPACE}publicTypeIndex"

    /** An entry in a type index that maps a class to one or more instances or containers. */
    public const val TYPE_REGISTRATION: String = "${NAMESPACE}TypeRegistration"

    /** The RDF class this registration entry applies to. */
    public const val FOR_CLASS: String = "${NAMESPACE}forClass"

    /** A specific resource instance that holds data of the registered class. */
    public const val INSTANCE: String = "${NAMESPACE}instance"

    /** A container whose members are instances of the registered class. */
    public const val INSTANCE_CONTAINER: String = "${NAMESPACE}instanceContainer"

    /** The type of a type index document itself. */
    public const val TYPE_INDEX: String = "${NAMESPACE}TypeIndex"

    /** A type index that is not listed in the public profile. */
    public const val UNLISTED_DOCUMENT: String = "${NAMESPACE}UnlistedDocument"

    /** A type index that is discoverable from the public profile. */
    public const val LISTED_DOCUMENT: String = "${NAMESPACE}ListedDocument"

    //N3 Patch
    /** rdf:type for a Solid N3 Patch document. */
    public const val INSERT_DELETE_PATCH: String = "${NAMESPACE}InsertDeletePatch"

    /** Triples to delete in a patch. */
    public const val DELETES: String = "${NAMESPACE}deletes"

    /** Triples to insert in a patch. */
    public const val INSERTS: String = "${NAMESPACE}inserts"

    /** Conditions that must hold for the patch to apply. */
    public const val WHERE: String = "${NAMESPACE}where"

    //Access Control (Solid-specific agent classes)
    /** Matches any agent (authenticated or not). Same as foaf:Agent. */
    public const val PUBLIC_AGENT: String = "http://xmlns.com/foaf/0.1/Agent"

    /** Matches only authenticated agents. */
    public const val AUTHENTICATED_AGENT: String = "${NAMESPACE}AuthenticatedAgent"

    // Notifications
    /** Links a storage description to an available notification channel. */
    public const val NOTIFICATION_CHANNEL: String = "${NAMESPACE}notificationChannel"
}
