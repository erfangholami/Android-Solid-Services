package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.contacts.Email
import com.erfangholami.androidsolidservices.shared.model.contacts.Name
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneNumber
import com.erfangholami.androidsolidservices.shared.model.contacts.URLType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF representation of a single Solid contact (`vcard:Individual`).
 *
 * Wraps the quads of one contact document on a pod and exposes typed accessors
 * and mutators. Construct from a pod response by passing the parsed quads, or
 * create a new empty instance by supplying only the identifier.
 *
 * All mutator methods update the in-memory quad list; call the contacts data
 * module to persist the change to the pod.
 */
public class ContactRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        addQuad(getIdentifier().toString(), RDF.TYPE, VCARD.INDIVIDUAL)
    }

    /** Returns the contact's formatted display name (`vcard:fn`). */
    public fun getFullName(): String =
        quads.find {
            it.subject == getIdentifier().toString() && it.predicate == VCARD.FN
        }?.`object`
            ?: error("Contact ${getIdentifier()} is missing a vcard:fn (formatted name)")

    /** Sets the contact's formatted display name. */
    public fun setFullName(name: String) {
        addQuadLiteral(getIdentifier().toString(), VCARD.FN, name, XSD.STRING)
    }

    /**
     * Returns the URL of the contact's photo (`vcard:hasPhoto`), or `null` if absent.
     */
    public fun getPhotoUrl(): String? =
        quads.find {
            it.subject == getIdentifier().toString() && it.predicate == VCARD.HAS_PHOTO
        }?.`object`

    /**
     * Returns all URLs associated with this contact (`vcard:url`), each paired with its
     * [URLType] classification. URLs without a recognized type default to [URLType.Home].
     */
    public fun getUrls(): List<Pair<URLType, String>> {
        val selfUri = getIdentifier().toString()
        return quads
            .filter { it.subject == selfUri && it.predicate == VCARD.URL }
            .mapNotNull { urlTriple ->
                val urlNode = urlTriple.`object`
                val type: URLType = when (
                    quads.find { it.subject == urlNode && it.predicate == RDF.TYPE }?.`object`
                ) {
                    VCARD.HOME -> URLType.Home
                    VCARD.WORK -> URLType.Work
                    VCARD.HOMEPAGE -> URLType.Homepage
                    VCARD.WEB_ID -> URLType.WebId
                    VCARD.PUBLIC_ID -> URLType.PublicId
                    else -> URLType.Home
                }
                val url = quads.find { it.subject == urlNode && it.predicate == VCARD.VALUE }
                    ?.`object` ?: return@mapNotNull null
                Pair(type, url)
            }
    }

    /**
     * Returns the contact's structured name (`vcard:hasName`) as a [Name], or `null`
     * if the contact has no structured-name node.
     */
    public fun getName(): Name? {
        val hasNameQuad = quads.find {
            it.subject == getIdentifier().toString() && it.predicate == VCARD.HAS_NAME
        } ?: return null
        val nameNode = hasNameQuad.`object`
        return Name(
            quads.find { it.subject == nameNode && it.predicate == VCARD.FAMILY_NAME }?.`object`,
            quads.find { it.subject == nameNode && it.predicate == VCARD.GIVEN_NAME }?.`object`,
            quads.find { it.subject == nameNode && it.predicate == VCARD.ADDITIONAL_NAME }?.`object`,
            quads.find { it.subject == nameNode && it.predicate == VCARD.HONORIFIC_PREFIX }?.`object`,
            quads.find { it.subject == nameNode && it.predicate == VCARD.HONORIFIC_SUFFIX }?.`object`,
        )
    }

    /** Returns all phone numbers (`vcard:hasTelephone`) stored for this contact. */
    public fun getPhoneNumbers(): List<PhoneNumber> =
        quads
            .filter { it.subject == getIdentifier().toString() && it.predicate == VCARD.HAS_TELEPHONE }
            .mapNotNull { triple ->
                quads.find { it.subject == triple.`object` && it.predicate == VCARD.VALUE }
                    ?.let { PhoneNumber(it.`object`) }
            }

    /**
     * Adds [newPhoneNumber] to this contact's quad list as a `vcard:hasTelephone` entry.
     *
     * The value is stored with a `tel:` URI prefix. Does nothing and returns `false` if
     * [newPhoneNumber] is null, empty, or already present.
     *
     * @return `true` if the phone number was added, `false` if it was a no-op.
     */
    public fun addPhoneNumber(newPhoneNumber: String?): Boolean {
        if (newPhoneNumber.isNullOrEmpty()) return false
        val telValue = "tel:$newPhoneNumber"
        if (quads.any { it.predicate == VCARD.VALUE && it.`object` == telValue }) return false
        val blankNode = "_:$newPhoneNumber"
        addQuad(
            getIdentifier().toString(),
            VCARD.HAS_TELEPHONE,
            blankNode,
            maxNumber = Int.MAX_VALUE
        )
        addQuadLiteral(blankNode, VCARD.VALUE, telValue, null)
        return true
    }

    /** Returns all email addresses (`vcard:hasEmail`) stored for this contact. */
    public fun getEmails(): List<Email> =
        quads
            .filter { it.subject == getIdentifier().toString() && it.predicate == VCARD.HAS_EMAIL }
            .mapNotNull { triple ->
                quads.find { it.subject == triple.`object` && it.predicate == VCARD.VALUE }
                    ?.let { Email(it.`object`) }
            }

    /**
     * Adds [newEmailAddress] to this contact's quad list as a `vcard:hasEmail` entry.
     *
     * The value is stored with a `mailto:` URI prefix. Does nothing and returns `false` if
     * [newEmailAddress] is null, empty, or already present.
     *
     * @return `true` if the email address was added, `false` if it was a no-op.
     */
    public fun addEmailAddress(newEmailAddress: String?): Boolean {
        if (newEmailAddress.isNullOrEmpty()) return false
        val mailtoValue = "mailto:$newEmailAddress"
        if (quads.any { it.predicate == VCARD.VALUE && it.`object` == mailtoValue }) return false
        val blankNode = "_:$newEmailAddress"
        addQuad(getIdentifier().toString(), VCARD.HAS_EMAIL, blankNode, maxNumber = Int.MAX_VALUE)
        addQuadLiteral(blankNode, VCARD.VALUE, mailtoValue, null)
        return true
    }

    /**
     * Removes the `vcard:hasTelephone` entry for [phoneNumber] from this contact's quad list.
     *
     * @return `true` if the entry was found and removed, `false` if it was not present.
     */
    public fun removePhoneNumber(phoneNumber: String): Boolean {
        val telValue = "tel:$phoneNumber"
        val valueQuad = quads.find { it.predicate == VCARD.VALUE && it.`object` == telValue }
            ?: return false
        val hasTelQuad = quads.find {
            it.`object` == valueQuad.subject && it.predicate == VCARD.HAS_TELEPHONE
        }
        quads.remove(valueQuad)
        if (hasTelQuad != null) quads.remove(hasTelQuad)
        return true
    }

    /**
     * Removes the `vcard:hasEmail` entry for [emailAddress] from this contact's quad list.
     *
     * @return `true` if the entry was found and removed, `false` if it was not present.
     */
    public fun removeEmailAddress(emailAddress: String): Boolean {
        val mailtoValue = "mailto:$emailAddress"
        val valueQuad = quads.find { it.predicate == VCARD.VALUE && it.`object` == mailtoValue }
            ?: return false
        val hasEmailQuad = quads.find {
            it.`object` == valueQuad.subject && it.predicate == VCARD.HAS_EMAIL
        }
        quads.remove(valueQuad)
        if (hasEmailQuad != null) quads.remove(hasEmailQuad)
        return true
    }
}
