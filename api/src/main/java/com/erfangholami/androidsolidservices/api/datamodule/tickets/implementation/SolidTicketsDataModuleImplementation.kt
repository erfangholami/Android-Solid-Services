package com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager

internal class SolidTicketsDataModuleImplementation : SolidTicketsDataModule {

    companion object {
        @Volatile
        private var INSTANCE: SolidTicketsDataModule? = null

        fun getInstance(
            authenticator: Authenticator,
        ): SolidTicketsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidTicketsDataModuleImplementation(authenticator).also { INSTANCE = it }
            }
        }

        fun getInstance(
            resourceManager: SolidResourceManager,
        ): SolidTicketsDataModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolidTicketsDataModuleImplementation(resourceManager).also { INSTANCE = it }
            }
        }
    }

    override val tickets: TicketStore

    private constructor(authenticator: Authenticator) {
        this.tickets = TicketEngine(SolidTicketsDataModuleHelper.getInstance(authenticator))
    }

    private constructor(resourceManager: SolidResourceManager) {
        this.tickets = TicketEngine(SolidTicketsDataModuleHelper.getInstance(resourceManager))
    }
}
