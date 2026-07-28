package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Solid Application Interoperability (SAI) vocabulary constants.
 * http://www.w3.org/ns/solid/interop#
 * Spec: https://solidproject.org/TR/sai
 *
 * SAI defines how applications request and receive delegated access to
 * data across Solid pods via registries, access needs, and access grants.
 */
public object SAI {
    public const val NAMESPACE: String = "http://www.w3.org/ns/solid/interop#"

    /** An individual, group, or organization identifiable by a WebID. */
    public const val SOCIAL_AGENT: String = "${NAMESPACE}SocialAgent"

    /** A software-based agent (app) used to access and manage pod data. */
    public const val APPLICATION: String = "${NAMESPACE}Application"

    /** The authorization agent application managing data access on behalf of an agent. */
    public const val AUTHORIZATION_AGENT: String = "${NAMESPACE}AuthorizationAgent"

    /** Links to all registries owned by a social agent. */
    public const val REGISTRY_SET: String = "${NAMESPACE}RegistrySet"

    /** A collection of agent registrations (known apps and social agents). */
    public const val AGENT_REGISTRY: String = "${NAMESPACE}AgentRegistry"

    /** A collection of data registrations for a specific shape tree. */
    public const val DATA_REGISTRY: String = "${NAMESPACE}DataRegistry"

    /** Stores data instances for a specific shape tree within a data registry. */
    public const val DATA_REGISTRATION: String = "${NAMESPACE}DataRegistration"

    /** Records metadata about an application used by a social agent. */
    public const val APPLICATION_REGISTRATION: String = "${NAMESPACE}ApplicationRegistration"

    /** Records another social agent that has been interacted with. */
    public const val SOCIAL_AGENT_REGISTRATION: String = "${NAMESPACE}SocialAgentRegistration"

    /** A secure way to initiate a data-sharing relationship. */
    public const val SOCIAL_AGENT_INVITATION: String = "${NAMESPACE}SocialAgentInvitation"

    /** A group of related access needs communicated together. */
    public const val ACCESS_NEED_GROUP: String = "${NAMESPACE}AccessNeedGroup"

    /** A requirement for access to one specific data type / shape tree. */
    public const val ACCESS_NEED: String = "${NAMESPACE}AccessNeed"

    /** Sent from one agent to another to communicate access need groups. */
    public const val ACCESS_REQUEST: String = "${NAMESPACE}AccessRequest"

    /** Records the decision to grant access to data for an agent. */
    public const val ACCESS_AUTHORIZATION: String = "${NAMESPACE}AccessAuthorization"

    /** Authorizes access to a specific data type within an access authorization. */
    public const val DATA_AUTHORIZATION: String = "${NAMESPACE}DataAuthorization"

    /** Describes the access that has been granted to an agent. */
    public const val ACCESS_GRANT: String = "${NAMESPACE}AccessGrant"

    /** Detailed access description for a specific data type within an access grant. */
    public const val DATA_GRANT: String = "${NAMESPACE}DataGrant"

    /** A data grant that re-shares access from a grantee to another agent. */
    public const val DELEGATED_DATA_GRANT: String = "${NAMESPACE}DelegatedDataGrant"

    /** Links a social agent to their registry set. */
    public const val HAS_REGISTRY_SET: String = "${NAMESPACE}hasRegistrySet"

    /** Links a social agent to their authorization agent application. */
    public const val HAS_AUTHORIZATION_AGENT: String = "${NAMESPACE}hasAuthorizationAgent"

    /** Links a registry set to an agent registry. */
    public const val HAS_AGENT_REGISTRY: String = "${NAMESPACE}hasAgentRegistry"

    /** Links a registry set to a data registry. */
    public const val HAS_DATA_REGISTRY: String = "${NAMESPACE}hasDataRegistry"

    /** Links an agent registry to a social agent registration. */
    public const val HAS_SOCIAL_AGENT_REGISTRATION: String = "${NAMESPACE}hasSocialAgentRegistration"

    /** Links an agent registry to an application registration. */
    public const val HAS_APPLICATION_REGISTRATION: String = "${NAMESPACE}hasApplicationRegistration"

    /** Links a data registry to a data registration. */
    public const val HAS_DATA_REGISTRATION: String = "${NAMESPACE}hasDataRegistration"

    /** Links a data registration to a specific data instance. */
    public const val HAS_DATA_INSTANCE: String = "${NAMESPACE}hasDataInstance"

    /** The social agent that created this registration. */
    public const val REGISTERED_BY: String = "${NAMESPACE}registeredBy"

    /** The application used to create this registration. */
    public const val REGISTERED_WITH: String = "${NAMESPACE}registeredWith"

    /** Timestamp when the registration was created (xsd:dateTime). */
    public const val REGISTERED_AT: String = "${NAMESPACE}registeredAt"

    /** Timestamp when the registration was last updated (xsd:dateTime). */
    public const val UPDATED_AT: String = "${NAMESPACE}updatedAt"

    /** The agent (app or social agent) that was registered. */
    public const val REGISTERED_AGENT: String = "${NAMESPACE}registeredAgent"

