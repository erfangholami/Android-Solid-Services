package com.erfangholami.androidsolidservices.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.tickets.IASSTicketsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages

/**
 * Stands in for the ASS app's data-modules service, hosted in `:fakeass`.
 *
 * It carries the **production fully-qualified name** on purpose: `ServiceConnector` builds its
 * Intent from a fixed class name and only the package is redirectable, so a fake is reachable
 * only if it answers to the same FQCN.
 *
 * This one also covers a shape the other fakes do not: the SDK does not talk to this binder
 * directly but to a **sub-binder** it hands back, so a returned `IBinder` has to survive the trip
 * and stay callable.
 */
class SolidDataModulesService : Service() {

    private val contacts = object : IASSContactsModuleInterface.Stub() {

        override fun listAddressBooks(
            webId: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("listAddressBooks", "webId" to webId)
            callback.bookList(webId)
        }

        override fun ensureAddressBookContainer(
            webId: String?,
            storage: String?,
            container: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "ensureAddressBookContainer",
                "webId" to webId,
                "storage" to storage,
                "container" to container,
            )
            callback.bookList(webId)
        }

        override fun getAddressBook(
            webId: String?,
            addressBookUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("getAddressBook", "webId" to webId, "addressBookUri" to addressBookUri)
            callback.book(webId)
        }

