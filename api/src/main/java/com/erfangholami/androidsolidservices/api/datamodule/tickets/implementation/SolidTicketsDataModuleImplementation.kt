package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.core.containerOf
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.result.solidCatching

internal class SolidTicketsDataModuleImplementation : SolidTicketsDataModule {

    companion object {
        @Volatile
        private var instance: SolidTicketsDataModule? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidTicketsDataModule {
            return instance ?: synchronized(this) {
                instance ?: SolidTicketsDataModuleImplementation(authenticator).also { instance = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidTicketsDataModule {
            return instance ?: synchronized(this) {
                instance ?: SolidTicketsDataModuleImplementation(resourceManager).also { instance = it }
            }
        }
    }

    override val tickets: TicketStore
    private val helper: SolidTicketsDataModuleHelper

    private constructor(authenticator: Authenticator) :
        this(SolidTicketsDataModuleHelper.getInstance(authenticator))

    private constructor(resourceManager: SolidResourceManager) :
        this(SolidTicketsDataModuleHelper.getInstance(resourceManager))

    private constructor(helper: SolidTicketsDataModuleHelper) {
        this.helper = helper
        this.tickets = TicketEngine(helper)
    }

    override suspend fun rootContainers(ownerWebId: String): SolidResult<List<String>> = solidCatching {
        val indexContainers = helper.resolveTicketIndexes(ownerWebId).map { containerOf(it) }
        val allocationRoot = StorageDiscovery.discover(helper.solidResourceManager, ownerWebId)
            ?.let { "$it$TICKETS_DIRECTORY_SUFFIX" }
        (indexContainers + listOfNotNull(allocationRoot)).distinct()
    }
}
