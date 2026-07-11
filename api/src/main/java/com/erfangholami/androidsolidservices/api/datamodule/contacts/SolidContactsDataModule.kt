package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.SolidContactsDataModuleImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager

/**
 * Facade over the Solid contacts data module, split into three role interfaces:
 * [books] for address books, [contacts] for contact documents and photos, and
 * [groups] for `vcard:Group` membership.
 *
 * Contacts follow the SolidOS vCard layout: address books at
 * `{storage}contacts/{uuid}/index.ttl#this` with `people.ttl` / `groups.ttl`
 * indexes, individual contacts at `Person/{uuid}/index.ttl#this`, and books
 * registered in the user's type index for discovery by other Solid apps.
 *
 * Obtain an instance via [SolidContactsDataModule.getInstance].
 */
public interface SolidContactsDataModule {

    /** Address-book discovery and lifecycle. */
    public val books: AddressBookStore

    /** Contact reads, writes, photos, and WebID lookup. */
    public val contacts: ContactStore

    /** Group lifecycle and membership. */
    public val groups: GroupStore

    public companion object {
        /**
         * Returns the application-scoped singleton [SolidContactsDataModule]
         * built on [authenticator]'s resource manager.
         */
        public fun getInstance(authenticator: Authenticator): SolidContactsDataModule =
            SolidContactsDataModuleImplementation.getInstance(authenticator)

        /**
         * Returns the application-scoped singleton [SolidContactsDataModule]
         * built on [resourceManager].
         */
        public fun getInstance(resourceManager: SolidResourceManager): SolidContactsDataModule =
            SolidContactsDataModuleImplementation.getInstance(resourceManager)
    }
}
