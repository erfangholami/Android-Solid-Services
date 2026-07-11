package com.erfangholami.androidsolidservices.data.remote

import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.domain.repository.ContactsRepository
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import com.erfangholami.androidsolidservices.shared.model.contacts.addressBookUriForContact
import com.erfangholami.androidsolidservices.shared.model.contacts.buildUpon
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepositoryImplementation @Inject constructor(
    private val contactsDataModule: SolidContactsDataModule,
) : ContactsRepository {

    override suspend fun getAddressBooks(ownerWebId: String): SolidResult<AddressBookList> =
        contactsDataModule.books.list(ownerWebId)

    override suspend fun createAddressBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String,
        container: String?,
    ): SolidResult<AddressBook> =
        contactsDataModule.books.create(ownerWebId, title, isPrivate, storage, container)

    override suspend fun getAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook> = contactsDataModule.books.get(ownerWebId, addressBookUri)

    override suspend fun deleteAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook> = contactsDataModule.books.delete(ownerWebId, addressBookUri)

    override suspend fun createNewContact(
        ownerWebId: String,
        addressBookString: String,
        newContact: NewContact,
        groupStrings: List<String>,
    ): SolidResult<FullContact> =
        contactsDataModule.contacts
            .create(ownerWebId, addressBookString, newContact.toContactData(), groupStrings)
            .map { it.toFullContact() }

    override suspend fun getContact(
        ownerWebId: String,
        contactString: String,
    ): SolidResult<FullContact> =
        contactsDataModule.contacts.get(ownerWebId, contactString).map { it.toFullContact() }

    override suspend fun renameContact(
        ownerWebId: String,
        contactString: String,
        newName: String,
    ): SolidResult<FullContact> = withContactUpdate(ownerWebId, contactString) { data ->
        val currentName = data.fullName ?: data.effectiveFullName()
        if (currentName == newName) null else data.copy(fullName = newName)
    }

    override suspend fun addNewPhoneNumber(
        ownerWebId: String,
        contactString: String,
        newPhoneNumber: String,
    ): SolidResult<FullContact> = withContactUpdate(ownerWebId, contactString) { data ->
        if (data.phones.any { it.number == newPhoneNumber }) null
        else data.buildUpon { phone(newPhoneNumber) }
    }

    override suspend fun addNewEmailAddress(
        ownerWebId: String,
        contactString: String,
        newEmailAddress: String,
    ): SolidResult<FullContact> = withContactUpdate(ownerWebId, contactString) { data ->
        if (data.emails.any { it.address == newEmailAddress }) null
        else data.buildUpon { email(newEmailAddress) }
    }

    override suspend fun removePhoneNumber(
        ownerWebId: String,
        contactString: String,
        phoneNumber: String,
    ): SolidResult<FullContact> = withContactUpdate(ownerWebId, contactString) { data ->
        if (data.phones.none { it.number == phoneNumber }) null
        else data.copy(phones = data.phones.filterNot { it.number == phoneNumber })
    }

    override suspend fun removeEmailAddress(
        ownerWebId: String,
        contactString: String,
        emailAddress: String,
    ): SolidResult<FullContact> = withContactUpdate(ownerWebId, contactString) { data ->
        if (data.emails.none { it.address == emailAddress }) null
        else data.copy(emails = data.emails.filterNot { it.address == emailAddress })
    }

    override suspend fun deleteContact(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): SolidResult<FullContact> =
        contactsDataModule.contacts
            .delete(ownerWebId, addressBookUri, contactUri)
            .map { it.toFullContact() }

    override suspend fun createNewGroup(
        ownerWebId: String,
        addressBookString: String,
        title: String,
        contactUris: List<String>,
    ): SolidResult<FullGroup> =
        contactsDataModule.groups.create(ownerWebId, addressBookString, title, contactUris)

    override suspend fun getGroup(
        ownerWebId: String,
        groupString: String,
    ): SolidResult<FullGroup> = contactsDataModule.groups.get(ownerWebId, groupString)

    override suspend fun deleteGroup(
        ownerWebId: String,
        addressBookString: String,
        groupString: String,
    ): SolidResult<FullGroup> =
        contactsDataModule.groups.delete(ownerWebId, addressBookString, groupString)

    override suspend fun addContactToGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): SolidResult<FullGroup> =
        contactsDataModule.groups.addMember(ownerWebId, groupString, contactString)

    override suspend fun removeContactFromGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): SolidResult<FullGroup> =
        contactsDataModule.groups.removeMember(ownerWebId, groupString, contactString)

    private suspend fun withContactUpdate(
        ownerWebId: String,
        contactUri: String,
        mutate: (ContactData) -> ContactData?,
    ): SolidResult<FullContact> {
        val contact = when (val current = contactsDataModule.contacts.get(ownerWebId, contactUri)) {
            is SolidResult.Success -> current.value
            is SolidResult.Failure -> return SolidResult.Failure(current.error)
        }
        val updated = mutate(contact.data)
            ?: return SolidResult.Success(contact.toFullContact())
        val addressBookUri = addressBookUriForContact(contactUri)
            ?: return SolidResult.Failure(
                SolidError.Unknown("Cannot derive the address book for $contactUri"),
            )
        return contactsDataModule.contacts
            .update(ownerWebId, addressBookUri, contactUri, updated)
            .map { it.toFullContact() }
    }
}
