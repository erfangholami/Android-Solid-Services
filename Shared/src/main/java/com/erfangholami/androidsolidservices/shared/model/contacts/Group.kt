package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import kotlinx.parcelize.Parcelize

@Parcelize
public data class Group(
    val uri: String,
    val name: String,
) : Parcelable

@Parcelize
public data class FullGroup(
    val uri: String,
    val name: String,
    val contacts: List<Contact>,
) : Parcelable {
    public companion object {
        public fun createFromRdf(groupRdf: GroupRDF): FullGroup {
            return FullGroup(
                uri = groupRdf.getIdentifier().toString(),
                name = groupRdf.getTitle(),
                contacts = groupRdf.getContacts()
            )
        }
    }
}
