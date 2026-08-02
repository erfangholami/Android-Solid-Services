package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.PEOPLE_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.LDP
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
    ): SolidResult<SolidContact> = runResult {
        SolidContact.createFromRdf(pod.contact(ownerWebId, contactUri))
    }

    override suspend fun list(
        ownerWebId: String,
        addressBookUri: String,
    ): SolidResult<SolidContactList> = runResult {
        SolidContactList(fetchAll(ownerWebId, addressBookUri))
    }

    override suspend fun create(
        ownerWebId: String,
        addressBookUri: String,
        data: ContactData,
        groupUris: List<String>,
    ): SolidResult<SolidContact> = runResult {
        require(data.effectiveFullName().isNotBlank()) {
            "A contact needs at least a name, phone number, or email address"
        }
        val contactId = UUID.randomUUID().toString()
        val bookContainer =
            addressBookUri.substring(0, addressBookUri.lastIndexOf("/") + 1)
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
    ): SolidResult<SolidContact> = runResult {
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
    ): SolidResult<SolidContact> = runResult {
        val contact = runCatching {
            SolidContact.createFromRdf(pod.contact(ownerWebId, contactUri))
        }.getOrNull()

        val contactContainer = contactUri.substring(0, contactUri.lastIndexOf("/") + 1)
        deleteTolerant(ownerWebId, contactContainer)

        var removed = false
        pod.updatePeopleIndex(ownerWebId, addressBookUri) {
            removed = it.removeContact(contactUri)
            removed
        }
        val groupsIndexUri = pod.addressBook(ownerWebId, addressBookUri).getGroupsIndex()
        if (removed && groupsIndexUri != null) {
            val groupsIndexRdf = pod.groupsIndex(ownerWebId, groupsIndexUri)
            groupsIndexRdf.getGroups(addressBookUri).forEach { groupSummary ->
                groupEngine.removeMemberInternal(ownerWebId, groupSummary.uri, contactUri)
            }
        }
        contact ?: SolidContact(uri = contactUri, data = ContactData())
    }

    override suspend fun setPhoto(
        ownerWebId: String,
        contactUri: String,
        photo: ByteArray,
        contentType: String,
    ): SolidResult<SolidContact> = runResult {
        val contactContainer = contactUri.substringBefore(INDEX_FILE_NAME)
        val photoUri = "${contactContainer}photo${extensionFor(contentType)}"
        pod.solidResourceManager.putRaw(
            webId = ownerWebId,
            uri = photoUri,
            contentType = contentType,
            body = photo,
            ifMatch = null,
            linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
        ).getOrThrow()
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
            deleteTolerant(ownerWebId, previousPhotoUri)
        }
        SolidContact.createFromRdf(updated)
    }

    override suspend fun removePhoto(
        ownerWebId: String,
        contactUri: String,
    ): SolidResult<SolidContact> = runResult {
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
        removedPhoto?.let { deleteTolerant(ownerWebId, it) }
        SolidContact.createFromRdf(updated)
    }

    override suspend fun getPhoto(
        ownerWebId: String,
        photoUri: String,
    ): SolidResult<ContactPhoto> = runResult {
        val resource = pod.solidResourceManager
            .read(ownerWebId, photoUri, SolidNonRDFResource::class.java)
            .getOrThrow()
        val bytes = resource.getEntity().use { it.readBytes() }
        ContactPhoto(photoUri, resource.getContentType(), bytes)
    }

    override suspend fun findByWebId(
        ownerWebId: String,
        webId: String,
    ): SolidResult<ContactMatch> = runResult {
        val target = webId.trim()
        val bookUris = pod.privateTypeIndex(ownerWebId).getAddressBooks() +
                pod.publicTypeIndex(ownerWebId).getAddressBooks()
        for (bookUri in bookUris.distinct()) {
            val contacts = runCatching {
                fetchAll(ownerWebId, bookUri)
            }.getOrDefault(emptyList())
            val match = contacts.firstOrNull { it.data.webId()?.trim() == target }
            if (match != null) return@runResult ContactMatch(match, bookUri)
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

    private fun extensionFor(contentType: String): String = when (contentType.lowercase()) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        else -> ""
    }

    private suspend fun deleteTolerant(ownerWebId: String, uri: String) {
        when (val result = pod.solidResourceManager.delete(ownerWebId, uri)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (result.error.code == SolidErrorCode.NOT_FOUND) Unit else result.getOrThrow()
        }
    }
}
