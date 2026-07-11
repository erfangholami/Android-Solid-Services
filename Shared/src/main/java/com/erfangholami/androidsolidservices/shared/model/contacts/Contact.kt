package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import kotlinx.parcelize.Parcelize

@Parcelize
public data class Contact(
    val uri: String,
    val name: String,
) : Parcelable

@Parcelize
public data class NewContact(
    var name: String,
    val email: String,
    val phoneNumber: String,
) : Parcelable

@Parcelize
public data class FullContact(
    val uri: String,
    val fullName: String,
    val emailAddresses: List<Email>,
    val phoneNumbers: List<PhoneNumber>,
) : Parcelable {
    public companion object {
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

@Parcelize
public data class Email(
    val value: String,
) : Parcelable

@Parcelize
public data class PhoneNumber(
    val value: String,
) : Parcelable

public enum class URLType {
    Home,
    Work,
    Homepage,
    WebId,
    PublicId,
}

@Parcelize
public data class Name(
    val familyName: String? = null,
    val givenName: String? = null,
    val additionalName: String? = null,
    val honorificPrefix: String? = null,
    val honorificSuffix: String? = null,
) : Parcelable