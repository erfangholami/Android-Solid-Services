package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.result.SolidResult

/**
 * Manages the address books of a pod user.
 *
 * Address books follow the SolidOS layout (`vcard:AddressBook` root with a
 * `nameEmailIndex` and a `groupIndex`) and are registered in the user's private
 * or public type index, from which [list] discovers them.
 */
public interface AddressBookStore {

    /** Lists the address books registered in [ownerWebId]'s private and public type indexes. */
    public suspend fun list(ownerWebId: String): SolidResult<AddressBookList>

    /**
     * Ensures the contacts container (default `{storage}contacts/`) exists — creating it as an
     * LDP BasicContainer when missing — and returns the current (possibly empty) address-book
     * list. Safe to call on every read: it never fails on an unprovisioned pod and provisions
     * the container that later writes (contacts, groups) depend on.
     *
     * [storage] may be omitted (`null`); when it is, and no explicit [container] is given, the
     * storage root is discovered from the profile / container hierarchy.
     */
    public suspend fun ensureContainer(
        ownerWebId: String,
        storage: String? = null,
        container: String? = null,
    ): SolidResult<AddressBookList>

    /** Reads the address book at [addressBookUri] with its contact and group summaries. */
    public suspend fun get(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook>

    /**
     * Creates an address book titled [title] under [container] (default
     * `{storage}contacts/`) and registers it in the private (default) or public
     * type index. [storage] may be omitted (`null`); when it is, and no explicit
     * [container] is given, the storage root is discovered.
     */
    public suspend fun create(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean = true,
        storage: String? = null,
        container: String? = null,
    ): SolidResult<AddressBook>

    /** Renames the address book at [addressBookUri] to [newName]. */
    public suspend fun rename(
        ownerWebId: String,
        addressBookUri: String,
        newName: String,
    ): SolidResult<AddressBook>

    /**
     * Deletes the address book at [addressBookUri]: its type-index registration and
     * its whole container (all contacts, groups, and photos inside).
     */
    public suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook>

    /**
     * Returns the user's default address book: the first book registered in the
     * private type index, or a newly created private book titled [title] under
     * [storage] when none exists yet. [storage] may be omitted (`null`) to have the
     * storage root discovered.
     */
    public suspend fun ensureDefault(
        ownerWebId: String,
        storage: String? = null,
        title: String = "Contacts",
    ): SolidResult<AddressBook>
}
