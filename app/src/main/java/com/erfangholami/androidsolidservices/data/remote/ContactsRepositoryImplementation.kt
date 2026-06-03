package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.domain.repository.ContactsRepository
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepositoryImplementation @Inject constructor(
    private val contactsDataModule: SolidContactsDataModule,
) : ContactsRepository {

    override suspend fun getAddressBooks(ownerWebId: String): DataModuleResult<AddressBookList> =
        contactsDataModule.getAddressBooks(ownerWebId)

    override suspend fun createAddressBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String,
        container: String?,
    ): DataModuleResult<AddressBook> =
        contactsDataModule.createAddressBook(ownerWebId, title, isPrivate, storage, container)

    override suspend fun getAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<AddressBook> = contactsDataModule.getAddressBook(ownerWebId, addressBookUri)

    override suspend fun deleteAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<AddressBook> =
        contactsDataModule.deleteAddressBook(ownerWebId, addressBookUri)

    override suspend fun createNewContact(
        ownerWebId: String,
        addressBookString: String,
        newContact: NewContact,
        groupStrings: List<String>,
    ): DataModuleResult<FullContact> =
        contactsDataModule.createNewContact(ownerWebId, addressBookString, newContact, groupStrings)

    override suspend fun getContact(
        ownerWebId: String,
        contactString: String,
    ): DataModuleResult<FullContact> = contactsDataModule.getContact(ownerWebId, contactString)

    override suspend fun renameContact(
        ownerWebId: String,
        contactString: String,
        newName: String,
    ): DataModuleResult<FullContact> =
        contactsDataModule.renameContact(ownerWebId, contactString, newName)

    override suspend fun addNewPhoneNumber(
        ownerWebId: String,
        contactString: String,
        newPhoneNumber: String,
    ): DataModuleResult<FullContact> =
        contactsDataModule.addNewPhoneNumber(ownerWebId, contactString, newPhoneNumber)

    override suspend fun addNewEmailAddress(
        ownerWebId: String,
        contactString: String,
        newEmailAddress: String,
    ): DataModuleResult<FullContact> =
        contactsDataModule.addNewEmailAddress(ownerWebId, contactString, newEmailAddress)

    override suspend fun removePhoneNumber(
        ownerWebId: String,
        contactString: String,
        phoneNumber: String,
    ): DataModuleResult<FullContact> =
        contactsDataModule.removePhoneNumber(ownerWebId, contactString, phoneNumber)

    override suspend fun removeEmailAddress(
        ownerWebId: String,
        contactString: String,
        emailAddress: String,
    ): DataModuleResult<FullContact> =
        contactsDataModule.removeEmailAddress(ownerWebId, contactString, emailAddress)

    override suspend fun deleteContact(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): DataModuleResult<FullContact> =
        contactsDataModule.deleteContact(ownerWebId, addressBookUri, contactUri)

    override suspend fun createNewGroup(
        ownerWebId: String,
        addressBookString: String,
        title: String,
        contactUris: List<String>,
    ): DataModuleResult<FullGroup> =
        contactsDataModule.createNewGroup(ownerWebId, addressBookString, title, contactUris)

    override suspend fun getGroup(
        ownerWebId: String,
        groupString: String,
    ): DataModuleResult<FullGroup> = contactsDataModule.getGroup(ownerWebId, groupString)

    override suspend fun deleteGroup(
        ownerWebId: String,
        addressBookString: String,
        groupString: String,
    ): DataModuleResult<FullGroup> =
        contactsDataModule.deleteGroup(ownerWebId, addressBookString, groupString)

    override suspend fun addContactToGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): DataModuleResult<FullGroup> =
        contactsDataModule.addContactToGroup(ownerWebId, contactString, groupString)

    override suspend fun removeContactFromGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): DataModuleResult<FullGroup> =
        contactsDataModule.removeContactFromGroup(ownerWebId, contactString, groupString)
}
