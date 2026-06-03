package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import kotlinx.parcelize.Parcelize

/**
 * A Solid contacts address book.
 *
 * Represents the aggregate view of one address book on a pod: its pod URI, display
 * title, the flat list of [Contact] summaries, and the [Group] summaries. Full
 * contact or group detail (emails, phone numbers, group membership) is fetched
 * separately via the contacts data module.
 *
 * @property uri   The absolute pod URI of the address-book RDF resource.
 * @property title The human-readable display name of the address book.
 * @property contacts Lightweight contact entries (URI + display name) within this book.
 * @property groups   Lightweight group entries (URI + display name) within this book.
 */
@Parcelize
public data class AddressBook(
    val uri: String,
    var title: String,
    var contacts: List<Contact>,
    var groups: List<Group>,
) : Parcelable {
    public companion object {
        /**
         * Constructs an [AddressBook] from its three constituent RDF documents.
         *
         * @param addressBookRdf   The root address-book RDF resource.
         * @param nameEmailIndexRdf The people index listing contacts in this book.
         * @param groupsIndexRdf    The groups index listing groups in this book.
         */
        public fun createFromRdf(
            addressBookRdf: AddressBookRDF,
            nameEmailIndexRdf: NameEmailIndexRDF,
            groupsIndexRdf: GroupsIndexRDF
        ): AddressBook {
            return AddressBook(
                uri = addressBookRdf.getIdentifier().toString(),
                title = addressBookRdf.getTitle(),
                contacts = nameEmailIndexRdf.getContacts(
                    addressBookRdf.getIdentifier().toString()
                ),
                groups = groupsIndexRdf.getGroups(
                    addressBookRdf.getIdentifier().toString()
                )
            )
        }
    }
}

/**
 * The URIs of all address books discoverable for a given pod user, split by
 * visibility.
 *
 * @property publicAddressBookUris  URIs of address books registered in the public type index.
 * @property privateAddressBookUris URIs of address books registered in the private type index.
 */
@Parcelize
public data class AddressBookList(
    val publicAddressBookUris: List<String>,
    val privateAddressBookUris: List<String>,
) : Parcelable
