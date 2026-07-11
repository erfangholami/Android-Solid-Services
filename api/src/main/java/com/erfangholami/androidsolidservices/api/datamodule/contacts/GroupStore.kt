package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult

/**
 * Manages the contact groups (`vcard:Group`) of an address book and their
 * memberships.
 */
public interface GroupStore {

    /**
     * Creates a group titled [title] in the address book at [addressBookUri],
     * optionally adding the contacts in [contactUris] as members.
     */
    public suspend fun create(
        ownerWebId: String,
        addressBookUri: String,
        title: String,
        contactUris: List<String> = emptyList(),
    ): DataModuleResult<FullGroup>

    /** Reads the group at [groupUri] with its member summaries. */
    public suspend fun get(
        ownerWebId: String,
        groupUri: String,
    ): DataModuleResult<FullGroup>

    /** Deletes the group at [groupUri] from the address book at [addressBookUri]. */
    public suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
        groupUri: String,
    ): DataModuleResult<FullGroup>

    /** Adds the contact at [contactUri] to the group at [groupUri]. */
    public suspend fun addMember(
        ownerWebId: String,
        groupUri: String,
        contactUri: String,
    ): DataModuleResult<FullGroup>

    /** Removes the contact at [contactUri] from the group at [groupUri]. */
    public suspend fun removeMember(
        ownerWebId: String,
        groupUri: String,
        contactUri: String,
    ): DataModuleResult<FullGroup>
}
