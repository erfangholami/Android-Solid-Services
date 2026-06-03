package com.erfangholami.androidsolidservices.services

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.erfangholami.androidsolidservices.di.IoDispatcher
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.ContactsRepository
import com.erfangholami.androidsolidservices.services.dispatch.dispatchDataModule
import com.erfangholami.androidsolidservices.services.dispatch.handle
import com.erfangholami.androidsolidservices.shared.IASSDataModulesService
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleAddressBookListCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullContactCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactModuleFullGroupCallback
import com.erfangholami.androidsolidservices.shared.model.contacts.IASSContactsModuleInterface
import com.erfangholami.androidsolidservices.shared.model.contacts.NewContact
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SolidDataModulesService : LifecycleService() {

    @Inject
    lateinit var contactsRepository: ContactsRepository

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private val binder = object : IASSDataModulesService.Stub() {
        override fun getContactsDataModuleInterface(): IASSContactsModuleInterface {
            return contactsModuleInterface
        }
    }

    private val contactsModuleInterface = object : IASSContactsModuleInterface.Stub() {

        override fun getAddressBooks(webId: String, callback: IASSContactModuleAddressBookListCallback) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.getAddressBooks(webId)
            }
        }

        override fun createAddressBook(
            webId: String,
            title: String,
            isPrivate: Boolean,
            callback: IASSContactModuleAddressBookCallback,
            storage: String?,
            ownerWebId: String?,
            container: String?
        ) {
            lifecycleScope.launch(ioDispatcher) {
                val profile = authRepository.getProfile(webId)
                val resolvedStorage = storage
                    ?: profile.webId?.getStorages()?.firstOrNull()?.toString()
                if (resolvedStorage == null) {
                    callback.onError(
                        ExceptionsErrorCode.NULL_WEBID,
                        "No storage available for $webId.",
                    )
                    return@launch
                }
                contactsRepository.createAddressBook(
                    ownerWebId ?: webId,
                    title,
                    isPrivate,
                    resolvedStorage,
                    container,
                ).handle(callback::onResult, callback::onError)
            }
        }

        override fun getAddressBook(
            webId: String,
            uri: String,
            callback: IASSContactModuleAddressBookCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.getAddressBook(webId, uri)
            }
        }

        override fun deleteAddressBook(
            webId: String,
            uri: String,
            ownerWebId: String?,
            callback: IASSContactModuleAddressBookCallback
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.deleteAddressBook(ownerWebId ?: webId, uri)
            }
        }

        override fun createNewContact(
            webId: String,
            addressBookUri: String,
            newContact: NewContact,
            groupUris: List<String>,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.createNewContact(webId, addressBookUri, newContact, groupUris)
            }
        }

        override fun getContact(
            webId: String,
            contactUri: String,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.getContact(webId, contactUri)
            }
        }

        override fun renameContact(
            webId: String,
            contactUri: String,
            newName: String,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.renameContact(webId, contactUri, newName)
            }
        }

        override fun addNewPhoneNumber(
            webId: String,
            contactUri: String,
            newPhoneNumber: String,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.addNewPhoneNumber(webId, contactUri, newPhoneNumber)
            }
        }

        override fun addNewEmailAddress(
            webId: String,
            contactUri: String,
            newEmailAddress: String,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.addNewEmailAddress(webId, contactUri, newEmailAddress)
            }
        }

        override fun removePhoneNumber(
            webId: String,
            contactUri: String,
            phoneNumber: String,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.removePhoneNumber(webId, contactUri, phoneNumber)
            }
        }

        override fun removeEmailAddress(
            webId: String,
            contactUri: String,
            emailAddress: String,
            callback: IASSContactModuleFullContactCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.removeEmailAddress(webId, contactUri, emailAddress)
            }
        }

        override fun deleteContact(
            webId: String,
            addressBookUri: String,
            contactUri: String,
            callback: IASSContactModuleFullContactCallback
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.deleteContact(webId, addressBookUri, contactUri)
            }
        }

        override fun createNewGroup(
            webId: String,
            addressBookUri: String,
            title: String,
            contactUris: List<String>,
            callback: IASSContactModuleFullGroupCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.createNewGroup(webId, addressBookUri, title, contactUris)
            }
        }

        override fun getGroup(
            webId: String,
            groupUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.getGroup(webId, groupUri)
            }
        }

        override fun deleteGroup(
            webId: String,
            addressBookUri: String,
            groupUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.deleteGroup(webId, addressBookUri, groupUri)
            }
        }

        override fun addContactToGroup(
            webId: String,
            contactUri: String,
            groupUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.addContactToGroup(webId, contactUri, groupUri)
            }
        }

        override fun removeContactFromGroup(
            webId: String,
            contactUri: String,
            groupUri: String,
            callback: IASSContactModuleFullGroupCallback,
        ) {
            lifecycleScope.dispatchDataModule(ioDispatcher, callback::onError, callback::onResult) {
                contactsRepository.removeContactFromGroup(webId, contactUri, groupUri)
            }
        }
    }
}
