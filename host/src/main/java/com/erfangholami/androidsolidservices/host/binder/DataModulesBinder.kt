package com.erfangholami.androidsolidservices.host.binder

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.host.access.AccessCheck
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.ModuleRootResolver
import com.erfangholami.androidsolidservices.host.access.VerbAccess
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.dispatch.dispatchParcelable
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The `IASSDataModulesService` binder, hosting the contacts and tickets module interfaces.
 *
 * Each verb is checked on its module at [VerbAccess.READ], [VerbAccess.APPEND] or
 * [VerbAccess.WRITE]; a verb that takes an explicit container is checked on that container
 * instead, so a module-scoped app cannot place a book or a ticket outside the module. A verb
 * that can add a container tells [moduleRoots] to forget the account, so the next check sees it.
 * There is no model translation: the IPC surface speaks the in-process types.
 */
public class DataModulesBinder(
    private val contactsDataModule: SolidContactsDataModule,
    private val ticketsDataModule: SolidTicketsDataModule,
    private val guard: AccessGuard,
    private val moduleRoots: ModuleRootResolver,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IASSDataModulesService.Stub() {

    override fun getContactsDataModuleInterface(): IASSContactsModuleInterface = contactsModuleInterface

    override fun getTicketsDataModuleInterface(): IASSTicketsModuleInterface = ticketsModuleInterface

    private fun module(webId: String, moduleId: String, level: AccessLevel) =
        guard.gate(webId, VerbTarget.Module(moduleId), level)

    private fun moduleOrContainer(webId: String, moduleId: String, container: String?, level: AccessLevel) =
        if (container == null) module(webId, moduleId, level) else guard.gate(webId, VerbTarget.Resource(container), level)

    private fun <T : Parcelable> dispatch(
        callback: IASSParcelableCallback,
        gate: suspend () -> AccessCheck,
        block: suspend () -> SolidResult<T>,
    ) {
        scope.dispatchParcelable(dispatcher, callback, gate, block)
    }

    private fun <T : Parcelable> dispatchThenForget(
        webId: String,
        callback: IASSParcelableCallback,
        gate: suspend () -> AccessCheck,
        block: suspend () -> SolidResult<T>,
    ) {
        scope.dispatchParcelable(dispatcher, callback, gate) {
            block().also { moduleRoots.invalidate(webId) }
        }
    }

    private val contactsModuleInterface = object : IASSContactsModuleInterface.Stub() {

        private fun contacts(webId: String, level: AccessLevel) = module(webId, DataModuleId.CONTACTS, level)

        override fun listAddressBooks(webId: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.books.list(webId)
            }

        override fun ensureAddressBookContainer(
            webId: String,
            storage: String?,
            container: String?,
            callback: IASSParcelableCallback,
        ) = dispatchThenForget(
            webId,
            callback,
            moduleOrContainer(webId, DataModuleId.CONTACTS, container, VerbAccess.APPEND),
        ) {
            contactsDataModule.books.ensureContainer(webId, storage, container)
        }

        override fun getAddressBook(webId: String, addressBookUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.books.get(webId, addressBookUri)
            }

        override fun createAddressBook(
            webId: String,
            title: String,
            isPrivate: Boolean,
            storage: String?,
            container: String?,
            callback: IASSParcelableCallback,
        ) = dispatchThenForget(
            webId,
            callback,
            moduleOrContainer(webId, DataModuleId.CONTACTS, container, VerbAccess.APPEND),
        ) {
            contactsDataModule.books.create(webId, title, isPrivate, storage, container)
        }

        override fun renameAddressBook(
            webId: String,
            addressBookUri: String,
            newName: String,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.books.rename(webId, addressBookUri, newName)
        }

        override fun deleteAddressBook(webId: String, addressBookUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
                contactsDataModule.books.delete(webId, addressBookUri)
            }

        override fun ensureDefaultAddressBook(
            webId: String,
            storage: String?,
            title: String,
            callback: IASSParcelableCallback,
        ) = dispatchThenForget(webId, callback, contacts(webId, VerbAccess.APPEND)) {
            contactsDataModule.books.ensureDefault(webId, storage, title)
        }

        override fun getContact(webId: String, contactUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.contacts.get(webId, contactUri)
            }

        override fun listContacts(webId: String, addressBookUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.contacts.list(webId, addressBookUri)
            }

        override fun createContact(
            webId: String,
            addressBookUri: String,
            data: ContactData,
            groupUris: MutableList<String>?,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.APPEND)) {
            contactsDataModule.contacts.create(webId, addressBookUri, data, groupUris.orEmpty())
        }

        override fun updateContact(
            webId: String,
            addressBookUri: String,
            contactUri: String,
            data: ContactData,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.contacts.update(webId, addressBookUri, contactUri, data)
        }

        override fun deleteContact(
            webId: String,
            addressBookUri: String,
            contactUri: String,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.contacts.delete(webId, addressBookUri, contactUri)
        }

        override fun setContactPhoto(
            webId: String,
            contactUri: String,
            photo: ByteArray,
            contentType: String,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.contacts.setPhoto(webId, contactUri, photo, contentType)
        }

        override fun removeContactPhoto(webId: String, contactUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
                contactsDataModule.contacts.removePhoto(webId, contactUri)
            }

        override fun getContactPhoto(webId: String, photoUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.contacts.getPhoto(webId, photoUri)
            }

        override fun findContactByWebId(webId: String, targetWebId: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.contacts.findByWebId(webId, targetWebId)
            }

        override fun createGroup(
            webId: String,
            addressBookUri: String,
            title: String,
            contactUris: MutableList<String>?,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.APPEND)) {
            contactsDataModule.groups.create(webId, addressBookUri, title, contactUris.orEmpty())
        }

        override fun getGroup(webId: String, groupUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, contacts(webId, VerbAccess.READ)) {
                contactsDataModule.groups.get(webId, groupUri)
            }

        override fun deleteGroup(
            webId: String,
            addressBookUri: String,
            groupUri: String,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.groups.delete(webId, addressBookUri, groupUri)
        }

        override fun addGroupMember(
            webId: String,
            groupUri: String,
            contactUri: String,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.groups.addMember(webId, groupUri, contactUri)
        }

        override fun removeGroupMember(
            webId: String,
            groupUri: String,
            contactUri: String,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, contacts(webId, VerbAccess.WRITE)) {
            contactsDataModule.groups.removeMember(webId, groupUri, contactUri)
        }
    }

    private val ticketsModuleInterface = object : IASSTicketsModuleInterface.Stub() {

        private fun tickets(webId: String, level: AccessLevel) = module(webId, DataModuleId.TICKETS, level)

        override fun listTickets(webId: String, callback: IASSParcelableCallback) =
            dispatch(callback, tickets(webId, VerbAccess.READ)) {
                ticketsDataModule.tickets.list(webId)
            }

        override fun getTicket(webId: String, ticketUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, tickets(webId, VerbAccess.READ)) {
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
            callback: IASSParcelableCallback,
        ) = dispatchThenForget(
            webId,
            callback,
            moduleOrContainer(webId, DataModuleId.TICKETS, container, VerbAccess.APPEND),
        ) {
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
            callback: IASSParcelableCallback,
        ) = dispatch(callback, tickets(webId, VerbAccess.WRITE)) {
            ticketsDataModule.tickets.update(webId, ticketUri, updated)
        }

        override fun putTicketArtifact(
            webId: String,
            ticketUri: String,
            artifact: ByteArray,
            artifactContentType: String,
            images: NewTicketImages?,
            callback: IASSParcelableCallback,
        ) = dispatch(callback, tickets(webId, VerbAccess.WRITE)) {
            ticketsDataModule.tickets.putArtifact(webId, ticketUri, artifact, artifactContentType, images)
        }

        override fun deleteTicket(webId: String, ticketUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, tickets(webId, VerbAccess.WRITE)) {
                ticketsDataModule.tickets.delete(webId, ticketUri)
            }

        override fun getTicketArtifact(webId: String, artifactUri: String, callback: IASSParcelableCallback) =
            dispatch(callback, tickets(webId, VerbAccess.READ)) {
                ticketsDataModule.tickets.getArtifact(webId, artifactUri)
            }
    }
}
