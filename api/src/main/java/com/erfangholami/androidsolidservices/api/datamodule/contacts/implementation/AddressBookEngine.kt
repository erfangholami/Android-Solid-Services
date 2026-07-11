package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.CONTACTS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.GROUPS_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.PEOPLE_FILE_NAME
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import java.net.URI
import java.util.UUID

internal class AddressBookEngine(
    private val pod: ContactsPodAccess,
) : AddressBookStore {

    override suspend fun list(ownerWebId: String): SolidResult<AddressBookList> = runResult {
        readBookList(ownerWebId)
    }

    override suspend fun ensureContainer(
        ownerWebId: String,
        storage: String,
        container: String?,
    ): SolidResult<AddressBookList> = runResult {
        val targetContainer = container ?: "${storage}${CONTACTS_DIRECTORY_SUFFIX}"
        pod.ensureContainer(ownerWebId, URI.create(targetContainer))
        readBookList(ownerWebId)
    }

    private suspend fun readBookList(ownerWebId: String): AddressBookList = AddressBookList(
        publicAddressBookUris = runCatching {
            pod.publicTypeIndex(ownerWebId).getAddressBooks()
        }.getOrDefault(emptyList()),
        privateAddressBookUris = runCatching {
            pod.privateTypeIndex(ownerWebId).getAddressBooks()
        }.getOrDefault(emptyList()),
    )

    override suspend fun get(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook> = runResult {
        readBook(ownerWebId, addressBookUri)
    }

    override suspend fun create(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String,
        container: String?,
    ): SolidResult<AddressBook> = runResult {
        val bookUri = createBook(ownerWebId, title, isPrivate, storage, container)
        readBook(ownerWebId, bookUri)
    }

    override suspend fun rename(
        ownerWebId: String,
        addressBookUri: String,
        newName: String,
    ): SolidResult<AddressBook> = runResult {
        val addressBookRdf = pod.addressBook(ownerWebId, URI.create(addressBookUri))
        if (addressBookRdf.getTitle() != newName) {
            addressBookRdf.setTitle(newName)
            pod.solidResourceManager.update(ownerWebId, addressBookRdf).getOrThrow()
        }
        readBook(ownerWebId, addressBookUri)
    }

    override suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook> = runResult {
        val book = runCatching { readBook(ownerWebId, addressBookUri) }.getOrNull()

        // Delete the whole book container recursively first, and only deregister it from the type
        // index if that succeeded. Deregistering after a partial delete would strip discovery while
        // leaving orphaned contact documents behind, so a failed delete must abort here (getOrThrow)
        // — the book stays registered and findable, and the caller can safely retry.
        val bookContainer = addressBookUri.substring(0, addressBookUri.lastIndexOf("/") + 1)
        pod.solidResourceManager.delete(ownerWebId, URI.create(bookContainer)).getOrThrow()

        val privateTypeIndex = pod.privateTypeIndex(ownerWebId)
        if (privateTypeIndex.containsAddressBook(addressBookUri)) {
            privateTypeIndex.removeAddressBook(addressBookUri)
            pod.solidResourceManager.update(ownerWebId, privateTypeIndex).getOrThrow()
        } else {
            val publicTypeIndex = pod.publicTypeIndex(ownerWebId)
            if (publicTypeIndex.containsAddressBook(addressBookUri)) {
                publicTypeIndex.removeAddressBook(addressBookUri)
                pod.solidResourceManager.update(ownerWebId, publicTypeIndex).getOrThrow()
            }
        }

        book ?: AddressBook(addressBookUri, "", emptyList(), emptyList())
    }

    override suspend fun ensureDefault(
        ownerWebId: String,
        storage: String,
        title: String,
    ): SolidResult<AddressBook> = runResult {
        val firstPrivate = pod.privateTypeIndex(ownerWebId).getAddressBooks().firstOrNull()
        val bookUri = firstPrivate ?: createBook(
            ownerWebId = ownerWebId,
            title = title,
            isPrivate = true,
            storage = storage,
            container = null,
        )
        readBook(ownerWebId, bookUri)
    }

    private suspend fun readBook(ownerWebId: String, addressBookUri: String): AddressBook {
        val addressBookRdf = pod.addressBook(ownerWebId, URI.create(addressBookUri))
        val peopleIndexUri = URI.create(addressBookRdf.getNameEmailIndex())
        val groupsIndexUri = URI.create(addressBookRdf.getGroupsIndex())
        val peopleIndexRdf = pod.peopleIndexOrNull(ownerWebId, peopleIndexUri)
            ?: NameEmailIndexRDF(peopleIndexUri)
        val groupsIndexRdf = pod.groupsIndexOrNull(ownerWebId, groupsIndexUri)
            ?: GroupsIndexRDF(groupsIndexUri)
        return AddressBook.createFromRdf(addressBookRdf, peopleIndexRdf, groupsIndexRdf)
    }

    private suspend fun createBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String,
        container: String?,
    ): String {
        val targetContainer = container ?: "${storage}${CONTACTS_DIRECTORY_SUFFIX}"
        val id = UUID.randomUUID().toString()
        val bookContainer = "${targetContainer}${id}/"
        pod.ensureContainer(ownerWebId, URI.create(bookContainer))
        val bookUri = "${bookContainer}${INDEX_FILE_NAME}"
        val peopleIndexUri = "${bookContainer}${PEOPLE_FILE_NAME}"
        val groupsIndexUri = "${bookContainer}${GROUPS_FILE_NAME}"

        val peopleIndex = NameEmailIndexRDF(URI.create(peopleIndexUri))
        val groupsIndex = GroupsIndexRDF(URI.create(groupsIndexUri))
        val addressBook = AddressBookRDF(
            identifier = URI.create(bookUri),
            contentType = "application/ld+json",
            quads = null,
            headers = null,
        ).apply {
            setOwner(ownerWebId)
            setTitle(title)
            setNameEmailIndex(peopleIndexUri)
            setGroupsIndex(groupsIndexUri)
        }

        pod.solidResourceManager.create(ownerWebId, peopleIndex).getOrThrow()
        pod.solidResourceManager.create(ownerWebId, groupsIndex).getOrThrow()
        val created = pod.solidResourceManager.create(ownerWebId, addressBook).getOrThrow()

        if (isPrivate) {
            val typeIndex = pod.privateTypeIndex(ownerWebId)
            typeIndex.addAddressBook(created.getIdentifier().toString())
            pod.solidResourceManager.update(ownerWebId, typeIndex).getOrThrow()
        } else {
            val typeIndex = pod.publicTypeIndex(ownerWebId)
            typeIndex.addAddressBook(created.getIdentifier().toString())
            pod.solidResourceManager.update(ownerWebId, typeIndex).getOrThrow()
        }
        return created.getIdentifier().toString()
    }
}
