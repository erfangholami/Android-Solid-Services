package com.erfangholami.androidsolidservices.api.datamodule.tickets

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation.SolidTicketsDataModuleImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager

/**
 * Wallet tickets (`schema:Ticket` resources) on a user's Solid pod.
 *
 * A facade over the pod's ticket storage, mirroring the contacts module: reach the operations
 * through the [tickets] store (`module.tickets.list()`, `.get(uri)`, `.create(…)`, …). Tickets
 * live as one RDF document each inside a tickets container (`{storage}tickets/` by default),
 * registered in the user's type index as a `solid:instanceContainer` for `schema:Ticket` so
 * other Solid apps can discover it; the container and registration are bootstrapped on first use.
 *
 * Obtain an instance via [SolidTicketsDataModule.getInstance].
 */
public interface SolidTicketsDataModule {

    public companion object {
        /**
         * Returns the application-scoped singleton [SolidTicketsDataModule]
         * built on [authenticator]'s resource manager.
         */
        public fun getInstance(authenticator: Authenticator): SolidTicketsDataModule =
            SolidTicketsDataModuleImplementation.getInstance(authenticator)

        /**
         * Returns the application-scoped singleton [SolidTicketsDataModule]
         * built on [resourceManager].
         */
        public fun getInstance(resourceManager: SolidResourceManager): SolidTicketsDataModule =
            SolidTicketsDataModuleImplementation.getInstance(resourceManager)
    }

    /** The user's wallet-ticket store. */
    public val tickets: TicketStore
}
