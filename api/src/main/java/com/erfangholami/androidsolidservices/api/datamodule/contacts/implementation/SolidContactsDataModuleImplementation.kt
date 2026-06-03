package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.CONTACTS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.FullContact
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import java.net.URI

internal class SolidContactsDataModuleImplementation : SolidContactsDataModule {

    companion object {
        @Volatile
        private var INSTANCE: SolidContactsDataModule? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidContactsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModuleImplementation(authenticator).also { INSTANCE = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidContactsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModuleImplementation(resourceManager).also { INSTANCE = it }
            }
        }
    }

    val helper: SolidContactsDataModuleHelper

    private constructor(authenticator: Authenticator) {
        this.helper = SolidContactsDataModuleHelper.getInstance(authenticator)
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.helper = SolidContactsDataModuleHelper.getInstance(resourceManager)
    }

    override suspend fun getAddressBooks(
        ownerWebId: String,
    ): DataModuleResult<AddressBookList> {
        return try {
            val privates = helper.getPrivateAddressBooks(ownerWebId)
            val public = helper.getPublicAddressBooks(ownerWebId)
            DataModuleResult.Success(
                AddressBookList(
                    publicAddressBookUris = public,
                    privateAddressBookUris = privates
                )
            )
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun createAddressBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String,
        container: String?
    ): DataModuleResult<AddressBook> {
        val newContainer = container ?: "${storage}${CONTACTS_DIRECTORY_SUFFIX}"
        return try {
            val createdAddressBookUri = helper.createAddressBook(
                ownerWebId,
                title,
                newContainer,
                isPrivate,
            ).getIdentifier().toString()
            getAddressBook(ownerWebId, createdAddressBookUri)
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun getAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<AddressBook> {
        return try {
            val addressBookRdf = helper.getAddressBook(ownerWebId, URI.create(addressBookUri))
            val addressBookContactsRdf = helper.getAddressBookContacts(
                ownerWebId,
                URI.create(addressBookRdf.getNameEmailIndex())
            )
            val addressBookGroupsRdf =
                helper.getAddressBookGroups(ownerWebId, URI.create(addressBookRdf.getGroupsIndex()))
            DataModuleResult.Success(
                AddressBook.createFromRdf(
                    addressBookRdf,
                    addressBookContactsRdf,
                    addressBookGroupsRdf
                )
            )
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun renameAddressBook(
        ownerWebId: String,
        addressBookUri: String,
        newName: String
    ): DataModuleResult<AddressBook> {
        return try {
            helper.renameAddressBook(ownerWebId, addressBookUri, newName)
            return getAddressBook(ownerWebId, addressBookUri)
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun deleteAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<AddressBook> {
        try {
            val addressBook = getAddressBook(ownerWebId, addressBookUri)
            helper.deleteAddressBook(ownerWebId, addressBookUri)
            return addressBook
        } catch (e: Exception) {
            return DataModuleResult.Error(e.message)
        }
    }

    override suspend fun createNewContact(
        ownerWebId: String,
        addressBookString: String,
        newContact: NewContact,
        groupStrings: List<String>
    ): DataModuleResult<FullContact> {
        return try {
            val newContact =
                helper.createContact(ownerWebId, URI.create(addressBookString), newContact)

            groupStrings.forEach {
                helper.addContactToGroup(ownerWebId, newContact, URI.create(it))
            }
            return DataModuleResult.Success(FullContact.createFromRdf(newContact))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun getContact(
        ownerWebId: String,
        contactString: String
    ): DataModuleResult<FullContact> {
        return try {
            val contactRdf = helper.getContact(ownerWebId, URI.create(contactString))
            DataModuleResult.Success(FullContact.createFromRdf(contactRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun renameContact(
        ownerWebId: String,
        contactString: String,
        newName: String
    ): DataModuleResult<FullContact> {
        return try {
            val contactRdf = helper.renameContact(ownerWebId, contactString, newName)
            return DataModuleResult.Success(FullContact.createFromRdf(contactRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun addNewPhoneNumber(
        ownerWebId: String,
        contactString: String,
        newPhoneNumber: String
    ): DataModuleResult<FullContact> {
        return try {
            val contactRdf =
                helper.addNewPhoneNumber(ownerWebId, URI.create(contactString), newPhoneNumber)
            DataModuleResult.Success(FullContact.createFromRdf(contactRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun addNewEmailAddress(
        ownerWebId: String,
        contactString: String,
        newEmailAddress: String
    ): DataModuleResult<FullContact> {
        return try {
            val contactRdf =
                helper.addNewEmailAddress(ownerWebId, URI.create(contactString), newEmailAddress)
            DataModuleResult.Success(FullContact.createFromRdf(contactRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun removePhoneNumber(
        ownerWebId: String,
        contactString: String,
        phoneNumber: String
    ): DataModuleResult<FullContact> {
        return try {
            val contactRdf = helper.removePhoneNumberFromContact(
                ownerWebId,
                URI.create(contactString),
                phoneNumber
            )
            DataModuleResult.Success(FullContact.createFromRdf(contactRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun removeEmailAddress(
        ownerWebId: String,
        contactString: String,
        emailAddress: String
    ): DataModuleResult<FullContact> {
        return try {
            val contactRdf = helper.removeEmailAddressFromContact(
                ownerWebId,
                URI.create(contactString),
                emailAddress
            )
            DataModuleResult.Success(FullContact.createFromRdf(contactRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun deleteContact(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String
    ): DataModuleResult<FullContact> {
        return try {
            val contact = getContact(ownerWebId, contactUri)
            helper.deleteContact(ownerWebId, addressBookUri, contactUri)
            contact
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun createNewGroup(
        ownerWebId: String,
        addressBookString: String,
        title: String,
        contactUris: List<String>,
    ): DataModuleResult<FullGroup> {
        return try {
            val groupRDF = helper.createGroup(ownerWebId, URI.create(addressBookString), title)

            contactUris.forEach {
                helper.addContactToGroup(ownerWebId, URI.create(it), groupRDF)
            }

            return getGroup(ownerWebId, groupRDF.getIdentifier().toString())
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun getGroup(
        ownerWebId: String,
        groupString: String
    ): DataModuleResult<FullGroup> {
        return try {
            val groupRdf = helper.getGroup(ownerWebId, URI.create(groupString))
            DataModuleResult.Success(FullGroup.createFromRdf(groupRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun deleteGroup(
        ownerWebId: String,
        addressBookString: String,
        groupString: String
    ): DataModuleResult<FullGroup> {
        return try {
            val groupRdf = helper.removeGroup(
                ownerWebId,
                URI.create(addressBookString),
                URI.create(groupString)
            )
            DataModuleResult.Success(FullGroup.createFromRdf(groupRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun addContactToGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String
    ): DataModuleResult<FullGroup> {
        return try {
            val groupRdf = helper.addContactToGroup(
                ownerWebId,
                URI.create(contactString),
                URI.create(groupString)
            )
            DataModuleResult.Success(FullGroup.createFromRdf(groupRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }

    override suspend fun removeContactFromGroup(
        ownerWebId: String,
        contactString: String,
        groupString: String
    ): DataModuleResult<FullGroup> {
        return try {
            val groupRdf = helper.removeContactFromGroup(ownerWebId, contactString, groupString)
            DataModuleResult.Success(FullGroup.createFromRdf(groupRdf))
        } catch (e: Exception) {
            DataModuleResult.Exception(e)
        }
    }
}