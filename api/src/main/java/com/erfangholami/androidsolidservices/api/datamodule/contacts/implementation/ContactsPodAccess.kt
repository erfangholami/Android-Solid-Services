package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CancellationException

internal suspend fun <T> runResult(block: suspend () -> T): SolidResult<T> =
    try {
        SolidResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SolidResult.Failure(SolidError.fromThrowable(e))
    }

internal class ContactsPodAccess(
    val solidResourceManager: SolidResourceManager,
) {

    suspend fun addressBook(ownerWebId: String, uri: String): AddressBookRDF =
        solidResourceManager.read(ownerWebId, uri, AddressBookRDF::class.java).getOrThrow()

    suspend fun peopleIndex(ownerWebId: String, uri: String): NameEmailIndexRDF =
        solidResourceManager.read(ownerWebId, uri, NameEmailIndexRDF::class.java).getOrThrow()

    suspend fun groupsIndex(ownerWebId: String, uri: String): GroupsIndexRDF =
        solidResourceManager.read(ownerWebId, uri, GroupsIndexRDF::class.java).getOrThrow()

    suspend fun addressBookOrNull(ownerWebId: String, uri: String): AddressBookRDF? =
        solidResourceManager.read(ownerWebId, uri, AddressBookRDF::class.java).dataOrNullIfMissing()

    suspend fun peopleIndexOrNull(ownerWebId: String, uri: String): NameEmailIndexRDF? =
        solidResourceManager.read(ownerWebId, uri, NameEmailIndexRDF::class.java).dataOrNullIfMissing()

    suspend fun groupsIndexOrNull(ownerWebId: String, uri: String): GroupsIndexRDF? =
        solidResourceManager.read(ownerWebId, uri, GroupsIndexRDF::class.java).dataOrNullIfMissing()

    suspend fun ensureContainer(ownerWebId: String, containerUri: String) {
        solidResourceManager.ensureContainer(ownerWebId, containerUri).getOrThrow()
    }

    suspend fun contact(ownerWebId: String, uri: String): ContactRDF =
        solidResourceManager.read(ownerWebId, uri, ContactRDF::class.java).getOrThrow()

    suspend fun group(ownerWebId: String, uri: String): GroupRDF =
        solidResourceManager.read(ownerWebId, uri, GroupRDF::class.java).getOrThrow()

    suspend fun privateTypeIndex(webId: String): PrivateTypeIndex =
        TypeIndexResolver.getPrivateTypeIndex(solidResourceManager, webId)

    suspend fun publicTypeIndex(webId: String): PublicTypeIndex =
        TypeIndexResolver.getPublicTypeIndex(solidResourceManager, webId)

    suspend fun refreshCachedName(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
        newName: String,
    ) {
        updatePeopleIndex(ownerWebId, addressBookUri) {
            it.updateContactName(contactUri, newName)
        }
        val groupsIndexUri = addressBook(ownerWebId, addressBookUri).getGroupsIndex() ?: return
        val groupsIndexRDF = groupsIndex(ownerWebId, groupsIndexUri)
        groupsIndexRDF.getGroups(addressBookUri).forEach { groupSummary ->
            val groupUri = groupSummary.uri
            solidResourceManager.casUpdate(
                ownerWebId,
                read = { solidResourceManager.read(ownerWebId, groupUri, GroupRDF::class.java) },
                mutate = { it.updateMemberName(contactUri, newName) },
            ).getOrThrow()
        }
    }

    suspend fun updatePeopleIndex(
        ownerWebId: String,
        addressBookUri: String,
        mutate: (NameEmailIndexRDF) -> Boolean,
    ) {
        val peopleUri = addressBook(ownerWebId, addressBookUri).getNameEmailIndex() ?: return
        solidResourceManager.casUpdate(
            ownerWebId,
            read = { solidResourceManager.read(ownerWebId, peopleUri, NameEmailIndexRDF::class.java) },
            mutate = mutate,
        ).getOrThrow()
    }

    suspend fun updateGroupsIndex(
        ownerWebId: String,
        addressBookUri: String,
        mutate: (GroupsIndexRDF) -> Boolean,
    ) {
        val groupsUri = addressBook(ownerWebId, addressBookUri).getGroupsIndex() ?: return
        solidResourceManager.casUpdate(
            ownerWebId,
            read = { solidResourceManager.read(ownerWebId, groupsUri, GroupsIndexRDF::class.java) },
            mutate = mutate,
        ).getOrThrow()
    }

    suspend fun updateGroup(
        ownerWebId: String,
        groupUri: String,
        mutate: (GroupRDF) -> Boolean,
    ) {
        val uri = groupUri
        solidResourceManager.casUpdate(
            ownerWebId,
            read = { solidResourceManager.read(ownerWebId, uri, GroupRDF::class.java) },
            mutate = mutate,
        ).getOrThrow()
    }

    private fun <T> SolidResult<T>.dataOrNullIfMissing(): T? = when (this) {
        is SolidResult.Success -> value
        is SolidResult.Failure ->
            if (error.code == SolidErrorCode.NOT_FOUND) null else getOrThrow()
    }
}
