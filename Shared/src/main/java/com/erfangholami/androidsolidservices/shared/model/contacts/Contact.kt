package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Summary and value types for the contacts model.
 *
 * The V1 *write* model (`NewContact`), *detail* model (`FullContact`) and the untyped
 * `Email` / `PhoneNumber` pair are all gone: both the in-process API and the IPC surface
 * now speak the immutable [ContactData] / [SolidContact] model, which carries full vCard
 * 4.0 coverage — including the *typed* [EmailEntry] / [PhoneEntry] that replaced the bare
 * value classes (a phone with no `vcard:Cell` / `vcard:Home` type reads back as
 * [PhoneType.OTHER] rather than losing the distinction).
 *
 * What remains here is load-bearing: [Contact] is the summary row carried by [AddressBook]
 * and [FullGroup], and [Name] / [URLType] are part of [ContactData] itself.
 */

/** A contact as it appears in a listing: just enough to render a row. */
@Parcelize
public data class Contact(
    val uri: String,
    val name: String,
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
