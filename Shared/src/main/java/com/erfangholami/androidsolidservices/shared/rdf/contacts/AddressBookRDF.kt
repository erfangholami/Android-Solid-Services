package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

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
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier().toString(), VCARD.ADDRESS_BOOK)
    }

    /** Returns the WebID of the address-book owner (`acl:owner`). */
    public fun getOwner(): String =
        quads.find { it.predicate == ACL.OWNER }!!.`object`

    /** Sets the address-book owner to [owner] (a WebID IRI). */
    public fun setOwner(owner: String) {
        addQuad(getIdentifier().toString(), ACL.OWNER, owner)
    }

    /** Returns the display title of this address book (`dc:title`). */
    public fun getTitle(): String =
        quads.find { it.predicate == DC.TITLE || it.predicate == DC.TITLE_LEGACY }!!.`object`

    /** Sets the display title of this address book. */
    public fun setTitle(title: String) {
        addQuadLiteral(getIdentifier().toString(), DC.TITLE, title, XSD.STRING)
    }

    /** Returns the URI of the people (name-email) index document (`vcard:nameEmailIndex`). */
    public fun getNameEmailIndex(): String =
        quads.find { it.predicate == VCARD.NAME_EMAIL_INDEX }!!.`object`

    /** Sets the URI of the people (name-email) index document. */
    public fun setNameEmailIndex(peopleIndex: String) {
        addQuad(getIdentifier().toString(), VCARD.NAME_EMAIL_INDEX, peopleIndex)
    }

    /** Returns the URI of the groups index document (`vcard:groupIndex`). */
    public fun getGroupsIndex(): String =
        quads.find { it.predicate == VCARD.GROUP_INDEX }!!.`object`

    /** Sets the URI of the groups index document. */
    public fun setGroupsIndex(groupsIndex: String) {
        addQuad(getIdentifier().toString(), VCARD.GROUP_INDEX, groupsIndex)
    }
}
