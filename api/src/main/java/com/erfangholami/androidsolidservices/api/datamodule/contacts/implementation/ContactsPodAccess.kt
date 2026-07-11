package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.typeindex.TypeIndexResolver
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.ContactRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import kotlinx.coroutines.CancellationException
import java.net.URI

/**
 * Wraps a contacts-engine operation into a [DataModuleResult], mapping any thrown
 * exception to [DataModuleResult.Exception]. [CancellationException] is rethrown so
 * coroutine cancellation propagates instead of surfacing as a failed result.
 */
internal suspend fun <T : Parcelable> runResult(block: suspend () -> T): DataModuleResult<T> =
    try {
        DataModuleResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DataModuleResult.Exception(e)
    }

/**
 * Shared low-level pod access for the contacts engines: typed reads of the module's
 * RDF documents, type-index resolution, and the display-name cache refresh that keeps
 * `people.ttl` and per-group cached names consistent with a contact's `vcard:fn`.
 */
internal class ContactsPodAccess(
    val solidResourceManager: SolidResourceManager,
) {

    suspend fun addressBook(ownerWebId: String, uri: URI): AddressBookRDF =
        solidResourceManager.read(ownerWebId, uri, AddressBookRDF::class.java).getOrThrow()

    suspend fun peopleIndex(ownerWebId: String, uri: URI): NameEmailIndexRDF =
        solidResourceManager.read(ownerWebId, uri, NameEmailIndexRDF::class.java).getOrThrow()

    suspend fun groupsIndex(ownerWebId: String, uri: URI): GroupsIndexRDF =
        solidResourceManager.read(ownerWebId, uri, GroupsIndexRDF::class.java).getOrThrow()

    /** Reads the address book at [uri], or `null` when it does not exist (404). */
    suspend fun addressBookOrNull(ownerWebId: String, uri: URI): AddressBookRDF? =
        solidResourceManager.read(ownerWebId, uri, AddressBookRDF::class.java).dataOrNullIfMissing()

    /** Reads the people index at [uri], or `null` when it does not exist (404). */
    suspend fun peopleIndexOrNull(ownerWebId: String, uri: URI): NameEmailIndexRDF? =
        solidResourceManager.read(ownerWebId, uri, NameEmailIndexRDF::class.java).dataOrNullIfMissing()

    /** Reads the groups index at [uri], or `null` when it does not exist (404). */
    suspend fun groupsIndexOrNull(ownerWebId: String, uri: URI): GroupsIndexRDF? =
        solidResourceManager.read(ownerWebId, uri, GroupsIndexRDF::class.java).dataOrNullIfMissing()

    /**
     * Ensures the container at [containerUri] and its whole parent chain exist, creating each
     * missing level as an LDP BasicContainer (bottom stops as soon as a HEAD reports an existing
     * ancestor — the pod storage root always exists). Needed for pods whose server does not
     * auto-create intermediate containers on PUT. No-op when [containerUri] already exists; other
     * HEAD errors are ignored so the subsequent write surfaces the real failure.
     */
    suspend fun ensureContainer(ownerWebId: String, containerUri: URI) {
        val missing = solidResourceManager.head(ownerWebId, containerUri).let {
            it is SolidNetworkResponse.Error && it.errorCode == 404
        }
        if (!missing) return
        parentContainer(containerUri)?.let { ensureContainer(ownerWebId, it) }
        solidResourceManager.create(ownerWebId, SolidContainer(containerUri)).getOrThrow()
    }

    private fun parentContainer(containerUri: URI): URI? {
        val text = containerUri.toString()
        val schemeIdx = text.indexOf("://")
        if (schemeIdx < 0) return null
        val firstPathSlash = text.indexOf('/', schemeIdx + 3)
        if (firstPathSlash < 0) return null
        val trimmed = text.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash <= firstPathSlash) return null
        return URI.create(trimmed.substring(0, lastSlash + 1))
    }

    suspend fun contact(ownerWebId: String, uri: URI): ContactRDF =
        solidResourceManager.read(ownerWebId, uri, ContactRDF::class.java).getOrThrow()

    suspend fun group(ownerWebId: String, uri: URI): GroupRDF =
        solidResourceManager.read(ownerWebId, uri, GroupRDF::class.java).getOrThrow()

    suspend fun privateTypeIndex(webId: String): PrivateTypeIndex =
        TypeIndexResolver.getPrivateTypeIndex(solidResourceManager, webId)

    suspend fun publicTypeIndex(webId: String): PublicTypeIndex =
        TypeIndexResolver.getPublicTypeIndex(solidResourceManager, webId)

    /**
     * Rewrites the cached `vcard:fn` for [contactUri] in the book's people index and in
     * every group of the book that lists the contact as a member.
     */
    suspend fun refreshCachedName(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
        newName: String,
    ) {
        val addressBookRDF = addressBook(ownerWebId, URI.create(addressBookUri))
        val peopleIndexRDF =
            peopleIndex(ownerWebId, URI.create(addressBookRDF.getNameEmailIndex()))
        if (peopleIndexRDF.updateContactName(contactUri, newName)) {
            solidResourceManager.update(ownerWebId, peopleIndexRDF).getOrThrow()
        }
        val groupsIndexRDF =
            groupsIndex(ownerWebId, URI.create(addressBookRDF.getGroupsIndex()))
        groupsIndexRDF.getGroups(addressBookUri).forEach { groupSummary ->
            val groupRdf = group(ownerWebId, URI.create(groupSummary.uri))
            if (groupRdf.updateMemberName(contactUri, newName)) {
                solidResourceManager.update(ownerWebId, groupRdf).getOrThrow()
            }
        }
    }

    private fun <T> SolidNetworkResponse<T>.dataOrNullIfMissing(): T? = when (this) {
        is SolidNetworkResponse.Success -> data
        is SolidNetworkResponse.Error -> if (errorCode == 404) null else getOrThrow()
        is SolidNetworkResponse.Exception -> getOrThrow()
    }
}
