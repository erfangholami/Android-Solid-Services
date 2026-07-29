package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Web Access Control (WAC) vocabulary constants.
 * http://www.w3.org/ns/auth/acl#
 * Spec: https://solidproject.org/TR/wac
 */
public object ACL {
    public const val NAMESPACE: String = "http://www.w3.org/ns/auth/acl#"

    public const val AUTHORIZATION: String = "${NAMESPACE}Authorization"

    public const val READ: String = "${NAMESPACE}Read"
    public const val WRITE: String = "${NAMESPACE}Write"
    public const val APPEND: String = "${NAMESPACE}Append"

    /** Allows reading and modifying the ACL resource itself. */
    public const val CONTROL: String = "${NAMESPACE}Control"

    /** Links an Authorization to a specific resource it protects. */
    public const val ACCESS_TO: String = "${NAMESPACE}accessTo"

    /** Links an Authorization to a container; inherited by all members. */
    public const val DEFAULT: String = "${NAMESPACE}default"

    /** Access mode(s) granted by this Authorization. */
    public const val MODE: String = "${NAMESPACE}mode"

    /** Individual agent (by WebID) granted access. */
    public const val AGENT: String = "${NAMESPACE}agent"

    /** Class of agents granted access (e.g. foaf:Agent, acl:AuthenticatedAgent). */
    public const val AGENT_CLASS: String = "${NAMESPACE}agentClass"

    /** Group resource (vcard:Group) whose members are granted access. */
    public const val AGENT_GROUP: String = "${NAMESPACE}agentGroup"

    /** HTTP Origin that is allowed to make requests. */
    public const val ORIGIN: String = "${NAMESPACE}origin"

    /** Any agent, authenticated or not. */
    public const val AGENT_PUBLIC: String = "http://xmlns.com/foaf/0.1/Agent"

    /** Any authenticated agent. */
    public const val AUTHENTICATED_AGENT: String = "${NAMESPACE}AuthenticatedAgent"

    /** Links a storage to its owner. */
    public const val OWNER: String = "${NAMESPACE}owner"

    /** Trusted application with allowed origins and modes. */
    public const val TRUSTED_APP: String = "${NAMESPACE}trustedApp"

    /** Access modes allowed for a trusted application. */
    public const val TRUSTED_MODE: String = "${NAMESPACE}trustedMode"
}
