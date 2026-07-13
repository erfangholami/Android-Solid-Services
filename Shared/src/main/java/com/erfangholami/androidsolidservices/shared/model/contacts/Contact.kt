package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Summary and value types for the contacts model.
 *
 * The former V1 *write* model (`NewContact`) and V1 *detail* model (`FullContact`) have
 * been removed: both the in-process API and the IPC surface now speak the immutable
 * [ContactData] / [SolidContact] model, which carries full vCard 4.0 coverage (typed
 * phones, emails, postal addresses, IM handles, categories, gender, …).
 *
 * The types below remain because they are still load-bearing: [Contact] is the summary row
 * carried by [AddressBook] / [FullGroup] and the name-email index, and the codec reads and
 * writes the primitive [Email] / [PhoneNumber] / [Name] shapes.
 */

/** A contact as it appears in a listing: just enough to render a row. */
@Parcelize
public data class Contact(
    val uri: String,
    val name: String,
) : Parcelable

/** A bare email address, as read from a `vcard:hasEmail` node. */
@Parcelize
public data class Email(
    val value: String,
) : Parcelable

/** A bare phone number, as read from a `vcard:hasTelephone` node. */
@Parcelize
public data class PhoneNumber(
    val value: String,
) : Parcelable

/** The kind of link a `vcard:hasURL` node carries. */
public enum class URLType {
    Home,
    Work,
    Homepage,
    WebId,
    PublicId,
}

/** The structured-name parts of a contact (`vcard:hasName`). */
@Parcelize
public data class Name(
    val familyName: String? = null,
    val givenName: String? = null,
    val additionalName: String? = null,
    val honorificPrefix: String? = null,
    val honorificSuffix: String? = null,
) : Parcelable
