package com.erfangholami.androidsolidservices.domain.repository

import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact

interface ContactsRepository {

    suspend fun getAddressBooks(ownerWebId: String): SolidResult<AddressBookList>

    suspend fun createAddressBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean = true,
        storage: String,
        container: String? = null,
    ): SolidResult<AddressBook>

    suspend fun getAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook>

    suspend fun deleteAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook>

    suspend fun createNewContact(
        ownerWebId: String,
        addressBookString: String,
        newContact: NewContact,
        groupStrings: List<String> = emptyList(),
    ): SolidResult<FullContact>

    suspend fun getContact(ownerWebId: String, contactString: String): SolidResult<FullContact>

    suspend fun renameContact(
        ownerWebId: String,
        contactString: String,
        newName: String,
    ): SolidResult<FullContact>

    suspend fun addNewPhoneNumber(
        ownerWebId: String,
        contactString: String,
        newPhoneNumber: String,
    ): SolidResult<FullContact>

    suspend fun addNewEmailAddress(
        ownerWebId: String,
        contactString: String,
        newEmailAddress: String,
    ): SolidResult<FullContact>

    suspend fun removePhoneNumber(
        ownerWebId: String,
        contactString: String,
        phoneNumber: String,
    ): SolidResult<FullContact>

    suspend fun removeEmailAddress(
        ownerWebId: String,
        contactString: String,
        emailAddress: String,
    ): SolidResult<FullContact>

    suspend fun deleteContact(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): SolidResult<FullContact>

    suspend fun createNewGroup(
        ownerWebId: String,
        addressBookString: String,
        title: String,
        contactUris: List<String> = emptyList(),
    ): SolidResult<FullGroup>

    suspend fun getGroup(ownerWebId: String, groupString: String): SolidResult<FullGroup>

    suspend fun deleteGroup(
        ownerWebId: String,
        addressBookString: String,
        groupString: String,
    ): SolidResult<FullGroup>

    suspend fun addContactToGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): SolidResult<FullGroup>

    suspend fun removeContactFromGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String,
    ): SolidResult<FullGroup>
}
