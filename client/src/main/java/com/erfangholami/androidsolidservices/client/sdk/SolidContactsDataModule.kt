package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_DATA_MODULES_SERVICE
import com.erfangholami.androidsolidservices.client.internal.CallbackBridge
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookListCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullContactCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullGroupCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import kotlinx.coroutines.flow.Flow

/**
 * Client SDK for the Solid Contacts data module: manage address books,
 * contacts and groups stored on the user's pod, following the Solid Contacts
 * model.
 *
 * Calls are delegated over IPC to the Android Solid Services app. Obtain an
 * instance via [Solid.getContactsDataModule]. Collect
 * [contactsDataModuleServiceConnectionState] and wait for `true` before
 * issuing calls. All operations are `suspend` functions, return `null` when
 * the service yields no result, and throw [SolidException] on failure.
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

    /** Returns every address book that belongs to [webId]. */
    public suspend fun getAddressBooks(webId: String): AddressBookList? =
        addressBookList { contacts, cb -> contacts.getAddressBooks(webId, cb) }

    /**
     * Creates a new address book titled [title] on the user's pod.
     *
     * @param isPrivate When `true`, the address book is owner-only; when
     *   `false`, it is publicly readable.
     * @param storage Optional pod storage (root) URL to create under; when
     *   `null`, the user's default storage is used.
     * @param container Optional container URI to create the address book in.
     */
    public suspend fun createAddressBook(
        webId: String,
        title: String,
        isPrivate: Boolean = true,
        storage: String? = null,
        ownerWebId: String? = null,
        container: String? = null,
    ): AddressBook? = addressBook { contacts, cb ->
        contacts.createAddressBook(webId, title, isPrivate, cb, storage, ownerWebId, container)
    }

    /** Reads the address book at [uri]. */
    public suspend fun getAddressBook(webId: String, uri: String): AddressBook? =
        addressBook { contacts, cb -> contacts.getAddressBook(webId, uri, cb) }

    /** Deletes the address book at [addressBookUri] and returns the removed address book. */
    public suspend fun deleteAddressBook(webId: String, addressBookUri: String): AddressBook? =
        addressBook { contacts, cb -> contacts.deleteAddressBook(webId, addressBookUri, null, cb) }

    /** Creates [newContact] in the address book at [addressBookUri], optionally adding it to the groups in [groupUris]. */
    public suspend fun createNewContact(
        webId: String,
        addressBookUri: String,
        newContact: NewContact,
        groupUris: List<String> = emptyList(),
    ): FullContact? = fullContact { contacts, cb ->
        contacts.createNewContact(webId, addressBookUri, newContact, groupUris, cb)
    }

    /** Reads the full contact at [contactUri], including its names, e-mail addresses and phone numbers. */
    public suspend fun getContact(webId: String, contactUri: String): FullContact? =
        fullContact { contacts, cb -> contacts.getContact(webId, contactUri, cb) }

    /** Updates the formatted name of the contact at [contactUri] to [newName]. */
    public suspend fun renameContact(webId: String, contactUri: String, newName: String): FullContact? =
        fullContact { contacts, cb -> contacts.renameContact(webId, contactUri, newName, cb) }

    /** Adds [newPhoneNumber] to the contact at [contactUri]. */
    public suspend fun addNewPhoneNumber(webId: String, contactUri: String, newPhoneNumber: String): FullContact? =
        fullContact { contacts, cb -> contacts.addNewPhoneNumber(webId, contactUri, newPhoneNumber, cb) }

    /** Adds [newEmailAddress] to the contact at [contactUri]. */
    public suspend fun addNewEmailAddress(webId: String, contactUri: String, newEmailAddress: String): FullContact? =
        fullContact { contacts, cb -> contacts.addNewEmailAddress(webId, contactUri, newEmailAddress, cb) }

    /** Removes [phoneNumber] from the contact at [contactUri]. */
    public suspend fun removePhoneNumber(webId: String, contactUri: String, phoneNumber: String): FullContact? =
        fullContact { contacts, cb -> contacts.removePhoneNumber(webId, contactUri, phoneNumber, cb) }

    /** Removes [emailAddress] from the contact at [contactUri]. */
    public suspend fun removeEmailAddress(webId: String, contactUri: String, emailAddress: String): FullContact? =
        fullContact { contacts, cb -> contacts.removeEmailAddress(webId, contactUri, emailAddress, cb) }

    /** Deletes the contact at [contactUri] from the address book at [addressBookUri]. */
    public suspend fun deleteContact(webId: String, addressBookUri: String, contactUri: String): FullContact? =
        fullContact { contacts, cb -> contacts.deleteContact(webId, addressBookUri, contactUri, cb) }

    /** Creates a new group titled [title] in the address book at [addressBookUri], optionally seeding it with [contactUris]. */
    public suspend fun createNewGroup(
        webId: String,
        addressBookUri: String,
        title: String,
        contactUris: List<String> = emptyList(),
    ): FullGroup? = fullGroup { contacts, cb ->
        contacts.createNewGroup(webId, addressBookUri, title, contactUris, cb)
    }

    /** Reads the full group at [groupUri], including its member contacts. */
    public suspend fun getGroup(webId: String, groupUri: String): FullGroup? =
        fullGroup { contacts, cb -> contacts.getGroup(webId, groupUri, cb) }

    /** Deletes the group at [groupUri] from the address book at [addressBookUri]. */
    public suspend fun deleteGroup(webId: String, addressBookUri: String, groupUri: String): FullGroup? =
        fullGroup { contacts, cb -> contacts.deleteGroup(webId, addressBookUri, groupUri, cb) }

    /** Adds the contact at [contactUri] to the group at [groupUri]. */
    public suspend fun addContactToGroup(webId: String, contactUri: String, groupUri: String): FullGroup? =
        fullGroup { contacts, cb -> contacts.addContactToGroup(webId, contactUri, groupUri, cb) }

    /** Removes the contact at [contactUri] from the group at [groupUri]. */
    public suspend fun removeContactFromGroup(webId: String, contactUri: String, groupUri: String): FullGroup? =
        fullGroup { contacts, cb -> contacts.removeContactFromGroup(webId, contactUri, groupUri, cb) }

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

    private suspend fun fullContact(
        call: (IASSContactsModuleInterface, IASSContactModuleFullContactCallback) -> Unit,
    ): FullContact? = connector.await { contacts, bridge ->
        call(contacts, object : IASSContactModuleFullContactCallback.Stub() {
            override fun onResult(fullContact: FullContact?) = bridge.onResult(fullContact)
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
