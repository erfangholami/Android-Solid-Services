package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD

/**
 * RDF representation of a Solid address book root document (`vcard:AddressBook`).
 *
 * This class is the read/write view of the address-book index document stored on the
 * pod. It is typed `vcard:AddressBook` and records the book's owner, display title,
 * and links to the two auxiliary index documents: the people (name-email) index and
 * the groups index.
 *
 * Construct from a freshly-parsed pod response by passing the parsed quads, or create
 * a new empty instance by supplying only the identifier.
 */
public class AddressBookRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier(), VCARD.ADDRESS_BOOK)
    }

    /**
     * Returns the WebID of the address-book owner (`acl:owner`), or `null` when the document
     * does not state one — a book written by another client, or a partial read.
     */
    public fun getOwner(): String? = findProperty(ACL.OWNER)

    /** Sets the address-book owner to [owner] (a WebID IRI). */
    public fun setOwner(owner: String) {
        addQuad(getIdentifier(), ACL.OWNER, owner)
    }

    /** Returns the display title of this address book (`dc:title`), or `null` when untitled. */
    public fun getTitle(): String? =
        findProperty(DC.TITLE) ?: findProperty(DC.TITLE_LEGACY)

    /** Sets the display title of this address book. */
    public fun setTitle(title: String) {
        addQuadLiteral(getIdentifier(), DC.TITLE, title, XSD.STRING)
    }

    /**
     * Returns the URI of the people (name-email) index document (`vcard:nameEmailIndex`), or
     * `null` when the book declares none — read that as "this book lists no contacts".
     */
    public fun getNameEmailIndex(): String? = findProperty(VCARD.NAME_EMAIL_INDEX)

    /** Sets the URI of the people (name-email) index document. */
    public fun setNameEmailIndex(peopleIndex: String) {
        addQuad(getIdentifier(), VCARD.NAME_EMAIL_INDEX, peopleIndex)
    }

    /**
     * Returns the URI of the groups index document (`vcard:groupIndex`), or `null` when the book
     * declares none — read that as "this book has no groups".
     */
    public fun getGroupsIndex(): String? = findProperty(VCARD.GROUP_INDEX)

    /** Sets the URI of the groups index document. */
    public fun setGroupsIndex(groupsIndex: String) {
        addQuad(getIdentifier(), VCARD.GROUP_INDEX, groupsIndex)
    }
}
