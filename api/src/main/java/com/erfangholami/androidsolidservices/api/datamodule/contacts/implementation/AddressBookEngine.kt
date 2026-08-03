package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.api.datamodule.core.containerOf
import com.erfangholami.androidsolidservices.api.datamodule.core.requireStorage
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
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
import com.erfangholami.androidsolidservices.shared.result.solidCatching
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import java.util.UUID

internal class AddressBookEngine(
    private val pod: ContactsPodAccess,
) : AddressBookStore {

    override suspend fun list(ownerWebId: String): SolidResult<AddressBookList> = solidCatching {
        readBookList(ownerWebId)
    }

    override suspend fun ensureContainer(
        ownerWebId: String,
        storage: String?,
        container: String?,
    ): SolidResult<AddressBookList> = solidCatching {
        val targetContainer =
            container ?: bookRoot(ownerWebId, storage)
        pod.ensureContainer(ownerWebId, targetContainer)
        readBookList(ownerWebId)
    }

    private suspend fun readBookList(ownerWebId: String): AddressBookList = AddressBookList(
        publicAddressBookUris = runCatching {
            pod.publicTypeIndex(ownerWebId).getInstances(VCARD.ADDRESS_BOOK)
        }.getOrDefault(emptyList()),
        privateAddressBookUris = runCatching {
            pod.privateTypeIndex(ownerWebId).getInstances(VCARD.ADDRESS_BOOK)
        }.getOrDefault(emptyList()),
    )

    override suspend fun get(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<AddressBook> = solidCatching {
        readBook(ownerWebId, addressBookUri)
    }

    override suspend fun create(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String?,
        container: String?,
    ): SolidResult<AddressBook> = solidCatching {
        val bookUri = createBook(ownerWebId, title, isPrivate, storage, container)
        readBook(ownerWebId, bookUri)
    }

    override suspend fun rename(
        ownerWebId: String,
        addressBookUri: String,
        newName: String,
    ): SolidResult<AddressBook> = solidCatching {
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
    ): SolidResult<AddressBook> = solidCatching {
        val book = runCatching { readBook(ownerWebId, addressBookUri) }.getOrNull()

        val bookContainer = containerOf(addressBookUri)
        pod.solidResourceManager.delete(ownerWebId, bookContainer).getOrThrow()

        TypeIndexResolver.removeResource(pod.solidResourceManager, ownerWebId, addressBookUri)

        book ?: AddressBook(addressBookUri, "", emptyList(), emptyList())
    }

    override suspend fun ensureDefault(
        ownerWebId: String,
        storage: String?,
        title: String,
    ): SolidResult<AddressBook> = solidCatching {
        val firstPrivate = pod.privateTypeIndex(ownerWebId).getInstances(VCARD.ADDRESS_BOOK).firstOrNull()
        val bookUri = firstPrivate ?: createBook(
            ownerWebId = ownerWebId,
            title = title,
            isPrivate = true,
            storage = storage,
            container = null,
        )
        readBook(ownerWebId, bookUri)
    }

    private suspend fun bookRoot(ownerWebId: String, storage: String?): String {
        val root = requireStorage(pod.solidResourceManager, ownerWebId, storage)
        return "$root$CONTACTS_DIRECTORY_SUFFIX"
    }

    private suspend fun readBook(ownerWebId: String, addressBookUri: String): AddressBook {
        val addressBookRdf = pod.addressBook(ownerWebId, addressBookUri)
        val peopleIndexUri = addressBookRdf.getNameEmailIndex()
        val groupsIndexUri = addressBookRdf.getGroupsIndex()
        val peopleIndexRdf = peopleIndexUri
            ?.let { pod.peopleIndexOrNull(ownerWebId, it) }
            ?: NameEmailIndexRDF(peopleIndexUri ?: addressBookUri)
        val groupsIndexRdf = groupsIndexUri
            ?.let { pod.groupsIndexOrNull(ownerWebId, it) }
            ?: GroupsIndexRDF(groupsIndexUri ?: addressBookUri)
        return AddressBook.createFromRdf(addressBookRdf, peopleIndexRdf, groupsIndexRdf)
    }

    private suspend fun createBook(
        ownerWebId: String,
        title: String,
        isPrivate: Boolean,
        storage: String?,
        container: String?,
    ): String {
        val targetContainer = container ?: bookRoot(ownerWebId, storage)
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
