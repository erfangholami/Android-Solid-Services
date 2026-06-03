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
    public const val ORGANIZATION_NAME: String = "${NAMESPACE}organization-name"
    public const val ROLE: String = "${NAMESPACE}role"
    public const val TITLE: String = "${NAMESPACE}title"
    public const val NOTE: String = "${NAMESPACE}note"
    public const val VALUE: String = "${NAMESPACE}value"
    public const val HAS_MEMBER: String = "${NAMESPACE}hasMember"
}
