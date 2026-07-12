package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.contacts.GroupStore
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.GROUP_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import java.net.URI
import java.util.UUID

internal class GroupEngine(
    private val pod: ContactsPodAccess,
) : GroupStore {

    override suspend fun create(
        ownerWebId: String,
        addressBookUri: String,
        title: String,
        contactUris: List<String>,
    ): SolidResult<FullGroup> = runResult {
        val bookContainer =
            addressBookUri.substring(0, addressBookUri.lastIndexOf("/") + 1)
        pod.ensureContainer(ownerWebId, URI.create("${bookContainer}${GROUP_DIRECTORY_SUFFIX}"))
        val groupUri = "${bookContainer}${GROUP_DIRECTORY_SUFFIX}${UUID.randomUUID()}.ttl"
        val groupRdf = GroupRDF(
            identifier = URI.create(groupUri),
            contentType = "application/ld+json",
            quads = null,
            headers = null,
        ).apply {
            setTitle(title)
            setIncludesInAddressBook(addressBookUri)
        }
        val created = pod.solidResourceManager.create(ownerWebId, groupRdf).getOrThrow()

        val bookIdentifier =
            pod.addressBook(ownerWebId, URI.create(addressBookUri)).getIdentifier().toString()
        pod.updateGroupsIndex(ownerWebId, addressBookUri) {
            it.addGroup(bookIdentifier, created)
            true
        }

        contactUris.forEach { contactUri ->
            addMemberInternal(
                ownerWebId,
                created.getIdentifier().toString(),
                pod.contact(ownerWebId, URI.create(contactUri)),
            )
        }
        FullGroup.createFromRdf(pod.group(ownerWebId, created.getIdentifier()))
    }

    override suspend fun get(
        ownerWebId: String,
        groupUri: String,
    ): SolidResult<FullGroup> = runResult {
        FullGroup.createFromRdf(pod.group(ownerWebId, URI.create(groupUri)))
    }

    override suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
        groupUri: String,
    ): SolidResult<FullGroup> = runResult {
        val groupRdf = pod.group(ownerWebId, URI.create(groupUri))
        var removed = false
        pod.updateGroupsIndex(ownerWebId, addressBookUri) {
            removed = it.removeGroup(URI.create(groupUri))
            removed
        }
        if (removed) {
            pod.solidResourceManager.delete(ownerWebId, groupRdf).getOrThrow()
        }
        FullGroup.createFromRdf(groupRdf)
    }

    override suspend fun addMember(
        ownerWebId: String,
        groupUri: String,
        contactUri: String,
    ): SolidResult<FullGroup> = runResult {
        val contactRdf = pod.contact(ownerWebId, URI.create(contactUri))
        addMemberInternal(ownerWebId, groupUri, contactRdf)
        FullGroup.createFromRdf(pod.group(ownerWebId, URI.create(groupUri)))
    }

    override suspend fun removeMember(
        ownerWebId: String,
        groupUri: String,
        contactUri: String,
    ): SolidResult<FullGroup> = runResult {
        removeMemberInternal(ownerWebId, groupUri, contactUri)
        FullGroup.createFromRdf(pod.group(ownerWebId, URI.create(groupUri)))
    }

    internal suspend fun addMemberInternal(
        ownerWebId: String,
        groupUri: String,
        contact: ContactRDF,
    ) {
        pod.updateGroup(ownerWebId, groupUri) {
            it.addMember(contact)
            true
        }
    }

    internal suspend fun removeMemberInternal(
        ownerWebId: String,
        groupUri: String,
        contactUri: String,
    ) {
        pod.updateGroup(ownerWebId, groupUri) {
            it.removeMember(URI.create(contactUri))
        }
    }
}
