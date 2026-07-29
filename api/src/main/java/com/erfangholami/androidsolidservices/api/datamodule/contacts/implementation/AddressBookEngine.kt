package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
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
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import java.util.UUID

internal class AddressBookEngine(
    private val pod: ContactsPodAccess,
) : AddressBookStore {

    override suspend fun list(ownerWebId: String): SolidResult<AddressBookList> = runResult {
        readBookList(ownerWebId)
    }

    override suspend fun ensureContainer(
        ownerWebId: String,
        storage: String?,
        container: String?,
    ): SolidResult<AddressBookList> = runResult {
        val targetContainer =
            container ?: "${requireStorage(ownerWebId, storage)}${CONTACTS_DIRECTORY_SUFFIX}"
        pod.ensureContainer(ownerWebId, targetContainer)
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
        storage: String?,
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
        pod.solidResourceManager.casUpdate(
            ownerWebId,
            read = { pod.solidResourceManager.read(ownerWebId, addressBookUri, AddressBookRDF::class.java) },
            mutate = { book ->
                if (book.getTitle() == newName) {
                    false
                } else {
                    book.setTitle(newName)
                    true
                }
            },
        ).getOrThrow()
        readBook(ownerWebId, addressBookUri)
    }

    override suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook> = runResult {
        val book = runCatching { readBook(ownerWebId, addressBookUri) }.getOrNull()

        val bookContainer = addressBookUri.substring(0, addressBookUri.lastIndexOf("/") + 1)
        pod.solidResourceManager.delete(ownerWebId, bookContainer).getOrThrow()

        TypeIndexResolver.removeResource(pod.solidResourceManager, ownerWebId, addressBookUri)

        book ?: AddressBook(addressBookUri, "", emptyList(), emptyList())
    }

    override suspend fun ensureDefault(
        ownerWebId: String,
        storage: String?,
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

    private suspend fun requireStorage(ownerWebId: String, storage: String?): String =
        storage
            ?: StorageDiscovery.discover(pod.solidResourceManager, ownerWebId)
            ?: error("Could not discover a storage for $ownerWebId")

    private suspend fun readBook(ownerWebId: String, addressBookUri: String): AddressBook {
        val addressBookRdf = pod.addressBook(ownerWebId, addressBookUri)
        val peopleIndexUri = addressBookRdf.getNameEmailIndex()
        val groupsIndexUri = addressBookRdf.getGroupsIndex()
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
        storage: String?,
        container: String?,
    ): String {
        val targetContainer =
            container ?: "${requireStorage(ownerWebId, storage)}${CONTACTS_DIRECTORY_SUFFIX}"
        val id = UUID.randomUUID().toString()
        val bookContainer = "${targetContainer}${id}/"
        pod.ensureContainer(ownerWebId, bookContainer)
        val bookUri = "${bookContainer}${INDEX_FILE_NAME}"
        val peopleIndexUri = "${bookContainer}${PEOPLE_FILE_NAME}"
        val groupsIndexUri = "${bookContainer}${GROUPS_FILE_NAME}"

        val peopleIndex = NameEmailIndexRDF(peopleIndexUri)
        val groupsIndex = GroupsIndexRDF(groupsIndexUri)
        val addressBook = AddressBookRDF(
            identifier = bookUri,
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

        TypeIndexResolver.addInstance(
            resourceManager = pod.solidResourceManager,
            webIdString = ownerWebId,
            forClass = VCARD.ADDRESS_BOOK,
            instanceUri = created.getIdentifier(),
            isPrivate = isPrivate,
        )
        return created.getIdentifier()
    }
}
