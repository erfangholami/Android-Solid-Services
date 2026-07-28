package com.erfangholami.androidsolidservices.shared.model.contacts;

import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookListCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleSolidContactCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleSolidContactListCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleContactPhotoCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleContactMatchCallback;
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullGroupCallback;

/**
 * AIDL IPC contract for the Solid Contacts data module.
 *
 * Mirrors the in-process module one-for-one: its three role stores (address books,
 * contacts, groups) are flattened into a single interface, with the store name as a
 * prefix. The write model is the immutable ContactData (full vCard 4.0 coverage) — the
 * same type the in-process API takes — so the IPC and in-process surfaces cannot drift.
 *
 * Third-party apps normally use the higher-level client SDK
 * (Solid.getContactsDataModule(...)) rather than binding here directly.
 */
interface IASSContactsModuleInterface {

    // ---- Address books ----

    /** Returns every address book that belongs to the user. */
    void listAddressBooks(String webId, IASSContactModuleAddressBookListCallback callback);

    /** Bootstraps the address-book container if absent, then returns the books in it. */
    void ensureAddressBookContainer(
        String webId,
        @nullable String storage,
        @nullable String container,
        IASSContactModuleAddressBookListCallback callback
    );

    /** Reads the address book at addressBookUri. */
    void getAddressBook(
        String webId,
        String addressBookUri,
        IASSContactModuleAddressBookCallback callback
    );

    /**
     * Creates an address book. isPrivate selects the private (default) or public type
     * index; storage / container override the registered or default location.
     */
    void createAddressBook(
        String webId,
        String title,
        boolean isPrivate,
        @nullable String storage,
        @nullable String container,
        IASSContactModuleAddressBookCallback callback
    );

    /** Renames the address book at addressBookUri. */
    void renameAddressBook(
        String webId,
        String addressBookUri,
        String newName,
        IASSContactModuleAddressBookCallback callback
    );

    /** Deletes the address book at addressBookUri and everything in it. */
    void deleteAddressBook(
        String webId,
        String addressBookUri,
        IASSContactModuleAddressBookCallback callback
    );

    /** Returns the user's default address book, creating it (titled title) if absent. */
    void ensureDefaultAddressBook(
        String webId,
        @nullable String storage,
        String title,
        IASSContactModuleAddressBookCallback callback
    );

    // ---- Contacts ----

    /** Reads the contact at contactUri. */
    void getContact(
        String webId,
        String contactUri,
        IASSContactModuleSolidContactCallback callback
    );

    /** Lists the contacts in the address book at addressBookUri. */
    void listContacts(
        String webId,
        String addressBookUri,
        IASSContactModuleSolidContactListCallback callback
    );

    /** Creates a contact from data, optionally adding it to groupUris. */
    void createContact(
        String webId,
        String addressBookUri,
        in ContactData data,
        in List<String> groupUris,
        IASSContactModuleSolidContactCallback callback
    );

    /**
     * Rewrites the contact at contactUri from data with replace semantics: properties
     * absent from data are removed.
     */
    void updateContact(
        String webId,
        String addressBookUri,
        String contactUri,
        in ContactData data,
        IASSContactModuleSolidContactCallback callback
    );

    /** Deletes the contact at contactUri (and its photo, if any). */
    void deleteContact(
        String webId,
        String addressBookUri,
        String contactUri,
        IASSContactModuleSolidContactCallback callback
    );

    /** Sets the contact's photo. Subject to the ~1 MB Binder transaction limit. */
    void setContactPhoto(
        String webId,
        String contactUri,
        in byte[] photo,
        String contentType,
        IASSContactModuleSolidContactCallback callback
    );

    /** Removes the contact's photo. */
    void removeContactPhoto(
        String webId,
        String contactUri,
        IASSContactModuleSolidContactCallback callback
    );

    /** Reads a contact photo's bytes. Subject to the ~1 MB Binder transaction limit. */
    void getContactPhoto(
        String webId,
        String photoUri,
        IASSContactModuleContactPhotoCallback callback
    );

    /** Finds a contact by exact WebID — the duplicate check before adding someone. */
    void findContactByWebId(
        String webId,
        String targetWebId,
        IASSContactModuleContactMatchCallback callback
    );

    // ---- Groups ----

    /** Creates a group titled title, optionally seeded with contactUris. */
    void createGroup(
        String webId,
        String addressBookUri,
        String title,
        in List<String> contactUris,
        IASSContactModuleFullGroupCallback callback
    );

    /** Reads the group at groupUri. */
    void getGroup(String webId, String groupUri, IASSContactModuleFullGroupCallback callback);

    /** Deletes the group at groupUri. */
    void deleteGroup(
        String webId,
        String addressBookUri,
        String groupUri,
        IASSContactModuleFullGroupCallback callback
    );

    /** Adds the contact at contactUri to the group at groupUri. */
    void addGroupMember(
        String webId,
        String groupUri,
        String contactUri,
        IASSContactModuleFullGroupCallback callback
    );

    /** Removes the contact at contactUri from the group at groupUri. */
    void removeGroupMember(
        String webId,
        String groupUri,
        String contactUri,
        IASSContactModuleFullGroupCallback callback
    );
}
