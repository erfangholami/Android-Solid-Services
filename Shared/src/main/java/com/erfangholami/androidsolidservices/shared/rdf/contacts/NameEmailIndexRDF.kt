package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.contacts.Contact
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF representation of the people (name-email) index document for a Solid address book.
 *
 * The name-email index lists all `vcard:Individual` contact entries belonging to an
 * address book, using `vcard:inAddressBook` triples. It is separate from the address-book
 * root so that the contact list can be loaded and updated independently.
 *
 * Construct from a pod response by passing the parsed quads, or create a new empty
 * instance by supplying only the identifier.
 */
public class NameEmailIndexRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /**
     * Returns lightweight [Contact] summaries for all contacts belonging to [addressBookUri].
     *
     * Contacts whose `vcard:fn` is missing from this index are silently skipped.
     */
    public fun getContacts(addressBookUri: String): List<Contact> =
        quads
            .filter { it.predicate == VCARD.IN_ADDRESS_BOOK && it.subject == addressBookUri }
            .mapNotNull { triple ->
                val contactName = quads.find {
                    it.subject == triple.`object` && it.predicate == VCARD.FN
                }?.`object` ?: return@mapNotNull null
                Contact(triple.`object`, contactName)
            }

    /**
     * Adds [contact] to this index under [addressBookUri], recording the contact's type and
     * display name inline so the index can be rendered without fetching each contact document.
     */
    public fun addContact(addressBookUri: String, contact: ContactRDF) {
        val contactUri = contact.getIdentifier().toString()
        addQuad(addressBookUri, VCARD.IN_ADDRESS_BOOK, contactUri, maxNumber = Int.MAX_VALUE)
        addQuad(contactUri, RDF.TYPE, VCARD.INDIVIDUAL)
        addQuadLiteral(contactUri, VCARD.FN, contact.getFullName(), XSD.STRING)
    }

    /**
     * Rewrites the cached `vcard:fn` for [contactUri] to [newName].
     *
     * @return `true` if the contact is listed in this index and its cached name was
     *   updated, `false` if the contact was not found.
     */
    public fun updateContactName(contactUri: String, newName: String): Boolean {
        val listed = quads.any {
            it.predicate == VCARD.IN_ADDRESS_BOOK && it.`object` == contactUri
        }
        if (!listed) return false
        addQuadLiteral(contactUri, VCARD.FN, newName, XSD.STRING)
        return true
    }

    /**
     * Removes all index entries for [contactUri] (both the `vcard:inAddressBook` link and
     * the contact's own cached triples).
     *
     * @return `true` if any entries were removed, `false` if the contact was not found.
     */
    public fun removeContact(contactUri: String): Boolean {
        val affected = quads.filter {
            (it.predicate == VCARD.IN_ADDRESS_BOOK && it.`object` == contactUri) ||
                    it.subject == contactUri
        }
        if (affected.isEmpty()) return false
        quads.removeAll { it.subject == contactUri || it.`object` == contactUri }
        return true
    }
}
