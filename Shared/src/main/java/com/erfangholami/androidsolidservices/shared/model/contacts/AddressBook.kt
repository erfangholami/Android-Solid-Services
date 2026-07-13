package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import kotlinx.parcelize.Parcelize

@Parcelize
public data class AddressBook(
    val uri: String,
    var title: String,
    var contacts: List<Contact>,
    var groups: List<Group>,
) : Parcelable {
    public companion object {
        public fun createFromRdf(
            addressBookRdf: AddressBookRDF,
            nameEmailIndexRdf: NameEmailIndexRDF,
            groupsIndexRdf: GroupsIndexRDF
        ): AddressBook {
            return AddressBook(
                uri = addressBookRdf.getIdentifier(),
                title = addressBookRdf.getTitle(),
                contacts = nameEmailIndexRdf.getContacts(
                    addressBookRdf.getIdentifier()
                ),
                groups = groupsIndexRdf.getGroups(
                    addressBookRdf.getIdentifier()
                )
            )
        }
    }
}

@Parcelize
public data class AddressBookList(
    val publicAddressBookUris: List<String>,
    val privateAddressBookUris: List<String>,
) : Parcelable
