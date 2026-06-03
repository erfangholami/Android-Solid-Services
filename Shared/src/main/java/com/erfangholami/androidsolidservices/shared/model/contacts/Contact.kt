package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import kotlinx.parcelize.Parcelize

/**
 * A lightweight contact entry as it appears in an address-book index.
 *
 * This is a summary record (URI + display name) returned when listing contacts in
 * an address book. To retrieve full detail (emails, phone numbers, structured name),
 * fetch [FullContact] via the contacts data module using [uri].
 *
 * @property uri  The absolute pod URI of the contact's RDF resource.
 * @property name The contact's formatted display name (`vcard:fn`).
 */
@Parcelize
public data class Contact(
    val uri: String,
    val name: String,
) : Parcelable

/**
 * Input model for creating a new contact on the pod.
 *
 * @property name        The contact's display name.
 * @property email       An initial email address (may be empty).
 * @property phoneNumber An initial phone number (may be empty).
 */
@Parcelize
public data class NewContact(
    var name: String,
    val email: String,
    val phoneNumber: String,
) : Parcelable

/**
 * The full detail of a contact fetched from the pod.
 *
 * Contains all vCard properties stored in the contact's RDF resource.
 *
 * @property uri            The absolute pod URI of the contact's RDF resource.
 * @property fullName       The contact's formatted display name (`vcard:fn`).
 * @property emailAddresses All email addresses associated with this contact.
 * @property phoneNumbers   All phone numbers associated with this contact.
 */
@Parcelize
public data class FullContact(
    val uri: String,
    val fullName: String,
    val emailAddresses: List<Email>,
    val phoneNumbers: List<PhoneNumber>,
) : Parcelable {
    public companion object {
        /**
         * Constructs a [FullContact] from a parsed contact RDF resource.
         */
        public fun createFromRdf(contactRdf: ContactRDF): FullContact {
            return FullContact(
                uri = contactRdf.getIdentifier().toString(),
                fullName = contactRdf.getFullName(),
                emailAddresses = contactRdf.getEmails(),
                phoneNumbers = contactRdf.getPhoneNumbers()
            )
        }
    }
}

/**
 * A single email address value from a contact's vCard record.
 *
 * @property value The raw email value (e.g. `mailto:alice@example.com`).
 */
@Parcelize
public data class Email(
    val value: String,
) : Parcelable

/**
 * A single phone number value from a contact's vCard record.
 *
 * @property value The raw telephone value (e.g. `tel:+1234567890`).
 */
@Parcelize
public data class PhoneNumber(
    val value: String,
) : Parcelable

/**
 * The type classification of a URL stored in a contact's vCard record (`vcard:url`).
 */
public enum class URLType {
    Home,
    Work,
    Homepage,

    /** The contact's Solid WebID. */
    WebId,

    /** A public identifier for the contact (e.g. a profile page). */
    PublicId,
}

/**
 * The structured name components of a contact (`vcard:hasName`).
 *
 * All fields are optional; only the components present in the pod RDF resource are
 * populated.
 */
@Parcelize
public data class Name(
    val familyName: String? = null,
    val givenName: String? = null,
    val additionalName: String? = null,
    val honorificPrefix: String? = null,
    val honorificSuffix: String? = null,
) : Parcelable