package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.GroupStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.core.containerOf
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.model.contacts.CONTACTS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.result.solidCatching

internal class SolidContactsDataModuleImplementation private constructor(
    resourceManager: SolidResourceManager,
) : SolidContactsDataModule {

    companion object {
        @Volatile
        private var instance: SolidContactsDataModule? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidContactsDataModule {
            return instance ?: synchronized(this) {
                instance ?: SolidContactsDataModuleImplementation(
                    SolidResourceManager.getInstance(authenticator),
                ).also { instance = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidContactsDataModule {
            return instance ?: synchronized(this) {
                instance ?: SolidContactsDataModuleImplementation(resourceManager)
                    .also { instance = it }
            }
        }
    }

    private val pod = ContactsPodAccess(resourceManager)
    private val groupEngine = GroupEngine(pod)

    override val books: AddressBookStore = AddressBookEngine(pod)
    override val contacts: ContactStore = ContactEngine(pod, groupEngine)
    override val groups: GroupStore = groupEngine

    override suspend fun rootContainers(ownerWebId: String): SolidResult<List<String>> = solidCatching {
        val registered = books.list(ownerWebId).getOrThrow()
        val bookContainers = (registered.privateAddressBookUris + registered.publicAddressBookUris)
            .map { containerOf(it.substringBefore('#')) }
        val allocationRoot = StorageDiscovery.discover(pod.solidResourceManager, ownerWebId)
            ?.let { "$it$CONTACTS_DIRECTORY_SUFFIX" }
        (bookContainers + listOfNotNull(allocationRoot)).distinct()
    }
}
