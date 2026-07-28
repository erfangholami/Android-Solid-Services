package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressType
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailType
import com.erfangholami.androidsolidservices.shared.model.contacts.Gender
import com.erfangholami.androidsolidservices.shared.model.contacts.ImEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.ImType
import com.erfangholami.androidsolidservices.shared.model.contacts.Name
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType
import com.erfangholami.androidsolidservices.shared.model.contacts.URLType
import com.erfangholami.androidsolidservices.shared.model.contacts.UrlEntry
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

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
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier(), VCARD.INDIVIDUAL)
    }

    /** Returns the contact's formatted display name (`vcard:fn`). */
    public fun getFullName(): String =
        quads.find {
            it.subject == getIdentifier() && it.predicate == VCARD.FN
        }?.`object`
            ?: error("Contact ${getIdentifier()} is missing a vcard:fn (formatted name)")

    /** Sets the contact's formatted display name. */
    public fun setFullName(name: String) {
        addQuadLiteral(getIdentifier(), VCARD.FN, name, XSD.STRING)
    }

    /**
     * Returns the URL of the contact's photo (`vcard:hasPhoto`), or `null` if absent.
     */
    public fun getPhotoUrl(): String? =
        quads.find {
            it.subject == getIdentifier() && it.predicate == VCARD.HAS_PHOTO
        }?.`object`

    /**
     * Returns all URLs associated with this contact (`vcard:url`), each paired with its
     * [URLType] classification. URLs without a recognized type default to [URLType.Home].
     */
    public fun getUrls(): List<Pair<URLType, String>> {
        val selfUri = getIdentifier()
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
            it.subject == getIdentifier() && it.predicate == VCARD.HAS_NAME
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

    /**
     * Sets (or clears) the contact's structured name (`vcard:hasName`).
     *
     * The name parts are written to the fragment node `{document}#name`; passing `null`
     * or a [Name] with no non-null components removes the node and its link entirely.
     * Read back with [getName].
     */
    public fun setName(name: Name?) {
        val selfUri = getIdentifier()
        quads.find { it.subject == selfUri && it.predicate == VCARD.HAS_NAME }?.let { link ->
            quads.removeAll { it.subject == link.`object` }
        }
        clearProperties(VCARD.HAS_NAME, selfUri)
        if (name == null) return
        val parts = listOfNotNull(
            name.familyName?.let { VCARD.FAMILY_NAME to it },
            name.givenName?.let { VCARD.GIVEN_NAME to it },
            name.additionalName?.let { VCARD.ADDITIONAL_NAME to it },
            name.honorificPrefix?.let { VCARD.HONORIFIC_PREFIX to it },
            name.honorificSuffix?.let { VCARD.HONORIFIC_SUFFIX to it },
        )
        if (parts.isEmpty()) return
        val nameNode = "${selfUri.substringBefore('#')}#name"
        addQuad(selfUri, VCARD.HAS_NAME, nameNode)
        parts.forEach { (predicate, value) ->
            addQuadLiteral(nameNode, predicate, value, XSD.STRING)
        }
    }

    /** Returns the contact's birthday (`vcard:bday`) as its raw lexical value, or `null` if absent. */
    public fun getBirthday(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.BIRTHDAY)

    /**
     * Sets (or clears, when `null`) the contact's birthday (`vcard:bday`).
     *
     * [birthday] is stored verbatim, typed `xsd:dateTime` when it contains a time
     * component (`T`) and `xsd:date` otherwise.
     */
    public fun setBirthday(birthday: String?) {
        val selfUri = getIdentifier()
        if (birthday.isNullOrBlank()) {
            clearProperties(VCARD.BIRTHDAY, selfUri)
            return
        }
        addQuadLiteral(selfUri, VCARD.BIRTHDAY, birthday, XSD.dateTypeFor(birthday))
    }

    /** Returns the contact's organization name (`vcard:organization-name`), or `null` if absent. */
    public fun getOrganizationName(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.ORGANIZATION_NAME)

    /** Sets (or clears, when `null`) the contact's organization name (`vcard:organization-name`). */
    public fun setOrganizationName(organizationName: String?) {
        setOptionalLiteral(VCARD.ORGANIZATION_NAME, organizationName)
    }

    /** Returns the contact's role (`vcard:role`), or `null` if absent. */
    public fun getRole(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.ROLE)

    /** Sets (or clears, when `null`) the contact's role (`vcard:role`). */
    public fun setRole(role: String?) {
        setOptionalLiteral(VCARD.ROLE, role)
    }

    /** Returns the contact's job title (`vcard:title`), or `null` if absent. */
    public fun getJobTitle(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.TITLE)

    /** Sets (or clears, when `null`) the contact's job title (`vcard:title`). */
    public fun setJobTitle(jobTitle: String?) {
        setOptionalLiteral(VCARD.TITLE, jobTitle)
    }

    /** Returns the contact's note (`vcard:note`), or `null` if absent. */
    public fun getNote(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.NOTE)

    /** Sets (or clears, when `null`) the contact's note (`vcard:note`). */
    public fun setNote(note: String?) {
        setOptionalLiteral(VCARD.NOTE, note)
    }

    /**
     * Adds [url] to this contact as a typed `vcard:url` entry.
     *
     * The entry is a counter-labelled blank node (URL text is not a safe blank-node
     * label) typed with the vocabulary class for [type] and carrying the URL as its
     * `vcard:value`. Does nothing and returns `false` if [url] is blank or already present.
     *
     * @return `true` if the URL was added, `false` if it was a no-op.
     */
    public fun addUrl(type: URLType, url: String): Boolean {
        if (url.isBlank()) return false
        if (getUrls().any { it.second == url }) return false
        var counter = 0
        while (quads.any { it.subject == "_:url$counter" }) counter++
        val blankNode = "_:url$counter"
        addQuad(getIdentifier(), VCARD.URL, blankNode, maxNumber = Int.MAX_VALUE)
        addQuad(blankNode, RDF.TYPE, vocabularyFor(type))
        addQuad(blankNode, VCARD.VALUE, url)
        return true
    }

    /**
     * Removes the `vcard:url` entry whose value is [url] from this contact's quad list.
     *
     * @return `true` if the entry was found and removed, `false` if it was not present.
     */
    public fun removeUrl(url: String): Boolean {
        val selfUri = getIdentifier()
        val link = quads
            .filter { it.subject == selfUri && it.predicate == VCARD.URL }
            .find { urlLink ->
                quads.any {
                    it.subject == urlLink.`object` && it.predicate == VCARD.VALUE && it.`object` == url
                }
            } ?: return false
        quads.removeAll { it.subject == link.`object` }
        quads.remove(link)
        return true
    }

    /**
     * Sets the contact's photo link (`vcard:hasPhoto`) to [photoUri].
     *
     * Only the link is written; uploading the binary itself is the contacts data
     * module's responsibility.
     */
    public fun setPhoto(photoUri: String) {
        addQuad(getIdentifier(), VCARD.HAS_PHOTO, photoUri)
    }

    /**
     * Removes the contact's photo link (`vcard:hasPhoto`).
     *
     * @return `true` if a link was present and removed, `false` otherwise.
     */
    public fun removePhoto(): Boolean {
        if (getPhotoUrl() == null) return false
        clearProperties(VCARD.HAS_PHOTO, getIdentifier())
        return true
    }

    /**
     * Returns all phone numbers with their vCard classification. Untyped legacy nodes
     * and nodes carrying unrecognized types read as [PhoneType.OTHER]; a leading `tel:`
     * scheme is stripped from the value.
     */
    public fun getPhoneEntries(): List<PhoneEntry> =
        entryNodes(VCARD.HAS_TELEPHONE).mapNotNull { node ->
            nodeValue(node)?.let { value ->
                PhoneEntry(percentDecode(value.removePrefix("tel:")), phoneTypeOf(node))
            }
        }

    /**
     * Adds a typed `vcard:hasTelephone` entry on a counter-labelled blank node
     * (`_:phone{n}`). [PhoneType.OTHER] entries are written without a type triple,
     * byte-compatible with legacy writers. Does nothing and returns `false` when
     * [number] is blank or already present.
     *
     * The stored value is a valid `tel:` IRI: RFC 3966 visual separators (spaces,
     * parentheses, dots, hyphens, slashes) are stripped and any remaining
     * IRI-illegal character is percent-encoded, so `"+31 6 12 34 56 78"` is stored
     * as `tel:+31612345678`.
     */
    public fun addPhone(number: String, type: PhoneType = PhoneType.OTHER): Boolean {
        if (number.isBlank()) return false
        val telValue = telIri(number)
        if (quads.any { it.predicate == VCARD.VALUE && normalizedTel(it.`object`) == telValue }) return false
        val node = freshBlankNode("phone")
        addQuad(getIdentifier(), VCARD.HAS_TELEPHONE, node, maxNumber = Int.MAX_VALUE)
        phoneTypeIri(type)?.let { addQuad(node, RDF.TYPE, it) }
        addQuadLiteral(node, VCARD.VALUE, telValue, null)
        return true
    }

    /**
     * Returns all email addresses with their vCard classification. Untyped legacy nodes
     * read as [EmailType.OTHER]; a leading `mailto:` scheme is stripped from the value.
     */
    public fun getEmailEntries(): List<EmailEntry> =
        entryNodes(VCARD.HAS_EMAIL).mapNotNull { node ->
            nodeValue(node)?.let { value ->
                EmailEntry(percentDecode(value.removePrefix("mailto:")), emailTypeOf(node))
            }
        }

    /**
     * Adds a typed `vcard:hasEmail` entry on a counter-labelled blank node (`_:email{n}`).
     * [EmailType.OTHER] entries are written without a type triple. Does nothing and
     * returns `false` when [address] is blank or already present.
     *
     * The stored value is a valid `mailto:` IRI: the address is trimmed and any
     * IRI-illegal character is percent-encoded.
     */
    public fun addEmail(address: String, type: EmailType = EmailType.OTHER): Boolean {
        if (address.isBlank()) return false
        val mailtoValue = mailtoIri(address)
        if (quads.any { it.predicate == VCARD.VALUE && it.`object` == mailtoValue }) return false
        val node = freshBlankNode("email")
        addQuad(getIdentifier(), VCARD.HAS_EMAIL, node, maxNumber = Int.MAX_VALUE)
        emailTypeIri(type)?.let { addQuad(node, RDF.TYPE, it) }
        addQuadLiteral(node, VCARD.VALUE, mailtoValue, null)
        return true
    }

    /**
     * Returns all instant-messaging handles (`vcard:hasInstantMessage`) with their
     * vCard classification. Untyped legacy nodes read as [ImType.OTHER].
     */
    public fun getImEntries(): List<ImEntry> =
        entryNodes(VCARD.HAS_INSTANT_MESSAGE).mapNotNull { node ->
            nodeValue(node)?.let { value -> ImEntry(value, imTypeOf(node)) }
        }

    /**
     * Adds a typed `vcard:hasInstantMessage` entry on a counter-labelled blank node
     * (`_:im{n}`). [ImType.OTHER] entries are written without a type triple. The handle
     * is stored verbatim as an `xsd:string` value — instant-messaging identifiers span
     * many schemes and bare forms, so it is kept exactly as provided. Does nothing and
     * returns `false` when [handle] is blank or already present.
     */
    public fun addImpp(handle: String, type: ImType = ImType.OTHER): Boolean {
        if (handle.isBlank()) return false
        val value = handle.trim()
        if (entryNodes(VCARD.HAS_INSTANT_MESSAGE).any { nodeValue(it) == value }) return false
        val node = freshBlankNode("im")
        addQuad(getIdentifier(), VCARD.HAS_INSTANT_MESSAGE, node, maxNumber = Int.MAX_VALUE)
        imTypeIri(type)?.let { addQuad(node, RDF.TYPE, it) }
        addQuadLiteral(node, VCARD.VALUE, value, XSD.STRING)
        return true
    }

    /**
     * Returns all postal addresses (`vcard:hasAddress`). Nodes missing the
     * `vcard:Address` class type still parse; entries with no address part are skipped.
     */
    public fun getAddresses(): List<AddressEntry> =
        entryNodes(VCARD.HAS_ADDRESS).mapNotNull { node ->
            val entry = AddressEntry(
                street = findPropertyForSubject(node, VCARD.STREET_ADDRESS),
                locality = findPropertyForSubject(node, VCARD.LOCALITY),
                region = findPropertyForSubject(node, VCARD.REGION),
                postalCode = findPropertyForSubject(node, VCARD.POSTAL_CODE),
                countryName = findPropertyForSubject(node, VCARD.COUNTRY_NAME),
                poBox = findPropertyForSubject(node, VCARD.POST_OFFICE_BOX),
                type = addressTypeOf(node),
            )
            entry.takeIf { !it.isEmpty() }
        }

    /**
     * Adds a postal address on a counter-labelled blank node (`_:addr{n}`), always typed
     * `vcard:Address` plus `vcard:Home`/`vcard:Work` when classified. Does nothing and
     * returns `false` when [entry] has no non-blank part.
     */
    public fun addAddress(entry: AddressEntry): Boolean {
        if (entry.isEmpty()) return false
        val node = freshBlankNode("addr")
        addQuad(getIdentifier(), VCARD.HAS_ADDRESS, node, maxNumber = Int.MAX_VALUE)
        addQuad(node, RDF.TYPE, VCARD.ADDRESS, maxNumber = Int.MAX_VALUE)
        when (entry.type) {
            AddressType.HOME -> addQuad(node, RDF.TYPE, VCARD.HOME, maxNumber = Int.MAX_VALUE)
            AddressType.WORK -> addQuad(node, RDF.TYPE, VCARD.WORK, maxNumber = Int.MAX_VALUE)
            AddressType.OTHER -> Unit
        }
        entry.street?.let { addQuadLiteral(node, VCARD.STREET_ADDRESS, it, XSD.STRING) }
        entry.locality?.let { addQuadLiteral(node, VCARD.LOCALITY, it, XSD.STRING) }
        entry.region?.let { addQuadLiteral(node, VCARD.REGION, it, XSD.STRING) }
        entry.postalCode?.let { addQuadLiteral(node, VCARD.POSTAL_CODE, it, XSD.STRING) }
        entry.countryName?.let { addQuadLiteral(node, VCARD.COUNTRY_NAME, it, XSD.STRING) }
        entry.poBox?.let { addQuadLiteral(node, VCARD.POST_OFFICE_BOX, it, XSD.STRING) }
        return true
    }

    /** Returns all typed URLs as [UrlEntry] values (wrapper over [getUrls]). */
    public fun getUrlEntries(): List<UrlEntry> =
        getUrls().map { UrlEntry(it.first, it.second) }

    /** Returns the contact's nickname (`vcard:nickname`), or `null` if absent. */
    public fun getNickname(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.NICKNAME)

    /** Sets (or clears, when `null`) the contact's nickname (`vcard:nickname`). */
    public fun setNickname(nickname: String?) {
        setOptionalLiteral(VCARD.NICKNAME, nickname)
    }

    /** Returns the contact's anniversary (`vcard:anniversary`) raw lexical value, or `null`. */
    public fun getAnniversary(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.ANNIVERSARY)

    /**
     * Sets (or clears, when `null`) the contact's anniversary (`vcard:anniversary`),
     * typed `xsd:dateTime` when the value contains a time component and `xsd:date` otherwise.
     */
    public fun setAnniversary(anniversary: String?) {
        val selfUri = getIdentifier()
        if (anniversary.isNullOrBlank()) {
            clearProperties(VCARD.ANNIVERSARY, selfUri)
            return
        }
        addQuadLiteral(selfUri, VCARD.ANNIVERSARY, anniversary, XSD.dateTypeFor(anniversary))
    }

    /** Returns the contact's organizational unit (`vcard:organization-unit`), or `null`. */
    public fun getOrganizationUnit(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.ORGANIZATION_UNIT)

    /** Sets (or clears, when `null`) the contact's organizational unit. */
    public fun setOrganizationUnit(unit: String?) {
        setOptionalLiteral(VCARD.ORGANIZATION_UNIT, unit)
    }

    /**
     * Returns the contact's persistent identifier (`vcard:hasUID`), or `null`.
     *
     * A value that [setUid] wrapped as `urn:uid:…` (because it was not an absolute
     * IRI) is unwrapped back to the original string, so set/get round-trips.
     */
    public fun getUid(): String? =
        findPropertyForSubject(getIdentifier(), VCARD.HAS_UID)?.let { stored ->
            if (stored.startsWith(UID_URN_PREFIX)) {
                percentDecode(stored.removePrefix(UID_URN_PREFIX))
            } else {
                stored
            }
        }

    /**
     * Sets (or clears, when `null`) the contact's persistent identifier
     * (`vcard:hasUID`, IRI). A value that is already an absolute IRI (`urn:uuid:…`,
     * `https://…`) is stored verbatim; anything else (e.g. a bare UID from a `.vcf`
     * import) is wrapped as `urn:uid:{percent-encoded value}` so the stored object
     * is always a valid IRI.
     */
    public fun setUid(uid: String?) {
        val selfUri = getIdentifier()
        if (uid.isNullOrBlank()) {
            clearProperties(VCARD.HAS_UID, selfUri)
        } else {
            val trimmed = uid.trim()
            val iri = if (ABSOLUTE_IRI_SCHEME.containsMatchIn(trimmed)) {
                trimmed
            } else {
                UID_URN_PREFIX + percentEncodeIriSuffix(trimmed)
            }
            addQuad(selfUri, VCARD.HAS_UID, iri)
        }
    }

    /** The contact's category tags (`vcard:hasCategory` literals). */
    public fun getCategories(): List<String> =
        findAllPropertiesForSubject(getIdentifier(), VCARD.HAS_CATEGORY)

    /** Replaces the contact's category tags with [categories] (trimmed, de-duplicated, blanks dropped). */
    public fun setCategories(categories: List<String>) {
        val selfUri = getIdentifier()
        clearProperties(VCARD.HAS_CATEGORY, selfUri)
        categories.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
            addQuadLiteral(selfUri, VCARD.HAS_CATEGORY, it, XSD.STRING, maxNumber = Int.MAX_VALUE)
        }
    }

    /**
     * The contact's geographic positions (`vcard:hasGeo`), as stored IRIs — typically
     * `geo:` URIs. An entry stored on a blank node by a foreign writer is read through
     * its `vcard:value`.
     */
    public fun getGeos(): List<String> =
        findAllPropertiesForSubject(getIdentifier(), VCARD.HAS_GEO).mapNotNull { obj ->
            if (obj.startsWith("_:")) nodeValue(obj) else obj
        }

    /**
     * Replaces the contact's geographic positions with [geos] (trimmed, de-duplicated,
     * blanks dropped). Each entry is stored as a direct IRI object per the W3C vCard
     * ontology (`vcard:hasGeo <geo:…>`); a bare `lat,lng` pair is prefixed with the
     * `geo:` scheme so the stored object is always a valid IRI.
     */
    public fun setGeos(geos: List<String>) {
        clearEntryNodes(VCARD.HAS_GEO)
        geos.map { it.trim() }.filter { it.isNotBlank() }.map { geoIri(it) }.distinct().forEach {
            addQuad(getIdentifier(), VCARD.HAS_GEO, it, maxNumber = Int.MAX_VALUE)
        }
    }

    /**
     * The contact's languages (`vcard:hasLanguage` entry nodes' `vcard:language` tags),
     * in stored order.
     */
    public fun getLanguages(): List<String> =
        entryNodes(VCARD.HAS_LANGUAGE).mapNotNull { node ->
            findPropertyForSubject(node, VCARD.LANGUAGE)
        }

    /**
     * Replaces the contact's languages with [languages] (BCP-47 tags; trimmed,
     * de-duplicated case-insensitively, blanks dropped). Each tag lives on a
     * counter-labelled blank node (`_:lang{n}`) carrying `vcard:language`, the W3C
     * vCard ontology mapping of the vCard 4.0 `LANG` property.
     */
    public fun setLanguages(languages: List<String>) {
        clearEntryNodes(VCARD.HAS_LANGUAGE)
        languages.map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .forEach { tag ->
                val node = freshBlankNode("lang")
                addQuad(getIdentifier(), VCARD.HAS_LANGUAGE, node, maxNumber = Int.MAX_VALUE)
                addQuadLiteral(node, VCARD.LANGUAGE, tag, XSD.STRING)
            }
    }

    /** The contact's gender (`vcard:hasGender`, an ontology gender-class IRI), or `null`. */
    public fun getGender(): Gender? =
        when (findPropertyForSubject(getIdentifier(), VCARD.HAS_GENDER)) {
            null -> null
            VCARD.MALE -> Gender.MALE
            VCARD.FEMALE -> Gender.FEMALE
            VCARD.GENDER_OTHER -> Gender.OTHER
            VCARD.GENDER_NONE -> Gender.NONE
            else -> Gender.UNKNOWN
        }

    /** Sets (or clears, when `null`) the contact's gender (`vcard:hasGender`, an IRI). */
    public fun setGender(gender: Gender?) {
        val selfUri = getIdentifier()
        clearProperties(VCARD.HAS_GENDER, selfUri)
        val iri = when (gender) {
            Gender.MALE -> VCARD.MALE
            Gender.FEMALE -> VCARD.FEMALE
            Gender.OTHER -> VCARD.GENDER_OTHER
            Gender.NONE -> VCARD.GENDER_NONE
            Gender.UNKNOWN -> VCARD.GENDER_UNKNOWN
            null -> null
        }
        if (iri != null) addQuad(selfUri, VCARD.HAS_GENDER, iri)
    }

    /**
     * Rewrites this contact's writable state from [data] with replace semantics: every
     * multi-valued entry (phones, emails, instant messages, addresses, URLs) and every
     * optional literal is replaced by the snapshot's content; properties absent from
     * [data] are removed. The photo link (`vcard:hasPhoto`) is left untouched.
     */
    public fun setContactData(data: ContactData) {
        clearEntryNodes(VCARD.HAS_TELEPHONE)
        clearEntryNodes(VCARD.HAS_EMAIL)
        clearEntryNodes(VCARD.HAS_INSTANT_MESSAGE)
        clearEntryNodes(VCARD.HAS_ADDRESS)
        clearEntryNodes(VCARD.URL)
        data.effectiveFullName().takeIf { it.isNotBlank() }?.let { setFullName(it) }
            ?: clearProperties(VCARD.FN, getIdentifier())
        setName(data.name)
        setNickname(data.nickname)
        data.phones.forEach { addPhone(it.number, it.type) }
        data.emails.forEach { addEmail(it.address, it.type) }
        data.impps.forEach { addImpp(it.handle, it.type) }
        data.addresses.forEach { addAddress(it) }
        setBirthday(data.birthday)
        setAnniversary(data.anniversary)
        setOrganizationName(data.organizationName)
        setOrganizationUnit(data.organizationUnit)
        setRole(data.role)
        setJobTitle(data.title)
        setNote(data.note)
        setCategories(data.categories)
        setGender(data.gender)
        setGeos(data.geos)
        setLanguages(data.languages)
        data.urls.forEach { addUrl(it.type, it.value) }
        setUid(data.uid)
    }

    /** Reads this contact's complete writable state into a [ContactData] snapshot. */
    public fun toContactData(): ContactData = ContactData(
        fullName = quads.find {
            it.subject == getIdentifier() && it.predicate == VCARD.FN
        }?.`object`,
        name = getName(),
        nickname = getNickname(),
        phones = getPhoneEntries(),
        emails = getEmailEntries(),
        impps = getImEntries(),
        addresses = getAddresses(),
        birthday = getBirthday(),
        anniversary = getAnniversary(),
        organizationName = getOrganizationName(),
        organizationUnit = getOrganizationUnit(),
        role = getRole(),
        title = getJobTitle(),
        note = getNote(),
        categories = getCategories(),
        gender = getGender(),
        geos = getGeos(),
        languages = getLanguages(),
        urls = getUrlEntries(),
        uid = getUid(),
    )

    private fun entryNodes(linkPredicate: String): List<String> =
        quads
            .filter { it.subject == getIdentifier() && it.predicate == linkPredicate }
            .map { it.`object` }

    private fun nodeValue(node: String): String? =
        findPropertyForSubject(node, VCARD.VALUE)

    private fun nodeTypes(node: String): List<String> =
        findAllPropertiesForSubject(node, RDF.TYPE)

    private fun phoneTypeOf(node: String): PhoneType {
        val types = nodeTypes(node)
        return when {
            VCARD.CELL in types -> PhoneType.CELL
            VCARD.HOME in types -> PhoneType.HOME
            VCARD.WORK in types -> PhoneType.WORK
            VCARD.FAX in types -> PhoneType.FAX
            VCARD.PAGER in types -> PhoneType.PAGER
            VCARD.VOICE in types -> PhoneType.VOICE
            VCARD.TEXT in types -> PhoneType.TEXT
            VCARD.VIDEO in types -> PhoneType.VIDEO
            VCARD.TEXT_PHONE in types -> PhoneType.TEXT_PHONE
            else -> PhoneType.OTHER
        }
    }

    private fun phoneTypeIri(type: PhoneType): String? = when (type) {
        PhoneType.CELL -> VCARD.CELL
        PhoneType.HOME -> VCARD.HOME
        PhoneType.WORK -> VCARD.WORK
        PhoneType.FAX -> VCARD.FAX
        PhoneType.PAGER -> VCARD.PAGER
        PhoneType.VOICE -> VCARD.VOICE
        PhoneType.TEXT -> VCARD.TEXT
        PhoneType.VIDEO -> VCARD.VIDEO
        PhoneType.TEXT_PHONE -> VCARD.TEXT_PHONE
        PhoneType.OTHER -> null
    }

    private fun emailTypeOf(node: String): EmailType {
        val types = nodeTypes(node)
        return when {
            VCARD.HOME in types -> EmailType.HOME
            VCARD.WORK in types -> EmailType.WORK
            else -> EmailType.OTHER
        }
    }

    private fun emailTypeIri(type: EmailType): String? = when (type) {
        EmailType.HOME -> VCARD.HOME
        EmailType.WORK -> VCARD.WORK
        EmailType.OTHER -> null
    }

    private fun imTypeOf(node: String): ImType {
        val types = nodeTypes(node)
        return when {
            VCARD.HOME in types -> ImType.HOME
            VCARD.WORK in types -> ImType.WORK
            else -> ImType.OTHER
        }
    }

    private fun imTypeIri(type: ImType): String? = when (type) {
        ImType.HOME -> VCARD.HOME
        ImType.WORK -> VCARD.WORK
        ImType.OTHER -> null
    }

    private fun addressTypeOf(node: String): AddressType {
        val types = nodeTypes(node)
        return when {
            VCARD.HOME in types -> AddressType.HOME
            VCARD.WORK in types -> AddressType.WORK
            else -> AddressType.OTHER
        }
    }

    private fun freshBlankNode(prefix: String): String {
        var counter = 0
        while (quads.any { it.subject == "_:$prefix$counter" }) counter++
        return "_:$prefix$counter"
    }

    private fun clearEntryNodes(linkPredicate: String) {
        val selfUri = getIdentifier()
        val nodes = entryNodes(linkPredicate)
        nodes.forEach { node -> quads.removeAll { it.subject == node } }
        quads.removeAll { it.subject == selfUri && it.predicate == linkPredicate }
    }

    private fun setOptionalLiteral(predicate: String, value: String?) {
        val selfUri = getIdentifier()
        if (value.isNullOrBlank()) {
            clearProperties(predicate, selfUri)
        } else {
            addQuadLiteral(selfUri, predicate, value, XSD.STRING)
        }
    }

    private fun vocabularyFor(type: URLType): String = when (type) {
        URLType.Home -> VCARD.HOME
        URLType.Work -> VCARD.WORK
        URLType.Homepage -> VCARD.HOMEPAGE
        URLType.WebId -> VCARD.WEB_ID
        URLType.PublicId -> VCARD.PUBLIC_ID
    }

    private fun telIri(number: String): String =
        "tel:" + percentEncodeIriSuffix(number.trim().replace(TEL_VISUAL_SEPARATORS, ""))

    private fun normalizedTel(storedValue: String): String =
        telIri(storedValue.removePrefix("tel:"))

    private fun mailtoIri(address: String): String =
        "mailto:" + percentEncodeIriSuffix(address.trim())

    private fun geoIri(value: String): String =
        if (ABSOLUTE_IRI_SCHEME.containsMatchIn(value)) value else "geo:" + value.replace(" ", "")

    private companion object {
        private val TEL_VISUAL_SEPARATORS = Regex("""[\s().\-/]""")
        private val ABSOLUTE_IRI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.\\-]*:")
        private const val UID_URN_PREFIX = "urn:uid:"
        private const val IRI_SAFE_PUNCTUATION = "-._~!$&'()*+,;=:@"

        private fun percentEncodeIriSuffix(value: String): String = buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val code = byte.toInt() and 0xFF
                val char = code.toChar()
                if (code < 0x80 && (char.isLetterOrDigit() || char in IRI_SAFE_PUNCTUATION)) {
                    append(char)
                } else {
                    append('%')
                    append("%02X".format(code))
                }
            }
        }

        private fun percentDecode(value: String): String =
            runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }
                .getOrDefault(value)
    }
}
