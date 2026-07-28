package com.erfangholami.androidsolidservices.shared.vocab

import com.erfangholami.androidsolidservices.shared.vocab.VCARD.HAS_MEMBER


/**
 * vCard Ontology vocabulary constants.
 * http://www.w3.org/2006/vcard/ns#
 *
 * Used by Solid pods to represent contacts, address books, and groups.
 * The Solid Contacts data module stores vcard:Individual resources inside
 * vcard:AddressBook containers, with membership expressed via [HAS_MEMBER].
 */
public object VCARD {
    public const val NAMESPACE: String = "http://www.w3.org/2006/vcard/ns#"

    public const val INDIVIDUAL: String = "${NAMESPACE}Individual"
    public const val ADDRESS_BOOK: String = "${NAMESPACE}AddressBook"
    public const val GROUP: String = "${NAMESPACE}Group"
    public const val HOMEPAGE: String = "${NAMESPACE}Homepage"
    public const val WEB_ID: String = "${NAMESPACE}WebId"
    public const val PUBLIC_ID: String = "${NAMESPACE}PublicId"
    public const val HOME: String = "${NAMESPACE}Home"
    public const val WORK: String = "${NAMESPACE}Work"

    /** Telephone feature/type classes asserted on `hasTelephone` nodes. */
    public const val CELL: String = "${NAMESPACE}Cell"
    public const val FAX: String = "${NAMESPACE}Fax"
    public const val PAGER: String = "${NAMESPACE}Pager"
    public const val TEXT: String = "${NAMESPACE}Text"
    public const val TEXT_PHONE: String = "${NAMESPACE}TextPhone"
    public const val VIDEO: String = "${NAMESPACE}Video"
    public const val VOICE: String = "${NAMESPACE}Voice"

    /** Postal address class asserted on `hasAddress` nodes. */
    public const val ADDRESS: String = "${NAMESPACE}Address"
    public const val STREET_ADDRESS: String = "${NAMESPACE}street-address"
    public const val LOCALITY: String = "${NAMESPACE}locality"
    public const val REGION: String = "${NAMESPACE}region"
    public const val POSTAL_CODE: String = "${NAMESPACE}postal-code"
    public const val COUNTRY_NAME: String = "${NAMESPACE}country-name"
    public const val POST_OFFICE_BOX: String = "${NAMESPACE}post-office-box"

    public const val NICKNAME: String = "${NAMESPACE}nickname"
    public const val ORGANIZATION_UNIT: String = "${NAMESPACE}organization-unit"

    /** Links an address book to a resource listing name/email pairs for fast lookup. */
    public const val NAME_EMAIL_INDEX: String = "${NAMESPACE}nameEmailIndex"

    /** Links an address book to a resource listing its groups for fast lookup. */
    public const val GROUP_INDEX: String = "${NAMESPACE}groupIndex"

    /** Links an address book to one of its groups. */
    public const val INCLUDES_GROUP: String = "${NAMESPACE}includesGroup"

    /** Links a contact back to the address book it belongs to. */
    public const val IN_ADDRESS_BOOK: String = "${NAMESPACE}inAddressBook"
    public const val MEMBER: String = "${NAMESPACE}member"

    /** Formatted (display) name — the full name as a single string. */
    public const val FN: String = "${NAMESPACE}fn"

    /** Persistent unique identifier for a contact card. */
    public const val HAS_UID: String = "${NAMESPACE}hasUID"
    public const val HAS_NAME: String = "${NAMESPACE}hasName"
    public const val FAMILY_NAME: String = "${NAMESPACE}family-name"
    public const val GIVEN_NAME: String = "${NAMESPACE}given-name"
    public const val ADDITIONAL_NAME: String = "${NAMESPACE}additional-name"
    public const val HONORIFIC_PREFIX: String = "${NAMESPACE}honorific-prefix"
    public const val HONORIFIC_SUFFIX: String = "${NAMESPACE}honorific-suffix"
    public const val HAS_PHOTO: String = "${NAMESPACE}hasPhoto"
    public const val HAS_RELATED: String = "${NAMESPACE}hasRelated"
    public const val URL: String = "${NAMESPACE}url"
    public const val HAS_ADDRESS: String = "${NAMESPACE}hasAddress"

    /** Birthday date — note the predicate is `bday`, not `birthday`. */
    public const val BIRTHDAY: String = "${NAMESPACE}bday"
    public const val ANNIVERSARY: String = "${NAMESPACE}anniversary"
    public const val HAS_EMAIL: String = "${NAMESPACE}hasEmail"
    public const val HAS_TELEPHONE: String = "${NAMESPACE}hasTelephone"
    public const val HAS_INSTANT_MESSAGE: String = "${NAMESPACE}hasInstantMessage"
    public const val ORGANIZATION_NAME: String = "${NAMESPACE}organization-name"
    public const val ROLE: String = "${NAMESPACE}role"
    public const val TITLE: String = "${NAMESPACE}title"
    public const val NOTE: String = "${NAMESPACE}note"
    public const val VALUE: String = "${NAMESPACE}value"
    public const val HAS_MEMBER: String = "${NAMESPACE}hasMember"

    public const val HAS_CATEGORY: String = "${NAMESPACE}hasCategory"

    public const val HAS_GEO: String = "${NAMESPACE}hasGeo"

    public const val HAS_LANGUAGE: String = "${NAMESPACE}hasLanguage"
    public const val LANGUAGE: String = "${NAMESPACE}language"

    public const val HAS_GENDER: String = "${NAMESPACE}hasGender"
    public const val MALE: String = "${NAMESPACE}Male"
    public const val FEMALE: String = "${NAMESPACE}Female"
    public const val GENDER_OTHER: String = "${NAMESPACE}Other"
    public const val GENDER_NONE: String = "${NAMESPACE}None"
    public const val GENDER_UNKNOWN: String = "${NAMESPACE}Unknown"
}
