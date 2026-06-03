package com.erfangholami.androidsolidservices.shared.model.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import kotlinx.parcelize.Parcelize

/**
 * A lightweight group entry as it appears in an address-book groups index.
 *
 * This is a summary record (URI + display name). To retrieve the group's member
 * contacts, fetch [FullGroup] via the contacts data module using [uri].
 *
 * @property uri  The absolute pod URI of the group's RDF resource.
 * @property name The group's display name (`vcard:fn`).
 */
@Parcelize
public data class Group(
    val uri: String,
    val name: String,
) : Parcelable

/**
 * The full detail of a group fetched from the pod, including its member contacts.
 *
 * @property uri      The absolute pod URI of the group's RDF resource.
 * @property name     The group's display name (`vcard:fn`).
 * @property contacts Lightweight [Contact] entries for all members of this group.
 */
@Parcelize
public data class FullGroup(
    val uri: String,
    val name: String,
    val contacts: List<Contact>,
) : Parcelable {
    public companion object {
        /**
         * Constructs a [FullGroup] from a parsed group RDF resource.
         */
        public fun createFromRdf(groupRdf: GroupRDF): FullGroup {
            return FullGroup(
                uri = groupRdf.getIdentifier().toString(),
                name = groupRdf.getTitle(),
                contacts = groupRdf.getContacts()
            )
        }
    }
}
