package com.erfangholami.androidsolidservices.shared.model.typeindex
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import java.util.UUID

/**
 * Represents a Solid Type Index document (public or private).
 *
 * A type index maps RDF classes to specific resource instances or containers
 * in a pod. It contains `solid:TypeRegistration` entries, each with a
 * `solid:forClass` predicate and either `solid:instance` or
 * `solid:instanceContainer`.
 *
 * Spec: https://solid.github.io/webid-profile/ — Type Indexes
 */
public abstract class SettingTypeIndex : SolidRDFResource {

    protected val typeKey: String = RDF.TYPE
    protected val typeRegistration: String = Solid.TYPE_REGISTRATION
    protected val forClassKey: String = Solid.FOR_CLASS
    protected val instanceKey: String = Solid.INSTANCE
    protected val instanceContainerKey: String = Solid.INSTANCE_CONTAINER
    protected val typeIndex: String = Solid.TYPE_INDEX
    protected val unlistedDocument: String = Solid.UNLISTED_DOCUMENT
    protected val listedDocument: String = Solid.LISTED_DOCUMENT
    protected val addressBook: String = VCARD.ADDRESS_BOOK

    public constructor(
        identifier: String,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers)

    init {
        setTypes()
    }

    internal abstract fun setTypes()

    /**
     * Returns all `solid:instance` URIs registered for the given [forClass] IRI.
     */
    public fun getInstances(forClass: String): List<String> =
        quads
            .filter { it.predicate == Solid.FOR_CLASS && it.`object` == forClass }
            .mapNotNull { registration ->
                quads.find { it.subject == registration.subject && it.predicate == Solid.INSTANCE }?.`object`
            }

    /**
     * Returns all `solid:instanceContainer` URIs registered for the given [forClass] IRI.
     */
    public fun getInstanceContainers(forClass: String): List<String> =
        quads
            .filter { it.predicate == Solid.FOR_CLASS && it.`object` == forClass }
            .mapNotNull { registration ->
                quads.find { it.subject == registration.subject && it.predicate == Solid.INSTANCE_CONTAINER }?.`object`
            }

    /**
     * Registers a `solid:instance` entry for [forClass] pointing to [instanceUri].
     */
    public fun addInstance(forClass: String, instanceUri: String) {
        val subject = "${getIdentifier()}#${UUID.randomUUID()}"
        addQuad(subject, RDF.TYPE, Solid.TYPE_REGISTRATION, maxNumber = Int.MAX_VALUE)
        addQuad(subject, Solid.FOR_CLASS, forClass, maxNumber = Int.MAX_VALUE)
        addQuad(subject, Solid.INSTANCE, instanceUri, maxNumber = Int.MAX_VALUE)
    }

    /**
     * Registers a `solid:instanceContainer` entry for [forClass] pointing to
     * [containerUri].
     */
    public fun addInstanceContainer(forClass: String, containerUri: String) {
        val subject = "${getIdentifier()}#${UUID.randomUUID()}"
        addQuad(subject, RDF.TYPE, Solid.TYPE_REGISTRATION, maxNumber = Int.MAX_VALUE)
        addQuad(subject, Solid.FOR_CLASS, forClass, maxNumber = Int.MAX_VALUE)
        addQuad(subject, Solid.INSTANCE_CONTAINER, containerUri, maxNumber = Int.MAX_VALUE)
    }

    /**
     * Returns `true` if [resourceUri] is registered (as either instance or
     * instanceContainer) for any class.
     */
    public fun containsResource(resourceUri: String): Boolean =
        quads.any { it.`object` == resourceUri }

    /**
     * Removes the registration entry that points to [resourceUri] (removes
     * the entire registration node including forClass and type triples).
     */
    public fun removeResource(resourceUri: String) {
        val anchor = quads.find { it.`object` == resourceUri } ?: return
        quads.removeAll { it.subject == anchor.subject || it.`object` == anchor.subject }
    }

    /** Returns all `solid:instance` URIs registered for `vcard:AddressBook`. */
    public fun getAddressBooks(): List<String> = getInstances(VCARD.ADDRESS_BOOK)

    /** Registers [addressBook] as a `solid:instance` for `vcard:AddressBook`. */
    public fun addAddressBook(addressBook: String): Unit = addInstance(VCARD.ADDRESS_BOOK, addressBook)

    /** Returns `true` if [addressBookUri] is registered in this type index. */
    public fun containsAddressBook(addressBookUri: String): Boolean = containsResource(addressBookUri)

    /** Removes the registration entry for [addressBookUri] from this type index. */
    public fun removeAddressBook(addressBookUri: String): Unit = removeResource(addressBookUri)
}
