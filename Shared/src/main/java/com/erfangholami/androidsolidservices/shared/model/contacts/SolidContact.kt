package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import kotlinx.parcelize.Parcelize

@Parcelize
public data class SolidContact(
    val uri: String,
    val etag: String? = null,
    val modified: Long? = null,
    val photoUri: String? = null,
    val data: ContactData,
) : Parcelable {

    public val fullName: String get() = data.fullName ?: data.effectiveFullName()

    public companion object {
        public fun createFromRdf(contactRdf: ContactRDF): SolidContact = SolidContact(
            uri = contactRdf.getIdentifier().toString(),
            etag = contactRdf.getMetadata().etag,
            modified = contactRdf.getLastModified(),
            photoUri = contactRdf.getPhotoUrl(),
            data = contactRdf.toContactData(),
        )
    }
}

@Parcelize
public data class SolidContactList(
    val contacts: List<SolidContact>,
) : Parcelable

@Parcelize
public data class ContactMatch(
    val contact: SolidContact? = null,
    val addressBookUri: String? = null,
) : Parcelable {
    public fun exists(): Boolean = contact != null
}
