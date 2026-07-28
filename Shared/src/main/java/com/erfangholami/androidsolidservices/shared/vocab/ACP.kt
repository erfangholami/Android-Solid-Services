package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Access Control Policy (ACP) vocabulary constants.
 * http://www.w3.org/ns/solid/acp#
 * Spec: https://solidproject.org/TR/acp
 */
public object ACP {
    public const val NAMESPACE: String = "http://www.w3.org/ns/solid/acp#"

    /** Connects a resource to its access controls. Linked via Link: rel="acl". */
    public const val ACCESS_CONTROL_RESOURCE: String = "${NAMESPACE}AccessControlResource"

    /** Connects an AccessControlResource to Policies. */
    public const val ACCESS_CONTROL_TYPE: String = "${NAMESPACE}AccessControl"

    /** Defines allowed/denied access modes and matcher conditions. */
    public const val POLICY: String = "${NAMESPACE}Policy"

    /** Describes matching context attributes for a policy. */
    public const val MATCHER: String = "${NAMESPACE}Matcher"

    /** Extensible base class for access modes. */
    public const val ACCESS_MODE: String = "${NAMESPACE}AccessMode"

    /** Describes a resource access instance (target, agent, client, issuer). */
    public const val CONTEXT_TYPE: String = "${NAMESPACE}Context"

    /** Defines access modes granted in a given context. */
    public const val ACCESS_GRANT: String = "${NAMESPACE}AccessGrant"

    /** Connects an ACR to the resource it controls. */
    public const val RESOURCE: String = "${NAMESPACE}resource"

    /** Connects an ACR to access controls that apply directly to the resource. */
    public const val ACCESS_CONTROL: String = "${NAMESPACE}accessControl"

    /** Connects an ACR to access controls that apply transitively to member resources. */
    public const val MEMBER_ACCESS_CONTROL: String = "${NAMESPACE}memberAccessControl"

    /** References policies applied by this access control. */
    public const val APPLY: String = "${NAMESPACE}apply"

    /** Access modes granted when the policy is satisfied. */
    public const val ALLOW: String = "${NAMESPACE}allow"

    /** Access modes denied when the policy is satisfied. */
    public const val DENY: String = "${NAMESPACE}deny"

    /** All referenced matchers must be satisfied. */
    public const val ALL_OF: String = "${NAMESPACE}allOf"

    /** At least one referenced matcher must be satisfied. */
    public const val ANY_OF: String = "${NAMESPACE}anyOf"

    /** None of the referenced matchers must be satisfied. */
    public const val NONE_OF: String = "${NAMESPACE}noneOf"

    public const val AGENT: String = "${NAMESPACE}agent"
    public const val CLIENT: String = "${NAMESPACE}client"
    public const val ISSUER: String = "${NAMESPACE}issuer"
    public const val VC: String = "${NAMESPACE}vc"
    public const val TARGET: String = "${NAMESPACE}target"
    public const val MODE: String = "${NAMESPACE}mode"
    public const val CREATOR: String = "${NAMESPACE}creator"
    public const val OWNER: String = "${NAMESPACE}owner"

    /** Base property for custom context attributes. */
    public const val ATTRIBUTE: String = "${NAMESPACE}attribute"

    /** Access modes granted in this context. */
    public const val GRANT: String = "${NAMESPACE}grant"

    /** Context associated with this access grant. */
    public const val CONTEXT: String = "${NAMESPACE}context"

    /** Matches all agents (authenticated and public). */
    public const val PUBLIC_AGENT: String = "${NAMESPACE}PublicAgent"

    /** Matches only authenticated agents. */
    public const val AUTHENTICATED_AGENT: String = "${NAMESPACE}AuthenticatedAgent"

    /** Matches when the requesting agent is the resource creator. */
    public const val CREATOR_AGENT: String = "${NAMESPACE}CreatorAgent"

    /** Matches when the requesting agent is the storage owner. */
    public const val OWNER_AGENT: String = "${NAMESPACE}OwnerAgent"

    /** Matches all client applications. */
    public const val PUBLIC_CLIENT: String = "${NAMESPACE}PublicClient"

    /** Matches all issuers. */
    public const val PUBLIC_ISSUER: String = "${NAMESPACE}PublicIssuer"

    /** Matcher that is always satisfied. */
    public const val ALWAYS_SATISFIED_RESTRICTION: String = "${NAMESPACE}AlwaysSatisfiedRestriction"
}
