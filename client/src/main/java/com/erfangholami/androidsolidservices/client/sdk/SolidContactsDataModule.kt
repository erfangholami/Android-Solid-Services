package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_DATA_MODULES_SERVICE
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookListCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleContactMatchCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleContactPhotoCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullGroupCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleSolidContactCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleSolidContactListCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList
import kotlinx.coroutines.flow.Flow

/**
 * Client SDK for the Solid Contacts data module: address books, contacts and groups stored
 * on the user's pod.
 *
 * The surface mirrors the in-process module — three role stores, [books], [contacts] and
 * [groups] — and speaks the same write model, the immutable [ContactData] (full vCard 4.0
 * coverage), so the same code shape works against either SDK.
 *
 * Calls are delegated over IPC to the Android Solid Services app, which owns the login and
 * the tokens. Obtain an instance via [Solid.getContactsDataModule]. Collect
 * [contactsDataModuleServiceConnectionState] and wait for `true` before issuing calls. All
 * operations are `suspend` functions, return `null` when the service yields no result, and
 * throw [SolidException] on failure.
 */
public class SolidContactsDataModule private constructor(context: Context) {

    public companion object {
        @Volatile
        private var INSTANCE: SolidContactsDataModule? = null

        public fun getInstance(context: Context): SolidContactsDataModule =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModule(context).also { INSTANCE = it }
            }
    }

    private val connector = ServiceConnector(
        context,
        ANDROID_SOLID_SERVICES_DATA_MODULES_SERVICE,
    ) { binder -> IASSDataModulesService.Stub.asInterface(binder).contactsDataModuleInterface }

    /** Hot [Flow] of the IPC service connection state; emits `true` once connected. */
    public fun contactsDataModuleServiceConnectionState(): Flow<Boolean> = connector.connectionState

    /** Address books — the containers that hold contacts and groups. */
    public val books: AddressBooks = AddressBooks()

    /** The contacts inside an address book. */
    public val contacts: Contacts = Contacts()

    /** The groups inside an address book. */
    public val groups: Groups = Groups()

    /** Address-book operations. Reached via [books]. */
    public inner class AddressBooks internal constructor() {

        /** Returns every address book that belongs to [webId]. */
        public suspend fun list(webId: String): AddressBookList? =
            addressBookList { c, cb -> c.listAddressBooks(webId, cb) }

        /** Bootstraps the address-book container if absent, then returns the books in it. */
        public suspend fun ensureContainer(
            webId: String,
            storage: String? = null,
            container: String? = null,
        ): AddressBookList? =
            addressBookList { c, cb -> c.ensureAddressBookContainer(webId, storage, container, cb) }

        /** Reads the address book at [addressBookUri]. */
        public suspend fun get(webId: String, addressBookUri: String): AddressBook? =
            addressBook { c, cb -> c.getAddressBook(webId, addressBookUri, cb) }

        /**
         * Creates an address book titled [title].
         *
         * @param isPrivate When `true` (default) it is registered in the private type index;
         *   when `false`, in the public one.
         * @param storage Optional pod storage (root) URL; when `null` the default is used.
         * @param container Optional container URI to create the address book in.
         */
        public suspend fun create(
            webId: String,
            title: String,
            isPrivate: Boolean = true,
            storage: String? = null,
            container: String? = null,
        ): AddressBook? = addressBook { c, cb ->
            c.createAddressBook(webId, title, isPrivate, storage, container, cb)
        }

        /** Renames the address book at [addressBookUri]. */
        public suspend fun rename(
            webId: String,
            addressBookUri: String,
            newName: String,
        ): AddressBook? = addressBook { c, cb ->
            c.renameAddressBook(webId, addressBookUri, newName, cb)
        }

        /** Deletes the address book at [addressBookUri] and everything in it. */
        public suspend fun delete(webId: String, addressBookUri: String): AddressBook? =
            addressBook { c, cb -> c.deleteAddressBook(webId, addressBookUri, cb) }

        /** Returns the user's default address book, creating it (titled [title]) if absent. */
        public suspend fun ensureDefault(
            webId: String,
            storage: String? = null,
            title: String = "Contacts",
        ): AddressBook? = addressBook { c, cb ->
            c.ensureDefaultAddressBook(webId, storage, title, cb)
        }
    }

    /** Contact operations. Reached via [contacts]. */
    public inner class Contacts internal constructor() {

        /** Reads the contact at [contactUri]. */
        public suspend fun get(webId: String, contactUri: String): SolidContact? =
            solidContact { c, cb -> c.getContact(webId, contactUri, cb) }

        /** Lists the contacts in the address book at [addressBookUri]. */
        public suspend fun list(webId: String, addressBookUri: String): SolidContactList? =
            solidContactList { c, cb -> c.listContacts(webId, addressBookUri, cb) }

        /** Creates a contact from [data], optionally adding it to [groupUris]. */
        public suspend fun create(
            webId: String,
            addressBookUri: String,
            data: ContactData,
            groupUris: List<String> = emptyList(),
        ): SolidContact? = solidContact { c, cb ->
            c.createContact(webId, addressBookUri, data, groupUris, cb)
        }

        /**
         * Rewrites the contact at [contactUri] from [data] with replace semantics: properties
         * absent from [data] are removed.
         */
        public suspend fun update(
            webId: String,
            addressBookUri: String,
            contactUri: String,
            data: ContactData,
        ): SolidContact? = solidContact { c, cb ->
            c.updateContact(webId, addressBookUri, contactUri, data, cb)
        }

        /** Deletes the contact at [contactUri] (and its photo, if any). */
        public suspend fun delete(
            webId: String,
            addressBookUri: String,
            contactUri: String,
        ): SolidContact? = solidContact { c, cb ->
            c.deleteContact(webId, addressBookUri, contactUri, cb)
        }

        /**
         * Sets the contact's photo.
         *
         * The bytes travel inline over Binder, so this is subject to the ~1 MB transaction
         * limit; a larger image will fail the call rather than be truncated.
         */
        public suspend fun setPhoto(
            webId: String,
            contactUri: String,
            photo: ByteArray,
            contentType: String,
        ): SolidContact? = solidContact { c, cb ->
            c.setContactPhoto(webId, contactUri, photo, contentType, cb)
        }

        /** Removes the contact's photo. */
        public suspend fun removePhoto(webId: String, contactUri: String): SolidContact? =
            solidContact { c, cb -> c.removeContactPhoto(webId, contactUri, cb) }

        /** Reads a contact photo's bytes. Subject to the ~1 MB Binder transaction limit. */
        public suspend fun getPhoto(webId: String, photoUri: String): ContactPhoto? =
            contactPhoto { c, cb -> c.getContactPhoto(webId, photoUri, cb) }

        /** Finds a contact by exact WebID — the duplicate check before adding someone. */
        public suspend fun findByWebId(webId: String, targetWebId: String): ContactMatch? =
            contactMatch { c, cb -> c.findContactByWebId(webId, targetWebId, cb) }
    }

    /** Group operations. Reached via [groups]. */
    public inner class Groups internal constructor() {

        /** Creates a group titled [title], optionally seeded with [contactUris]. */
        public suspend fun create(
            webId: String,
            addressBookUri: String,
            title: String,
            contactUris: List<String> = emptyList(),
        ): FullGroup? = fullGroup { c, cb ->
            c.createGroup(webId, addressBookUri, title, contactUris, cb)
        }

        /** Reads the group at [groupUri]. */
        public suspend fun get(webId: String, groupUri: String): FullGroup? =
            fullGroup { c, cb -> c.getGroup(webId, groupUri, cb) }

        /** Deletes the group at [groupUri]. */
        public suspend fun delete(
            webId: String,
            addressBookUri: String,
            groupUri: String,
        ): FullGroup? = fullGroup { c, cb ->
            c.deleteGroup(webId, addressBookUri, groupUri, cb)
        }

        /** Adds the contact at [contactUri] to the group at [groupUri]. */
        public suspend fun addMember(
            webId: String,
            groupUri: String,
            contactUri: String,
        ): FullGroup? = fullGroup { c, cb -> c.addGroupMember(webId, groupUri, contactUri, cb) }

        /** Removes the contact at [contactUri] from the group at [groupUri]. */
        public suspend fun removeMember(
            webId: String,
            groupUri: String,
            contactUri: String,
        ): FullGroup? = fullGroup { c, cb -> c.removeGroupMember(webId, groupUri, contactUri, cb) }
    }

    private suspend fun addressBookList(
        call: (IASSContactsModuleInterface, IASSContactModuleAddressBookListCallback) -> Unit,
    ): AddressBookList? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleAddressBookListCallback.Stub() {
            override fun onResult(addressBookList: AddressBookList?) = bridge.onResult(addressBookList)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    private suspend fun addressBook(
        call: (IASSContactsModuleInterface, IASSContactModuleAddressBookCallback) -> Unit,
    ): AddressBook? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleAddressBookCallback.Stub() {
            override fun onResult(addressBook: AddressBook?) = bridge.onResult(addressBook)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    private suspend fun solidContact(
        call: (IASSContactsModuleInterface, IASSContactModuleSolidContactCallback) -> Unit,
    ): SolidContact? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleSolidContactCallback.Stub() {
            override fun onResult(contact: SolidContact?) = bridge.onResult(contact)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    private suspend fun solidContactList(
        call: (IASSContactsModuleInterface, IASSContactModuleSolidContactListCallback) -> Unit,
    ): SolidContactList? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleSolidContactListCallback.Stub() {
            override fun onResult(contactList: SolidContactList?) = bridge.onResult(contactList)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    private suspend fun contactPhoto(
        call: (IASSContactsModuleInterface, IASSContactModuleContactPhotoCallback) -> Unit,
    ): ContactPhoto? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleContactPhotoCallback.Stub() {
            override fun onResult(photo: ContactPhoto?) = bridge.onResult(photo)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    private suspend fun contactMatch(
        call: (IASSContactsModuleInterface, IASSContactModuleContactMatchCallback) -> Unit,
    ): ContactMatch? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleContactMatchCallback.Stub() {
            override fun onResult(match: ContactMatch?) = bridge.onResult(match)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }

    private suspend fun fullGroup(
        call: (IASSContactsModuleInterface, IASSContactModuleFullGroupCallback) -> Unit,
    ): FullGroup? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleFullGroupCallback.Stub() {
            override fun onResult(fullGroup: FullGroup?) = bridge.onResult(fullGroup)
            override fun onError(errorCode: Int, errorMessage: String) = bridge.onError(errorCode, errorMessage)
        })
    }
}
