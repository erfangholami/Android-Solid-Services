package com.erfangholami.androidsolidservices.api.datamodule

import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * The contract a data-module store implements so its entities can be shared as
 * **data identities** rather than files.
 *
 * A shareable entity lives in its own pod container (a ticket's `{uuid}/` holding document +
 * artifact + images; a contact's `Person/{uuid}/` holding document + photo), so one grant on
 * [shareTarget] covers the complete entity — WAC `acl:default` / ACP `acp:memberAccessControl`
 * make members inherit. The sharing engine itself stays type-open: it threads [entityTypeIri]
 * and a display name as opaque strings (`SharingManager.createShare(resourceType, resourceName)`),
 * while this contract supplies the module-specific knowledge — which resource to grant on, what
 * the entity is called, whether a single public artifact exists, and how a receiver resolves the
 * entity inside a container they were granted.
 *
 * Implementing this interface is step one of making a new data module shareable; a consumer app
 * additionally provides the module's share/receive UI and the receiver-side deep copy through the
 * module's normal write path.
 *
 * @param T the store's full entity read model (e.g. `Ticket`, `SolidContact`).
 */
public interface ShareableEntityStore<T> {

    /**
     * RDF class IRI of the entities this store manages (e.g. `https://schema.org/Ticket`,
     * `http://www.w3.org/2006/vcard/ns#Individual`) — the open-vocabulary discriminator written
     * on typed share records and notifications. Readers render unknown IRIs generically.
     */
    public val entityTypeIri: String

    /**
     * The resource a person-to-person share of the entity at [entityUri] should grant access
     * on — the entity's own container when the layout provides one, so the grant covers the
     * document and its attachments in one authorization. Falls back to the entity document
     * itself for legacy layouts without a per-entity container; never a shared parent container.
     */
    public fun shareTarget(entityUri: String): String

    /**
     * The single resource representing [entity] for an anyone-with-the-link (public) share —
     * e.g. a ticket's original `.pkpass` artifact, which any receiver can consume without a
     * Solid client. Returns `null` when the entity has no such representation or must not be
     * publicly shared (no artifact, issuer prohibits sharing, or the module has no public form).
     */
    public fun publicShareTarget(entity: T): String?

    /** Human title used to label shares and notifications, or `null` when the entity has none. */
    public fun displayName(entity: T): String?

    /**
     * Resolves the entity inside [containerUri] — a per-entity container someone shared — by
     * followed links only: the container's membership is listed and the member document typed
     * [entityTypeIri] is read. Works against a foreign pod the caller has read access to.
     * Fails with `NOT_FOUND` when the container holds no entity of this store's type.
     */
    public suspend fun findInContainer(ownerWebId: String, containerUri: String): SolidResult<T>
}
