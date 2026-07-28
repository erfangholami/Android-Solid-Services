package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.services.dispatch.dispatchDataModule
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookListCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleContactMatchCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleContactPhotoCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullGroupCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleSolidContactCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleSolidContactListCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketArtifactCallback
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketCallback
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketListCallback
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * Hosts the AIDL data-module services (Contacts and Tickets) for third-party apps.
 *
 * Each stub is a thin adapter: it forwards straight to the corresponding in-process data
 * module and hands the resulting [com.erfangholami.androidsolidservices.shared.result.SolidResult]
 * to the AIDL callback. There is deliberately no model translation here — the IPC surface
 * speaks exactly the same types as the in-process API (`ContactData` / `SolidContact`,
 * `NewTicket` / `Ticket`), so the two surfaces cannot drift.
 */
@AndroidEntryPoint
class SolidDataModulesService : LifecycleService() {

    @Inject
    lateinit var contactsDataModule: SolidContactsDataModule

    @Inject
    lateinit var ticketsDataModule: SolidTicketsDataModule

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private val binder = object : IASSDataModulesService.Stub() {
        override fun getContactsDataModuleInterface(): IASSContactsModuleInterface =
            contactsModuleInterface

        override fun getTicketsDataModuleInterface(): IASSTicketsModuleInterface =
            ticketsModuleInterface
    }

    private val contactsModuleInterface = object : IASSContactsModuleInterface.Stub() {

        override fun listAddressBooks(
            webId: String,
            callback: IASSContactModuleAddressBookListCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.list(webId)
        }

        override fun ensureAddressBookContainer(
            webId: String,
            storage: String?,
            container: String?,
            callback: IASSContactModuleAddressBookListCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.ensureContainer(webId, storage, container)
        }

        override fun getAddressBook(
            webId: String,
            addressBookUri: String,
            callback: IASSContactModuleAddressBookCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.get(webId, addressBookUri)
        }

        override fun createAddressBook(
            webId: String,
            title: String,
            isPrivate: Boolean,
            storage: String?,
            container: String?,
            callback: IASSContactModuleAddressBookCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.create(webId, title, isPrivate, storage, container)
        }

        override fun renameAddressBook(
            webId: String,
            addressBookUri: String,
            newName: String,
            callback: IASSContactModuleAddressBookCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.rename(webId, addressBookUri, newName)
        }

        override fun deleteAddressBook(
            webId: String,
            addressBookUri: String,
            callback: IASSContactModuleAddressBookCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.delete(webId, addressBookUri)
        }

        override fun ensureDefaultAddressBook(
            webId: String,
            storage: String?,
            title: String,
            callback: IASSContactModuleAddressBookCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.books.ensureDefault(webId, storage, title)
        }

        override fun getContact(
            webId: String,
            contactUri: String,
            callback: IASSContactModuleSolidContactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.get(webId, contactUri)
        }

        override fun listContacts(
            webId: String,
            addressBookUri: String,
            callback: IASSContactModuleSolidContactListCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.list(webId, addressBookUri)
        }

        override fun createContact(
            webId: String,
            addressBookUri: String,
            data: ContactData,
            groupUris: MutableList<String>?,
            callback: IASSContactModuleSolidContactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.create(
                webId,
                addressBookUri,
                data,
                groupUris.orEmpty(),
            )
        }

        override fun updateContact(
            webId: String,
            addressBookUri: String,
            contactUri: String,
            data: ContactData,
            callback: IASSContactModuleSolidContactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.update(webId, addressBookUri, contactUri, data)
        }

        override fun deleteContact(
            webId: String,
            addressBookUri: String,
            contactUri: String,
            callback: IASSContactModuleSolidContactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.delete(webId, addressBookUri, contactUri)
        }

        override fun setContactPhoto(
            webId: String,
            contactUri: String,
            photo: ByteArray,
            contentType: String,
            callback: IASSContactModuleSolidContactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.setPhoto(webId, contactUri, photo, contentType)
        }

        override fun removeContactPhoto(
            webId: String,
            contactUri: String,
            callback: IASSContactModuleSolidContactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.removePhoto(webId, contactUri)
        }

        override fun getContactPhoto(
            webId: String,
            photoUri: String,
            callback: IASSContactModuleContactPhotoCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.getPhoto(webId, photoUri)
        }

        override fun findContactByWebId(
            webId: String,
            targetWebId: String,
            callback: IASSContactModuleContactMatchCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.contacts.findByWebId(webId, targetWebId)
        }

        override fun createGroup(
            webId: String,
            addressBookUri: String,
            title: String,
            contactUris: MutableList<String>?,
            callback: IASSContactModuleFullGroupCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.groups.create(
                webId,
                addressBookUri,
                title,
                contactUris.orEmpty(),
            )
        }

        override fun getGroup(
            webId: String,
            groupUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.groups.get(webId, groupUri)
        }

        override fun deleteGroup(
            webId: String,
            addressBookUri: String,
            groupUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.groups.delete(webId, addressBookUri, groupUri)
        }

        override fun addGroupMember(
            webId: String,
            groupUri: String,
            contactUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.groups.addMember(webId, groupUri, contactUri)
        }

        override fun removeGroupMember(
            webId: String,
            groupUri: String,
            contactUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            contactsDataModule.groups.removeMember(webId, groupUri, contactUri)
        }
    }

    private val ticketsModuleInterface = object : IASSTicketsModuleInterface.Stub() {

        override fun listTickets(webId: String, callback: IASSTicketListCallback) =
            dispatch(callback::onError, callback::onResult) {
                ticketsDataModule.tickets.list(webId)
            }

        override fun getTicket(
            webId: String,
            ticketUri: String,
            callback: IASSTicketCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            ticketsDataModule.tickets.get(webId, ticketUri)
        }

        override fun createTicket(
            webId: String,
            newTicket: NewTicket,
            storage: String?,
            artifact: ByteArray?,
            artifactContentType: String?,
            images: NewTicketImages?,
            isPrivate: Boolean,
            container: String?,
            callback: IASSTicketCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            ticketsDataModule.tickets.create(
                ownerWebId = webId,
                newTicket = newTicket,
                storage = storage,
                artifact = artifact,
                artifactContentType = artifactContentType,
                images = images,
                isPrivate = isPrivate,
                container = container,
            )
        }

        override fun updateTicket(
            webId: String,
            ticketUri: String,
            updated: NewTicket,
            callback: IASSTicketCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            ticketsDataModule.tickets.update(webId, ticketUri, updated)
        }

        override fun deleteTicket(
            webId: String,
            ticketUri: String,
            callback: IASSTicketCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            ticketsDataModule.tickets.delete(webId, ticketUri)
        }

        override fun getTicketArtifact(
            webId: String,
            artifactUri: String,
            callback: IASSTicketArtifactCallback,
        ) = dispatch(callback::onError, callback::onResult) {
            ticketsDataModule.tickets.getArtifact(webId, artifactUri)
        }
    }

    private fun <T : android.os.Parcelable> dispatch(
        onError: (Int, String) -> Unit,
        onSuccess: (T?) -> Unit,
        block: suspend () -> com.erfangholami.androidsolidservices.shared.result.SolidResult<T>,
    ) {
        lifecycleScope.dispatchDataModule(ioDispatcher, onError, onSuccess, block)
    }
}