        override fun createAddressBook(
            webId: String?,
            title: String?,
            isPrivate: Boolean,
            storage: String?,
            container: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createAddressBook",
                "webId" to webId,
                "title" to title,
                "isPrivate" to isPrivate,
                "storage" to storage,
                "container" to container,
            )
            callback.book(webId)
        }

        override fun renameAddressBook(
            webId: String?,
            addressBookUri: String?,
            newName: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "renameAddressBook",
                "webId" to webId,
                "addressBookUri" to addressBookUri,
                "newName" to newName,
            )
            callback.book(webId)
        }

        override fun deleteAddressBook(
            webId: String?,
            addressBookUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("deleteAddressBook", "webId" to webId, "addressBookUri" to addressBookUri)
            callback.book(webId)
        }

        override fun ensureDefaultAddressBook(
            webId: String?,
            storage: String?,
            title: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "ensureDefaultAddressBook",
                "webId" to webId,
                "storage" to storage,
                "title" to title,
            )
            callback.book(webId)
        }

        override fun getContact(
            webId: String?,
            contactUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("getContact", "webId" to webId, "contactUri" to contactUri)
            callback.contact(webId)
        }

        override fun listContacts(
            webId: String?,
            addressBookUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("listContacts", "webId" to webId, "addressBookUri" to addressBookUri)
            if (failing(webId)) callback?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.CONTACT_LIST))
        }

        override fun createContact(
            webId: String?,
            addressBookUri: String?,
            data: ContactData?,
            groupUris: MutableList<String>?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createContact",
                "webId" to webId,
                "addressBookUri" to addressBookUri,
                "data" to data,
                "groupUris" to groupUris,
            )
            callback.contact(webId)
        }

        override fun updateContact(
            webId: String?,
            addressBookUri: String?,
            contactUri: String?,
            data: ContactData?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "updateContact",
                "webId" to webId,
                "addressBookUri" to addressBookUri,
                "contactUri" to contactUri,
                "data" to data,
            )
            callback.contact(webId)
        }

        override fun deleteContact(
            webId: String?,
            addressBookUri: String?,
            contactUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "deleteContact",
                "webId" to webId,
                "addressBookUri" to addressBookUri,
                "contactUri" to contactUri,
            )
            callback.contact(webId)
        }

        override fun setContactPhoto(
            webId: String?,
            contactUri: String?,
            photo: ByteArray?,
            contentType: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "setContactPhoto",
                "webId" to webId,
                "contactUri" to contactUri,
                "photo" to photo,
                "contentType" to contentType,
            )
            callback.contact(webId)
        }

        override fun removeContactPhoto(
            webId: String?,
            contactUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("removeContactPhoto", "webId" to webId, "contactUri" to contactUri)
            callback.contact(webId)
        }

        override fun getContactPhoto(
            webId: String?,
            photoUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("getContactPhoto", "webId" to webId, "photoUri" to photoUri)
            if (failing(webId)) callback?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.CONTACT_PHOTO))
        }

        override fun findContactByWebId(
            webId: String?,
            targetWebId: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("findContactByWebId", "webId" to webId, "targetWebId" to targetWebId)
            if (failing(webId)) callback?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.CONTACT_MATCH))
        }

        override fun createGroup(
            webId: String?,
            addressBookUri: String?,
            title: String?,
            contactUris: MutableList<String>?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createGroup",
                "webId" to webId,
                "addressBookUri" to addressBookUri,
                "title" to title,
                "contactUris" to contactUris,
            )
            callback.group(webId)
        }

        override fun getGroup(
            webId: String?,
            groupUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("getGroup", "webId" to webId, "groupUri" to groupUri)
            callback.group(webId)
        }

        override fun deleteGroup(
            webId: String?,
            addressBookUri: String?,
            groupUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "deleteGroup",
                "webId" to webId,
                "addressBookUri" to addressBookUri,
                "groupUri" to groupUri,
            )
            callback.group(webId)
        }

        override fun addGroupMember(
            webId: String?,
            groupUri: String?,
            contactUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "addGroupMember",
                "webId" to webId,
                "groupUri" to groupUri,
                "contactUri" to contactUri,
            )
            callback.group(webId)
        }

        override fun removeGroupMember(
            webId: String?,
            groupUri: String?,
            contactUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "removeGroupMember",
                "webId" to webId,
                "groupUri" to groupUri,
                "contactUri" to contactUri,
            )
            callback.group(webId)
        }
    }

    private val tickets = object : IASSTicketsModuleInterface.Stub() {

        override fun listTickets(webId: String?, callback: IASSParcelableCallback?) {
            record("listTickets", "webId" to webId)
            if (failing(webId)) callback?.onError(TICKETS_ERROR, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.TICKET_LIST))
        }

        override fun getTicket(
            webId: String?,
            ticketUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("getTicket", "webId" to webId, "ticketUri" to ticketUri)
            callback.ticket(webId)
        }

        override fun createTicket(
            webId: String?,
            newTicket: NewTicket?,
            storage: String?,
            artifact: ByteArray?,
            artifactContentType: String?,
            images: NewTicketImages?,
            isPrivate: Boolean,
            container: String?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "createTicket",
                "webId" to webId,
                "newTicket" to newTicket,
                "storage" to storage,
                "artifact" to artifact,
                "artifactContentType" to artifactContentType,
                "images" to images.describe(),
                "isPrivate" to isPrivate,
                "container" to container,
            )
            callback.ticket(webId)
        }

        override fun updateTicket(
            webId: String?,
            ticketUri: String?,
            updated: NewTicket?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "updateTicket",
                "webId" to webId,
                "ticketUri" to ticketUri,
                "updated" to updated,
            )
            callback.ticket(webId)
        }

        override fun putTicketArtifact(
            webId: String?,
            ticketUri: String?,
            artifact: ByteArray?,
            artifactContentType: String?,
            images: NewTicketImages?,
            callback: IASSParcelableCallback?,
        ) {
            record(
                "putTicketArtifact",
                "webId" to webId,
                "ticketUri" to ticketUri,
                "artifact" to artifact,
                "artifactContentType" to artifactContentType,
                "images" to images.describe(),
            )
            callback.ticket(webId)
        }

        override fun deleteTicket(
            webId: String?,
            ticketUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("deleteTicket", "webId" to webId, "ticketUri" to ticketUri)
            callback.ticket(webId)
        }

        override fun getTicketArtifact(
            webId: String?,
            artifactUri: String?,
            callback: IASSParcelableCallback?,
        ) {
            record("getTicketArtifact", "webId" to webId, "artifactUri" to artifactUri)
            if (failing(webId)) callback?.onError(TICKETS_ERROR, Fixtures.ERROR_MESSAGE)
            else callback?.onResult(IpcEnvelope.of(Fixtures.TICKET_ARTIFACT))
        }
    }

    private val binder = object : IASSDataModulesService.Stub() {
        override fun getContactsDataModuleInterface(): IASSContactsModuleInterface = contacts
        override fun getTicketsDataModuleInterface(): IASSTicketsModuleInterface = tickets
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun record(method: String, vararg args: Pair<String, Any?>) =
        CallLog.record(applicationContext, method, *args)

    private fun failing(webId: String?) = webId == Fixtures.FAILING_WEB_ID

    private fun IASSParcelableCallback?.bookList(webId: String?) {
        if (failing(webId)) this?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.ADDRESS_BOOK_LIST))
    }

    private fun IASSParcelableCallback?.book(webId: String?) {
        if (failing(webId)) this?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.ADDRESS_BOOK_MODEL))
    }

    private fun IASSParcelableCallback?.contact(webId: String?) {
        if (failing(webId)) this?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.SOLID_CONTACT))
    }

    private fun IASSParcelableCallback?.group(webId: String?) {
        if (failing(webId)) this?.onError(CONTACTS_ERROR, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.FULL_GROUP))
    }

    private fun IASSParcelableCallback?.ticket(webId: String?) {
        if (failing(webId)) this?.onError(TICKETS_ERROR, Fixtures.ERROR_MESSAGE)
        else this?.onResult(IpcEnvelope.of(Fixtures.TICKET_MODEL))
    }

    /** [NewTicketImages] is not a data class, so its slots have to be spelled out to be asserted. */
    private fun NewTicketImages?.describe(): String = this?.let {
        listOf(
            "logo" to it.logo,
            "icon" to it.icon,
            "strip" to it.strip,
            "thumbnail" to it.thumbnail,
            "footer" to it.footer,
            "background" to it.background,
        ).joinToString(prefix = "{", postfix = "}") { (name, bytes) ->
            "$name=${CallLog.describe(bytes)}"
        }
    } ?: CallLog.NULL

    companion object {
        const val CONTACTS_ERROR: Int = ExceptionsErrorCode.NOT_SUPPORTED_CLASS
        const val TICKETS_ERROR: Int = ExceptionsErrorCode.NULL_WEBID
    }
}
