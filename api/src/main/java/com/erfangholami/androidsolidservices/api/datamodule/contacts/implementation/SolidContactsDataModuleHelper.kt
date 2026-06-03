package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.model.contacts.GROUPS_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.GROUP_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import com.erfangholami.androidsolidservices.shared.model.contacts.PEOPLE_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.PEOPLE_FILE_NAME
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import java.net.URI
import java.util.UUID

internal class SolidContactsDataModuleHelper {

    companion object {
        @Volatile
        private var INSTANCE: SolidContactsDataModuleHelper? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidContactsDataModuleHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModuleHelper(authenticator).also { INSTANCE = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidContactsDataModuleHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModuleHelper(resourceManager).also { INSTANCE = it }
            }
        }
    }

    val solidResourceManager: SolidResourceManager

    private constructor(authenticator: Authenticator) {
        this.solidResourceManager = SolidResourceManager.getInstance(authenticator)
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.solidResourceManager = resourceManager
    }

    suspend fun createAddressBook(
        ownerWebId: String,
        title: String,
        container: String,
        isPrivate: Boolean = true,
    ): AddressBookRDF {

        val id: String = UUID.randomUUID().toString()
        val uri = "${container}${id}/${INDEX_FILE_NAME}"
        val nameEmailIndex = "${container}${id}/${PEOPLE_FILE_NAME}"
        val groupIndex = "${container}${id}/${GROUPS_FILE_NAME}"

        val nemEmailIndex = NameEmailIndexRDF(
            identifier = URI.create(nameEmailIndex),
            contentType = "application/ld+json",
            quads = null,
            headers = null
        )

        val groupsIndexRDF = GroupsIndexRDF(
            identifier = URI.create(groupIndex),
            contentType = "application/ld+json",
            quads = null,
            headers = null
        )

        val addressBook = AddressBookRDF(
            identifier = URI.create(uri),
            contentType = "application/ld+json",
            quads = null,
            headers = null
        ).apply {
            setOwner(ownerWebId)
            setTitle(title)
            setNameEmailIndex(nameEmailIndex)
            setGroupsIndex(groupIndex)
        }

        solidResourceManager.create(ownerWebId, nemEmailIndex).getOrThrow()
        solidResourceManager.create(ownerWebId, groupsIndexRDF).getOrThrow()
        val newCreatedAddressBook =
            solidResourceManager.create(ownerWebId, addressBook).getOrThrow()
        updateTypeIndex(ownerWebId, newCreatedAddressBook.getIdentifier(), isPrivate)
        return newCreatedAddressBook
    }

    private suspend fun updateTypeIndex(
        ownerWebId: String,
        addressBookUri: URI,
        isPrivate: Boolean = true
    ) {
        if (isPrivate) {
            val privateTypeIndex = getPrivateTypeIndex(ownerWebId)
            privateTypeIndex.addAddressBook(addressBookUri.toString())
            solidResourceManager.update(ownerWebId, privateTypeIndex).getOrThrow()
        } else {
            val publicTypeIndex = getPublicTypeIndex(ownerWebId)
            publicTypeIndex.addAddressBook(addressBookUri.toString())
            solidResourceManager.update(ownerWebId, publicTypeIndex).getOrThrow()
        }
    }

    suspend fun getAddressBook(
        ownerWebId: String,
        uri: URI
    ): AddressBookRDF {
        return solidResourceManager.read(ownerWebId, uri, AddressBookRDF::class.java).getOrThrow()
    }

    suspend fun renameAddressBook(
        ownerWebId: String,
        addressBookUri: String,
        newName: String
    ): AddressBookRDF {
        val addressBookRdf = getAddressBook(ownerWebId, URI.create(addressBookUri))
        if (addressBookRdf.getTitle() != newName) {
            addressBookRdf.setTitle(newName)
            solidResourceManager.update(ownerWebId, addressBookRdf)

        }
        return addressBookRdf

    }

    suspend fun deleteAddressBook(
        ownerWebId: String,
        addressBookUri: String,
    ): AddressBookRDF {
        val addressBookRDF = getAddressBook(ownerWebId, URI.create(addressBookUri))

        val privateTypeIndex = getPrivateTypeIndex(ownerWebId)
        val publicTypeIndex = getPublicTypeIndex(ownerWebId)
        if (privateTypeIndex.containsAddressBook(addressBookUri)) {
            privateTypeIndex.removeAddressBook(addressBookUri)
            solidResourceManager.update(ownerWebId, privateTypeIndex).getOrThrow()
        } else if (publicTypeIndex.containsAddressBook(addressBookUri)) {
            publicTypeIndex.removeAddressBook(addressBookUri)
            solidResourceManager.update(ownerWebId, publicTypeIndex).getOrThrow()
        }

        val addressBookMainContainer =
            addressBookUri.substring(0, addressBookUri.lastIndexOf("/") + 1)
        solidResourceManager.delete(ownerWebId, URI.create(addressBookMainContainer))
        return addressBookRDF
    }

    suspend fun getAddressBookContacts(
        ownerWebId: String,
        nameEmailIndexUri: URI
    ): NameEmailIndexRDF {
        return solidResourceManager.read(
            ownerWebId,
            nameEmailIndexUri,
            NameEmailIndexRDF::class.java
        ).getOrThrow()
    }

    suspend fun getAddressBookGroups(
        ownerWebId: String,
        groupsIndexUri: URI
    ): GroupsIndexRDF {
        return solidResourceManager.read(ownerWebId, groupsIndexUri, GroupsIndexRDF::class.java)
            .getOrThrow()
    }

    suspend fun createContact(
        ownerWebId: String,
        addressBookUri: URI,
        newContact: NewContact
    ): ContactRDF {
        val contactId = UUID.randomUUID()
        val addressBookContainer =
            addressBookUri.toString().substring(0, addressBookUri.toString().lastIndexOf("/") + 1)
        val contactUri =
            "${addressBookContainer}${PEOPLE_DIRECTORY_SUFFIX}${contactId}/${INDEX_FILE_NAME}"
        val newContactRDF = createSingleContact(ownerWebId, URI.create(contactUri), newContact)
        addContactToAddressBook(ownerWebId, newContactRDF, addressBookUri)
        return newContactRDF
    }

    private suspend fun createSingleContact(
        ownerWebId: String,
        contactUri: URI,
        newContact: NewContact
    ): ContactRDF {
        val newContactRDF = ContactRDF(
            identifier = contactUri,
            contentType = "application/ld+json",
            quads = null,
            headers = null
        ).apply {
            setFullName(newContact.name)
            addPhoneNumber(newContact.phoneNumber)
            addEmailAddress(newContact.email)
        }
        return solidResourceManager.create(ownerWebId, newContactRDF).getOrThrow()
    }

    private suspend fun addContactToAddressBook(
        ownerWebId: String,
        contact: ContactRDF,
        addressBookUri: URI
    ) {
        val addressBookRDF = getAddressBook(ownerWebId, addressBookUri)
        val nameEmailIndexRDF =
            getAddressBookContacts(ownerWebId, URI.create(addressBookRDF.getNameEmailIndex()))
        nameEmailIndexRDF.addContact(addressBookRDF.getIdentifier().toString(), contact)
        solidResourceManager.update(ownerWebId, nameEmailIndexRDF)
    }

    suspend fun addContactToGroup(
        ownerWebId: String,
        contactUri: URI,
        groupUri: URI
    ): GroupRDF {
        return addContactToGroup(ownerWebId, getContact(ownerWebId, contactUri), groupUri)
    }

    suspend fun addContactToGroup(
        ownerWebId: String,
        contact: ContactRDF,
        groupUri: URI
    ): GroupRDF {
        return addContactToGroup(ownerWebId, contact, getGroup(ownerWebId, groupUri))
    }

    suspend fun addContactToGroup(
        ownerWebId: String,
        contactUri: URI,
        group: GroupRDF
    ): GroupRDF {
        return addContactToGroup(ownerWebId, getContact(ownerWebId, contactUri), group)
    }

    suspend fun addContactToGroup(
        ownerWebId: String,
        contact: ContactRDF,
        group: GroupRDF
    ): GroupRDF {
        group.addMember(contact)
        solidResourceManager.update(ownerWebId, group)
        return group
    }

    suspend fun getContact(
        ownerWebId: String,
        contactUri: URI
    ): ContactRDF {
        return solidResourceManager.read(ownerWebId, contactUri, ContactRDF::class.java)
            .getOrThrow()
    }


    suspend fun deleteContact(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String
    ) {
        val addressBookRDF = getAddressBook(ownerWebId, URI.create(addressBookUri))
        val nameEmailIndexRDF =
            getAddressBookContacts(ownerWebId, URI.create(addressBookRDF.getNameEmailIndex()))
        if (nameEmailIndexRDF.removeContact(contactUri)) {
            solidResourceManager.update(ownerWebId, nameEmailIndexRDF)
            val groupsIndexRDF =
                getAddressBookGroups(ownerWebId, URI.create(addressBookRDF.getGroupsIndex()))
            groupsIndexRDF.getGroups(addressBookUri).forEach {
                removeContactFromGroup(ownerWebId, contactUri, it.uri)
            }
        }
        val contactDir = contactUri.substring(0, contactUri.lastIndexOf("/") + 1)
        solidResourceManager.delete(ownerWebId, URI.create(contactDir))

    }

    suspend fun createGroup(
        ownerWebId: String,
        addressBookUri: URI,
        groupName: String
    ): GroupRDF {
        val addressBookContainer =
            addressBookUri.toString().substring(0, addressBookUri.toString().lastIndexOf("/") + 1)
        val groupUri = "${addressBookContainer}${GROUP_DIRECTORY_SUFFIX}${
            groupName.trim().replace(" ", "_")
        }.ttl"
        val group = createSingleGroup(ownerWebId, addressBookUri, URI.create(groupUri), groupName)
        addGroupToAddressBook(ownerWebId, addressBookUri, group)
        return group
    }

    private suspend fun createSingleGroup(
        ownerWebId: String,
        addressBookUri: URI,
        groupUri: URI,
        groupName: String
    ): GroupRDF {
        val groupRdf = GroupRDF(
            identifier = groupUri,
            contentType = "application/ld+json",
            quads = null,
            headers = null
        ).apply {
            setTitle(groupName)
            setIncludesInAddressBook(addressBookUri.toString())
        }
        return solidResourceManager.create(ownerWebId, groupRdf).getOrThrow()
    }

    private suspend fun addGroupToAddressBook(
        ownerWebId: String,
        addressBookUri: URI,
        group: GroupRDF
    ) {
        val addressBookRDF = getAddressBook(ownerWebId, addressBookUri)
        val groupsIndexRDF =
            getAddressBookGroups(ownerWebId, URI.create(addressBookRDF.getGroupsIndex()))
        groupsIndexRDF.addGroup(addressBookRDF.getIdentifier().toString(), group)
        solidResourceManager.update(ownerWebId, groupsIndexRDF)
    }

    suspend fun getGroup(
        ownerWebId: String,
        groupUri: URI
    ): GroupRDF {
        return solidResourceManager.read(ownerWebId, groupUri, GroupRDF::class.java).getOrThrow()
    }

    suspend fun removeContactFromGroup(
        ownerWebId: String,
        contactUri: String,
        groupUri: String
    ): GroupRDF {
        val groupRdf = getGroup(ownerWebId, URI.create(groupUri))
        val result = groupRdf.removeMember(URI.create(contactUri))
        if (result) {
            solidResourceManager.update(ownerWebId, groupRdf).getOrThrow()
        }
        return groupRdf
    }

    suspend fun addNewPhoneNumber(
        ownerWebId: String,
        contactUri: URI,
        newPhoneNumber: String
    ): ContactRDF {
        val contact = getContact(ownerWebId, contactUri)
        val addingResult = contact.addPhoneNumber(newPhoneNumber)
        return if (addingResult) {
            solidResourceManager.update(ownerWebId, contact).getOrThrow()
        } else {
            contact
        }
    }

    suspend fun addNewEmailAddress(
        ownerWebId: String,
        contactUri: URI,
        newEmailAddress: String
    ): ContactRDF {
        val contact = getContact(ownerWebId, contactUri)
        val addingResult = contact.addEmailAddress(newEmailAddress)
        return if (addingResult) {
            solidResourceManager.update(ownerWebId, contact).getOrThrow()
        } else {
            contact
        }
    }

    suspend fun removePhoneNumberFromContact(
        ownerWebId: String,
        contactUri: URI,
        phoneNumber: String
    ): ContactRDF {
        val contactRdf = getContact(ownerWebId, contactUri)
        if (contactRdf.removePhoneNumber(phoneNumber)) {
            solidResourceManager.update(ownerWebId, contactRdf).getOrThrow()
        }
        return contactRdf
    }

    suspend fun removeEmailAddressFromContact(
        ownerWebId: String,
        contactUri: URI,
        emailAddress: String
    ): ContactRDF {
        val contactRdf = getContact(ownerWebId, contactUri)
        if (contactRdf.removeEmailAddress(emailAddress)) {
            solidResourceManager.update(ownerWebId, contactRdf).getOrThrow()
        }
        return contactRdf
    }

    suspend fun removeGroup(
        ownerWebId: String,
        addressBookUri: URI,
        groupUri: URI
    ): GroupRDF {
        val addressBookRDF = getAddressBook(ownerWebId, addressBookUri)
        val groupRdf = getGroup(ownerWebId, groupUri)
        val groupsIndexRDF =
            getAddressBookGroups(ownerWebId, URI.create(addressBookRDF.getGroupsIndex()))
        if (groupsIndexRDF.removeGroup(groupUri)) {
            solidResourceManager.update(ownerWebId, groupsIndexRDF).getOrThrow()
            solidResourceManager.delete(ownerWebId, groupRdf).getOrThrow()
        }
        return groupRdf
    }

    suspend fun getPrivateAddressBooks(webId: String): List<String> {
        return getPrivateTypeIndex(webId).getAddressBooks()
    }

    suspend fun getPublicAddressBooks(webId: String): List<String> {
        return getPublicTypeIndex(webId).getAddressBooks()
    }

    suspend fun renameContact(
        ownerWebId: String,
        contactUri: String,
        newName: String
    ): ContactRDF {
        val contactRdf = getContact(ownerWebId, URI.create(contactUri))
        if (contactRdf.getFullName() != newName) {
            contactRdf.setFullName(newName)
            solidResourceManager.update(ownerWebId, contactRdf)
        }
        return contactRdf
    }


    private suspend fun getPrivateTypeIndex(webIdString: String): PrivateTypeIndex {
        val webId =
            solidResourceManager.read(webIdString, URI.create(webIdString), WebId::class.java)
                .getOrThrow()
        var privateTypeIndexUri = webId.getPrivateTypeIndex()

        if (privateTypeIndexUri == null) {
            val extendedProfile = solidResourceManager.read(
                webIdString,
                webId.getPrimaryTopicDocuments().firstOrNull() ?: URI.create(webIdString),
                WebId::class.java
            ).getOrThrow()
            privateTypeIndexUri = extendedProfile.getPrivateTypeIndex()

            if (privateTypeIndexUri == null) {
                extendedProfile.setPrivateTypeIndex(webIdString, webId.getStorages()[0].toString())
                solidResourceManager.update(webIdString, extendedProfile).getOrThrow()
                privateTypeIndexUri = extendedProfile.getPrivateTypeIndex()
                solidResourceManager.create(
                    webIdString,
                    PrivateTypeIndex(
                        privateTypeIndexUri!!,
                        "application/ld+json",
                        null,
                        null
                    )
                ).getOrThrow()
            }
        }

        return solidResourceManager.read(
            webIdString,
            privateTypeIndexUri,
            PrivateTypeIndex::class.java
        ).getOrThrow()
    }

    private suspend fun getPublicTypeIndex(webIdString: String): PublicTypeIndex {
        val webId =
            solidResourceManager.read(webIdString, URI.create(webIdString), WebId::class.java)
                .getOrThrow()
        var publicTypeIndexUri = webId.getPublicTypeIndex()

        if (publicTypeIndexUri == null) {
            val extendedProfile = solidResourceManager.read(
                webIdString,
                webId.getPrimaryTopicDocuments().firstOrNull() ?: URI.create(webIdString),
                WebId::class.java
            ).getOrThrow()
            publicTypeIndexUri = extendedProfile.getPublicTypeIndex()

            if (publicTypeIndexUri == null) {
                extendedProfile.setPublicTypeIndex(webIdString, webId.getStorages()[0].toString())
                solidResourceManager.update(webIdString, extendedProfile).getOrThrow()
                publicTypeIndexUri = extendedProfile.getPublicTypeIndex()
                solidResourceManager.create(
                    webIdString,
                    PublicTypeIndex(
                        publicTypeIndexUri!!,
                        "application/ld+json",
                        null,
                        null
                    )
                ).getOrThrow()
            }
        }

        return solidResourceManager.read(
            webIdString,
            publicTypeIndexUri,
            PublicTypeIndex::class.java
        ).getOrThrow()
    }
}