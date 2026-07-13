package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.contacts.Contact
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.OWL
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * RDF representation of a Solid contact group (`vcard:Group`).
 *
 * Wraps the quads of one group document on a pod and exposes typed accessors and
 * mutators. Construct from a pod response by passing the parsed quads, or create a
 * new empty instance by supplying only the identifier.
 *
 * All mutator methods update the in-memory quad list; call the contacts data module
 * to persist the change to the pod.
 */
public class GroupRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier(), VCARD.GROUP)
    }

    /** Returns the group's display name (`vcard:fn`). */
    public fun getTitle(): String =
        quads.find {
            it.subject == getIdentifier() && it.predicate == VCARD.FN
        }!!.`object`

    /** Sets the group's display name. */
    public fun setTitle(title: String) {
        addQuadLiteral(getIdentifier(), VCARD.FN, title, XSD.STRING)
    }

    /**
     * Adds a `vcard:includesGroup` triple linking this group to [addressBookUri],
     * so the group is discoverable from the address-book root.
     */
    public fun setIncludesInAddressBook(addressBookUri: String) {
        addQuad(addressBookUri, VCARD.INCLUDES_GROUP, getIdentifier())
    }

    /**
     * Returns the [Contact] summaries for all members of this group (`vcard:hasMember`).
     *
     * Where an `owl:sameAs` alias is present (used to map a group-local blank-node member
     * reference to the canonical contact URI), the canonical URI is returned.
     */
    public fun getContacts(): List<Contact> {
        val members = quads
            .filter { it.predicate == VCARD.HAS_MEMBER }
            .map { triple ->
                val sameAs = quads.find {
                    it.predicate == OWL.SAME_AS && it.subject == triple.`object`
                }
                sameAs?.`object` ?: triple.`object`
            }

        return members.map { memberUri ->
            val name = quads.find {
                it.subject == memberUri && it.predicate == VCARD.FN
            }!!.`object`
            Contact(memberUri, name)
        }
    }

    /**
     * Adds [contact] as a member of this group (`vcard:hasMember`), also recording
     * the contact's display name inline so group listings can be rendered without an
     * extra pod request.
     */
    public fun addMember(contact: ContactRDF) {
        addQuad(
            getIdentifier(),
            VCARD.HAS_MEMBER,
            contact.getIdentifier(),
            maxNumber = Int.MAX_VALUE
        )
        addQuadLiteral(contact.getIdentifier(), VCARD.FN, contact.getFullName(), XSD.STRING)
    }

    /**
     * Rewrites the cached member `vcard:fn` for [contactUri] to [newName].
     *
     * Membership is matched directly or through an `owl:sameAs` alias (the same
     * aliasing [getContacts] resolves).
     *
     * @return `true` if [contactUri] is a member of this group and its cached name
     *   was updated, `false` if it is not a member.
     */
    public fun updateMemberName(contactUri: String, newName: String): Boolean {
        val member = quads.any {
            it.subject == getIdentifier() &&
                    it.predicate == VCARD.HAS_MEMBER &&
                    it.`object` == contactUri
        } || quads.any { it.predicate == OWL.SAME_AS && it.`object` == contactUri }
        if (!member) return false
        addQuadLiteral(contactUri, VCARD.FN, newName, XSD.STRING)
        return true
    }

    /**
     * Removes the membership entry for [contactURI] from this group's quad list.
     *
     * @return `true` if the member was found and removed, `false` if not present.
     */
    public fun removeMember(contactURI: String): Boolean {
        val contactStr = contactURI
        val member = quads.find {
            it.subject == getIdentifier() &&
                    it.predicate == VCARD.HAS_MEMBER &&
                    it.`object` == contactStr
        } ?: return false

        val memberNameQuads = quads.filter {
            it.subject == contactStr && it.predicate == VCARD.FN
        }
        quads.remove(member)
        quads.removeAll(memberNameQuads)
        return true
    }
}
