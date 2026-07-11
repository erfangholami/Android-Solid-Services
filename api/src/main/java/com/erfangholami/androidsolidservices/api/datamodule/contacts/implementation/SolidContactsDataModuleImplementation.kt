package com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.GroupStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager

internal class SolidContactsDataModuleImplementation private constructor(
    resourceManager: SolidResourceManager,
) : SolidContactsDataModule {

    companion object {
        @Volatile
        private var INSTANCE: SolidContactsDataModule? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidContactsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModuleImplementation(
                    SolidResourceManager.getInstance(authenticator),
                ).also { INSTANCE = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidContactsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidContactsDataModuleImplementation(resourceManager)
                    .also { INSTANCE = it }
            }
        }
    }

    private val pod = ContactsPodAccess(resourceManager)
    private val groupEngine = GroupEngine(pod)

    override val books: AddressBookStore = AddressBookEngine(pod)
    override val contacts: ContactStore = ContactEngine(pod, groupEngine)
    override val groups: GroupStore = groupEngine
}
