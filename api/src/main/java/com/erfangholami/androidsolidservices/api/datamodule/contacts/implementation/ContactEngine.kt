package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.INDEX_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.contacts.PEOPLE_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class ContactEngine(
    private val pod: ContactsPodAccess,
    private val groupEngine: GroupEngine,
) : ContactStore {

    override suspend fun get(
        ownerWebId: String,
        contactUri: String,
    ): SolidResult<SolidContact> = runResult {
        SolidContact.createFromRdf(pod.contact(ownerWebId, URI.create(contactUri)))
    }

    override suspend fun getAll(
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
        pod.ensureContainer(ownerWebId, URI.create(contactContainer))
        val effectiveData =
            if (data.uid == null) data.copy(uid = "urn:uuid:$contactId") else data
        val contactRdf = ContactRDF(URI.create(contactUri)).apply {
            setContactData(effectiveData)
        }
        val created = pod.solidResourceManager.create(ownerWebId, contactRdf).getOrThrow()

        val addressBookRdf = pod.addressBook(ownerWebId, URI.create(addressBookUri))
        val peopleIndexRdf =
            pod.peopleIndex(ownerWebId, URI.create(addressBookRdf.getNameEmailIndex()))
        peopleIndexRdf.addContact(addressBookRdf.getIdentifier().toString(), created)
        pod.solidResourceManager.update(ownerWebId, peopleIndexRdf).getOrThrow()

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
        val old = pod.contact(ownerWebId, URI.create(contactUri))
        val effectiveData = if (data.uid == null) data.copy(uid = old.getUid()) else data
        val fresh = ContactRDF(URI.create(contactUri)).apply {
            setContactData(effectiveData)
            old.getPhotoUrl()?.let { setPhoto(it) }
        }
        pod.solidResourceManager.update(ownerWebId, fresh).getOrThrow()
        val newName = data.effectiveFullName()
        val oldName = runCatching { old.getFullName() }.getOrNull()
        if (oldName != newName) {
            pod.refreshCachedName(ownerWebId, addressBookUri, contactUri, newName)
        }
        SolidContact.createFromRdf(fresh)
    }

    override suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): SolidResult<SolidContact> = runResult {
        val contact = runCatching {
            SolidContact.createFromRdf(pod.contact(ownerWebId, URI.create(contactUri)))
        }.getOrNull()

        val contactContainer = contactUri.substring(0, contactUri.lastIndexOf("/") + 1)
        deleteTolerant(ownerWebId, URI.create(contactContainer))

        val addressBookRdf = pod.addressBook(ownerWebId, URI.create(addressBookUri))
        val peopleIndexRdf =
            pod.peopleIndex(ownerWebId, URI.create(addressBookRdf.getNameEmailIndex()))
        if (peopleIndexRdf.removeContact(contactUri)) {
            pod.solidResourceManager.update(ownerWebId, peopleIndexRdf).getOrThrow()
            val groupsIndexRdf =
                pod.groupsIndex(ownerWebId, URI.create(addressBookRdf.getGroupsIndex()))
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
        val contactRdf = pod.contact(ownerWebId, URI.create(contactUri))
        val contactContainer = contactUri.substringBefore(INDEX_FILE_NAME)
        val photoUri = "${contactContainer}photo${extensionFor(contentType)}"
        pod.solidResourceManager.putRaw(
            webid = ownerWebId,
            uri = URI.create(photoUri),
            contentType = contentType,
            body = photo,
            ifMatch = null,
            linkHeader = "<${LDP.NON_RDF_SOURCE}>; rel=\"type\"",
        ).getOrThrow()
        val previous = contactRdf.getPhotoUrl()
        contactRdf.setPhoto(photoUri)
        pod.solidResourceManager.update(ownerWebId, contactRdf).getOrThrow()
        if (previous != null && previous != photoUri) {
            deleteTolerant(ownerWebId, URI.create(previous))
        }
        SolidContact.createFromRdf(contactRdf)
    }

    override suspend fun removePhoto(
        ownerWebId: String,
        contactUri: String,
    ): SolidResult<SolidContact> = runResult {
        val contactRdf = pod.contact(ownerWebId, URI.create(contactUri))
        contactRdf.getPhotoUrl()?.let { photoUri ->
            contactRdf.removePhoto()
            pod.solidResourceManager.update(ownerWebId, contactRdf).getOrThrow()
            deleteTolerant(ownerWebId, URI.create(photoUri))
        }
        SolidContact.createFromRdf(contactRdf)
    }

    override suspend fun getPhoto(
        ownerWebId: String,
        photoUri: String,
    ): SolidResult<ContactPhoto> = runResult {
        val resource = pod.solidResourceManager
            .read(ownerWebId, URI.create(photoUri), SolidNonRDFResource::class.java)
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
            pod.addressBookOrNull(ownerWebId, URI.create(addressBookUri)) ?: return emptyList()
        val peopleIndexRdf =
            pod.peopleIndexOrNull(ownerWebId, URI.create(addressBookRdf.getNameEmailIndex()))
                ?: return emptyList()
        val entries = peopleIndexRdf.getContacts(addressBookUri)
        return coroutineScope {
            entries.map { entry ->
                async {
                    SolidContact.createFromRdf(pod.contact(ownerWebId, URI.create(entry.uri)))
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

    private suspend fun deleteTolerant(ownerWebId: String, uri: URI) {
        when (val result = pod.solidResourceManager.delete(ownerWebId, uri)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (result.error.code == SolidErrorCode.NOT_FOUND) Unit else result.getOrThrow()
        }
    }
}
