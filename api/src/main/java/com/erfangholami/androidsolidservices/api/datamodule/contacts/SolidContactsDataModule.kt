package com.erfangholami.androidsolidservices.api.datamodule.contacts

import android.content.Context
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.SolidContactsDataModuleImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact

/**
 * Manages Solid Contacts data (address books, contacts, groups) on a user's Solid pod.
 *
 * All operations are performed on behalf of [ownerWebId] using the Solid Contacts
 * specification.  Results are wrapped in [DataModuleResult] to distinguish data errors
 * from unexpected exceptions.
 *
 * Obtain an instance via [SolidContactsDataModule.getInstance].
 */
public interface SolidContactsDataModule {

    public companion object {
        /**
         * Returns the application-scoped singleton [SolidContactsDataModule].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getInstance(authenticator: Authenticator): SolidContactsDataModule =
            SolidContactsDataModuleImplementation.getInstance(authenticator)

        public fun getInstance(resourceManager: SolidResourceManager): SolidContactsDataModule =
            SolidContactsDataModuleImplementation.getInstance(resourceManager)
    }

    //region AddressBooks
    /** Returns every address book that belongs to [ownerWebId]. */
    public suspend fun getAddressBooks(
        ownerWebId: String
    ): DataModuleResult<AddressBookList>

    /**
     * Creates a new address book titled [title] on [ownerWebId]'s pod.
     *
     * @param isPrivate When `true`, the address book is created with
     *   owner-only access; when `false`, it is publicly readable.
     * @param storage The pod storage (root) URL the address book is created under.
     * @param container Optional container URI to create the address book in;
     *   when `null`, a default contacts container under [storage] is used.
     */
    public suspend fun createAddressBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean = true,
        storage: String,
        container: String? = null,
    ): DataModuleResult<AddressBook>

    /** Reads the address book at [addressBookUri]. */
    public suspend fun getAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<AddressBook>

    /** Renames the address book at [addressBookUri] to [newName]. */
    public suspend fun renameAddressBook(
        ownerWebId: String,
        addressBookUri: String,
        newName: String,
    ): DataModuleResult<AddressBook>

    /** Deletes the address book at [addressBookUri] and returns the removed address book. */
    public suspend fun deleteAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<AddressBook>
    //endregion

    //region Contacts
    /**
     * Creates [newContact] in the address book at [addressBookString],
     * optionally adding it to the groups whose URIs are listed in [groupStrings].
     */
    public suspend fun createNewContact(
        ownerWebId: String,
        addressBookString: String,
        newContact: NewContact,
        groupStrings: List<String> = emptyList(),
    ): DataModuleResult<FullContact>

    /** Reads the full contact at [contactString], including its names, e-mail addresses and phone numbers. */
    public suspend fun getContact(
        ownerWebId: String,
        contactString: String
    ): DataModuleResult<FullContact>

    /** Updates the formatted name of the contact at [contactString] to [newName]. */
    public suspend fun renameContact(
        ownerWebId: String,
        contactString: String,
        newName: String,
    ): DataModuleResult<FullContact>

    /** Adds [newPhoneNumber] to the contact at [contactString]. */
    public suspend fun addNewPhoneNumber(
        ownerWebId: String,
        contactString: String,
        newPhoneNumber: String,
    ): DataModuleResult<FullContact>

    /** Adds [newEmailAddress] to the contact at [contactString]. */
    public suspend fun addNewEmailAddress(
        ownerWebId: String,
        contactString: String,
        newEmailAddress: String,
    ): DataModuleResult<FullContact>

    /** Removes [phoneNumber] from the contact at [contactString]. */
    public suspend fun removePhoneNumber(
        ownerWebId: String,
        contactString: String,
        phoneNumber: String,
    ): DataModuleResult<FullContact>

    /** Removes [emailAddress] from the contact at [contactString]. */
    public suspend fun removeEmailAddress(
        ownerWebId: String,
        contactString: String,
        emailAddress: String,
    ): DataModuleResult<FullContact>

    /** Deletes the contact at [contactUri] from the address book at [addressBookUri]. */
    public suspend fun deleteContact(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): DataModuleResult<FullContact>
    //endregion

    //region Groups
    /**
     * Creates a new group titled [title] in the address book at
     * [addressBookString], optionally seeding it with the contacts whose URIs
     * are listed in [contactUris].
     */
    public suspend fun createNewGroup(
        ownerWebId: String,
        addressBookString: String,
        title: String,
        contactUris: List<String> = emptyList(),
    ): DataModuleResult<FullGroup>

    /** Reads the full group at [groupString], including its member contacts. */
    public suspend fun getGroup(
        ownerWebId: String,
        groupString: String,
    ): DataModuleResult<FullGroup>

    /** Deletes the group at [groupString] from the address book at [addressBookString]. */
    public suspend fun deleteGroup(
        ownerWebId: String,
        addressBookString: String,
        groupString: String
    ): DataModuleResult<FullGroup>

    /** Adds the contact at [contactString] to the group at [groupString]. */
    public suspend fun addContactToGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): DataModuleResult<FullGroup>

    /** Removes the contact at [contactString] from the group at [groupString]. */
    public suspend fun removeContactFromGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): DataModuleResult<FullGroup>
    //endregion
}
