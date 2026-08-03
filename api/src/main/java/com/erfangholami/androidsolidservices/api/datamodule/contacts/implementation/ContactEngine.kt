package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.api.datamodule.core.containerOf
import com.erfangholami.androidsolidservices.api.datamodule.core.deleteTolerant
import com.erfangholami.androidsolidservices.api.datamodule.core.putAttachment
import com.erfangholami.androidsolidservices.api.datamodule.core.readAttachment
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.PEOPLE_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.result.solidCatching
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID

internal class ContactEngine(
    private val pod: ContactsPodAccess,
    private val groupEngine: GroupEngine,
) : ContactStore {

    override suspend fun get(
        ownerWebId: String,
        contactUri: String,
    ): SolidResult<SolidContact> = solidCatching {
        SolidContact.createFromRdf(pod.contact(ownerWebId, contactUri))
    }

    override suspend fun list(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<SolidContactList> = solidCatching {
        SolidContactList(fetchAll(ownerWebId, addressBookUri))
    }

    override suspend fun create(
        ownerWebId: String,
        addressBookUri: String,
        data: ContactData,
        groupUris: List<String>,
    ): SolidResult<SolidContact> = solidCatching {
        require(data.effectiveFullName().isNotBlank()) {
            "A contact needs at least a name, phone number, or email address"
        }
        val contactId = UUID.randomUUID().toString()
        val bookContainer = containerOf(addressBookUri)
        val contactContainer = "${bookContainer}${PEOPLE_DIRECTORY_SUFFIX}${contactId}/"
        val contactUri = "${contactContainer}${INDEX_FILE_NAME}"
        pod.ensureContainer(ownerWebId, contactContainer)
        val effectiveData =
            if (data.uid == null) data.copy(uid = "urn:uuid:$contactId") else data
        val contactRdf = ContactRDF(contactUri).apply {
            setContactData(effectiveData)
        }
        val created = pod.solidResourceManager.create(ownerWebId, contactRdf).getOrThrow()

        pod.updatePeopleIndex(ownerWebId, addressBookUri) {
            it.addContact(addressBookUri, created)
            true
        }

        groupUris.forEach { groupUri ->
            groupEngine.addMemberInternal(ownerWebId, groupUri, created)
        }
        SolidContact.createFromRdf(created)
    }

    override suspend fun update(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
        data: ContactData,
    ): SolidResult<SolidContact> = solidCatching {
        require(data.effectiveFullName().isNotBlank()) {
            "A contact needs at least a name, phone number, or email address"
        }
        var oldName: String? = null
        val updated = pod.solidResourceManager.casUpdate(
            ownerWebId,
            read = { pod.solidResourceManager.read(ownerWebId, contactUri, ContactRDF::class.java) },
            mutate = { contactRdf ->
                oldName = runCatching { contactRdf.getFullName() }.getOrNull()
                val effectiveData = if (data.uid == null) data.copy(uid = contactRdf.getUid()) else data
                contactRdf.setContactData(effectiveData)
                true
            },
        ).getOrThrow()
        val newName = data.effectiveFullName()
        if (oldName != newName) {
            pod.refreshCachedName(ownerWebId, addressBookUri, contactUri, newName)
        }
        SolidContact.createFromRdf(updated)
    }

    override suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): SolidResult<SolidContact> = solidCatching {
        val contact = runCatching {
            SolidContact.createFromRdf(pod.contact(ownerWebId, contactUri))
        }.getOrNull()

        deleteTolerant(pod.solidResourceManager, ownerWebId, containerOf(contactUri))

        var removed = false
        pod.updatePeopleIndex(ownerWebId, addressBookUri) {
            removed = it.removeContact(contactUri)
            removed
        }
        val groupsIndexUri = pod.addressBook(ownerWebId, addressBookUri).getGroupsIndex()
        if (removed && groupsIndexUri != null) {
            pod.groupsIndex(ownerWebId, groupsIndexUri)
                .getGroups(addressBookUri)
                .forEach { groupEngine.removeMemberInternal(ownerWebId, it.uri, contactUri) }
        }
        contact ?: SolidContact(uri = contactUri, data = ContactData())
    }

    override suspend fun setPhoto(
        ownerWebId: String,
        contactUri: String,
        photo: ByteArray,
        contentType: String,
    ): SolidResult<SolidContact> = solidCatching {
        val photoUri = putAttachment(
            resourceManager = pod.solidResourceManager,
            ownerWebId = ownerWebId,
            container = contactUri.substringBefore(INDEX_FILE_NAME),
            role = PHOTO_ROLE,
            contentType = contentType,
            body = photo,
        )
        var previous: String? = null
        val updated = pod.solidResourceManager.casUpdate(
            ownerWebId,
            read = { pod.solidResourceManager.read(ownerWebId, contactUri, ContactRDF::class.java) },
            mutate = { contactRdf ->
                previous = contactRdf.getPhotoUrl()
                contactRdf.setPhoto(photoUri)
                true
            },
        ).getOrThrow()
        val previousPhotoUri = previous
        if (previousPhotoUri != null && previousPhotoUri != photoUri) {
            deleteTolerant(pod.solidResourceManager, ownerWebId, previousPhotoUri)
        }
        SolidContact.createFromRdf(updated)
    }

    override suspend fun removePhoto(
        ownerWebId: String,
        contactUri: String,
    ): SolidResult<SolidContact> = solidCatching {
        var removedPhoto: String? = null
        val updated = pod.solidResourceManager.casUpdate(
            ownerWebId,
            read = { pod.solidResourceManager.read(ownerWebId, contactUri, ContactRDF::class.java) },
            mutate = { contactRdf ->
                val photoUri = contactRdf.getPhotoUrl()
                if (photoUri == null) {
                    false
                } else {
                    removedPhoto = photoUri
                    contactRdf.removePhoto()
                    true
                }
            },
        ).getOrThrow()
        removedPhoto?.let { deleteTolerant(pod.solidResourceManager, ownerWebId, it) }
        SolidContact.createFromRdf(updated)
    }

    override suspend fun getPhoto(
        ownerWebId: String,
        photoUri: String,
    ): SolidResult<ContactPhoto> = solidCatching {
        val attachment = readAttachment(pod.solidResourceManager, ownerWebId, photoUri)
        ContactPhoto(attachment.uri, attachment.contentType, attachment.bytes)
    }

    override suspend fun findByWebId(
        ownerWebId: String,
        webId: String,
    ): SolidResult<ContactMatch> = solidCatching {
        val target = webId.trim()
        val bookUris = pod.privateTypeIndex(ownerWebId).getInstances(VCARD.ADDRESS_BOOK) +
                pod.publicTypeIndex(ownerWebId).getInstances(VCARD.ADDRESS_BOOK)
        for (bookUri in bookUris.distinct()) {
            val contacts = runCatching {
                fetchAll(ownerWebId, bookUri)
            }.getOrDefault(emptyList())
            val match = contacts.firstOrNull { it.data.webId()?.trim() == target }
            if (match != null) return@solidCatching ContactMatch(match, bookUri)
        }
        ContactMatch()
    }

    private suspend fun fetchAll(
        ownerWebId: String,
        addressBookUri: String,
    ): List<SolidContact> {
        val addressBookRdf =
            pod.addressBookOrNull(ownerWebId, addressBookUri) ?: return emptyList()
        val peopleIndexRdf = addressBookRdf.getNameEmailIndex()
            ?.let { pod.peopleIndexOrNull(ownerWebId, it) }
            ?: return emptyList()
        val entries = peopleIndexRdf.getContacts(addressBookUri)
        return coroutineScope {
            entries.map { entry ->
                async {
                    SolidContact.createFromRdf(pod.contact(ownerWebId, entry.uri))
                }
            }.awaitAll()
        }
    }
}

private const val PHOTO_ROLE = "photo"