    /** The shape tree that describes the registered data type. */
    public const val REGISTERED_SHAPE_TREE: String = "${NAMESPACE}registeredShapeTree"

    /** Human-readable name of the application. */
    public const val APPLICATION_NAME: String = "${NAMESPACE}applicationName"

    /** Human-readable description of the application's function. */
    public const val APPLICATION_DESCRIPTION: String = "${NAMESPACE}applicationDescription"

    /** The social agent who authored this application. */
    public const val APPLICATION_AUTHOR: String = "${NAMESPACE}applicationAuthor"

    /** URI of a thumbnail image for the application. */
    public const val APPLICATION_THUMBNAIL: String = "${NAMESPACE}applicationThumbnail"

    /** URI the authorization agent redirects to after authorization. */
    public const val HAS_AUTHORIZATION_CALLBACK_ENDPOINT: String = "${NAMESPACE}hasAuthorizationCallbackEndpoint"

    /** URI the authorization agent uses for authorization redirects. */
    public const val HAS_AUTHORIZATION_REDIRECT_ENDPOINT: String = "${NAMESPACE}hasAuthorizationRedirectEndpoint"

    /** Links an access need group to its constituent access needs. */
    public const val HAS_ACCESS_NEED: String = "${NAMESPACE}hasAccessNeed"

    /** Links an authorization or grant to its access need groups. */
    public const val HAS_ACCESS_NEED_GROUP: String = "${NAMESPACE}hasAccessNeedGroup"

    /** Marks an access need as required (`interop:Required`) or optional (`interop:Optional`). */
    public const val ACCESS_NECESSITY: String = "${NAMESPACE}accessNecessity"

    /** Context for presenting the access need (`interop:PersonalAccess`, `interop:SharedAccess`). */
    public const val ACCESS_SCENARIO: String = "${NAMESPACE}accessScenario"

    /** The requested access modes (e.g. `acl:Read`, `acl:Write`). */
    public const val ACCESS_MODE: String = "${NAMESPACE}accessMode"

    /** Additional access modes granted to the data creator. */
    public const val CREATOR_ACCESS_MODE: String = "${NAMESPACE}creatorAccessMode"

    /** References a parent access need this need inherits from. */
    public const val INHERITS_FROM_NEED: String = "${NAMESPACE}inheritsFromNeed"

    /** Links a social agent registration or access grant to an access grant. */
    public const val HAS_ACCESS_GRANT: String = "${NAMESPACE}hasAccessGrant"

    /** Links an access grant to one of its constituent data grants. */
    public const val HAS_DATA_GRANT: String = "${NAMESPACE}hasDataGrant"

    /** The social agent who granted the authorization. */
    public const val GRANTED_BY: String = "${NAMESPACE}grantedBy"

    /** The application used to perform the authorization. */
    public const val GRANTED_WITH: String = "${NAMESPACE}grantedWith"

    /** Timestamp when the authorization was granted (xsd:dateTime). */
    public const val GRANTED_AT: String = "${NAMESPACE}grantedAt"

    /** The agent receiving the authorization or grant. */
    public const val GRANTEE: String = "${NAMESPACE}grantee"

    /** The social agent who owns the authorized data. */
    public const val DATA_OWNER: String = "${NAMESPACE}dataOwner"

    /** Scope of the authorization (`All`, `AllFromAgent`, `AllFromRegistry`, `SelectedFromRegistry`, `Inherited`). */
    public const val SCOPE_OF_AUTHORIZATION: String = "${NAMESPACE}scopeOfAuthorization"

    /** Scope of the grant (mirrors scopeOfAuthorization for the issued grant). */
    public const val SCOPE_OF_GRANT: String = "${NAMESPACE}scopeOfGrant"

    /** Links a data grant to the access need it satisfies. */
    public const val SATISFIES_ACCESS_NEED: String = "${NAMESPACE}satisfiesAccessNeed"

    /** References a parent authorization this authorization inherits from. */
    public const val INHERITS_FROM_AUTHORIZATION: String = "${NAMESPACE}inheritsFromAuthorization"

    /** References a parent data grant this grant inherits from. */
    public const val INHERITS_FROM_GRANT: String = "${NAMESPACE}inheritsFromGrant"

    /** The data grant this delegated grant re-shares. */
    public const val DELEGATION_OF_GRANT: String = "${NAMESPACE}delegationOfGrant"

    /** References the mutual registration in the other agent's registry. */
    public const val RECIPROCAL_REGISTRATION: String = "${NAMESPACE}reciprocalRegistration"

    /** A secure URL for accepting a social agent invitation. */
    public const val CAPABILITY_URL: String = "${NAMESPACE}capabilityUrl"

    /** Marks an access need as required. */
    public const val REQUIRED: String = "${NAMESPACE}Required"

    /** Marks an access need as optional. */
    public const val OPTIONAL: String = "${NAMESPACE}Optional"

    /** Access for the agent's own personal data. */
    public const val PERSONAL_ACCESS: String = "${NAMESPACE}PersonalAccess"

    /** Access shared with or from other agents. */
    public const val SHARED_ACCESS: String = "${NAMESPACE}SharedAccess"
}
