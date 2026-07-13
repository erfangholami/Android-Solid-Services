package com.erfangholami.androidsolidservices.shared.model.access

import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.Solid

/**
 * A single WAC authorization rule (`acl:Authorization`).
 *
 * Each authorization grants a set of [modes] to a set of [agents] /
 * [agentClasses] / [agentGroups] on the resources identified by
 * [accessTo] (direct) or inherited by members of containers in [default].
 *
 * Spec: https://solidproject.org/TR/wac
 */
public data class AclAuthorization(
    /** Identifier of this authorization node (blank node or IRI). */
    val subject: String,

    /** IRIs of specific resources this authorization applies to directly. */
    val accessTo: List<String> = emptyList(),

    /**
     * IRIs of containers whose members inherit this authorization
     * (acl:default).
     */
    val default: List<String> = emptyList(),

    /**
     * Access modes granted (e.g. `acl:Read`, `acl:Write`, `acl:Append`,
     * `acl:Control`).
     */
    val modes: Set<String> = emptySet(),

    /** Individual agents granted access, identified by WebID. */
    val agents: List<String> = emptyList(),

    /**
     * Agent classes granted access.
     * Common values: `foaf:Agent` (public), `acl:AuthenticatedAgent`.
     */
    val agentClasses: List<String> = emptyList(),

    /**
     * Group resources (`vcard:Group`) whose members are granted access.
     */
    val agentGroups: List<String> = emptyList(),

    /**
     * HTTP Origins that are permitted for this authorization.
     * Empty means all origins are permitted.
     */
    val origins: List<String> = emptyList(),
) {
    public fun allowsRead(): Boolean = modes.contains(ACL.READ)
    public fun allowsWrite(): Boolean = modes.contains(ACL.WRITE)

    /**
     * `true` if this authorization permits appending. Folds in `acl:Write`
     * for convenience, since a write grant subsumes the ability to append
     * even though WAC asserts the modes independently.
     */
    public fun allowsAppend(): Boolean = modes.contains(ACL.APPEND) || allowsWrite()
    public fun allowsControl(): Boolean = modes.contains(ACL.CONTROL)

    /** Returns `true` if this authorization applies to the public (`foaf:Agent` agentClass). */
    public fun isPublic(): Boolean = agentClasses.any { it.toString() == Solid.PUBLIC_AGENT }
}
