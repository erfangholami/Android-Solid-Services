package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult

/**
 * Reads and writes the contacts of a pod user.
 *
 * Contacts are `vcard:Individual` documents inside an address book's container;
 * their writable state is the [ContactData] snapshot (build it with the
 * `contactData {}` DSL or derive it from an existing contact with `buildUpon {}`).
 */
public interface ContactStore {

    /** Reads the contact at [contactUri] with its complete vCard detail. */
    public suspend fun get(
        ownerWebId: String,
        contactUri: String,
    ): DataModuleResult<SolidContact>

    /**
     * Fetches every contact of the address book at [addressBookUri] with full detail.
     * Contact documents are fetched concurrently; the call is all-or-nothing.
     */
    public suspend fun getAll(
        ownerWebId: String,
        addressBookUri: String,
    ): DataModuleResult<SolidContactList>

    /**
     * Creates a contact from [data] in the address book at [addressBookUri], optionally
     * adding it to the groups in [groupUris]. When [ContactData.uid] is absent, a
     * `urn:uuid:` identifier is assigned. Fails when [data] resolves to no display name.
     */
    public suspend fun create(
        ownerWebId: String,
        addressBookUri: String,
        data: ContactData,
        groupUris: List<String> = emptyList(),
    ): DataModuleResult<SolidContact>

    /**
     * Rewrites the contact at [contactUri] from [data] with replace semantics:
     * properties absent from [data] are removed (the photo link is preserved — manage
     * it via [setPhoto] / [removePhoto]). When the display name changes, the address
     * book's people index and the cached member names in the book's groups are
     * refreshed to match.
     *
     * The persistent identifier (`vcard:hasUID`) is library-managed unless overridden:
     * when [ContactData.uid] is `null`, the contact's existing UID is carried forward
     * rather than removed; supply an explicit value to change it.
     *
     * @param addressBookUri The address book the contact belongs to (the contact
     *   document itself carries no back-link; only the book's people index does).
     */
    public suspend fun update(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
        data: ContactData,
    ): DataModuleResult<SolidContact>

    /**
     * Deletes the contact at [contactUri] from the address book at [addressBookUri]:
     * its people-index entry, its group memberships, and its whole `Person/{uuid}/`
     * container (including any photo binary). Returns the removed contact.
     */
    public suspend fun delete(
        ownerWebId: String,
        addressBookUri: String,
        contactUri: String,
    ): DataModuleResult<SolidContact>

    /**
     * Uploads (or overwrites) the contact's photo as a binary resource next to the
     * contact document and links it via `vcard:hasPhoto`.
     */
    public suspend fun setPhoto(
        ownerWebId: String,
        contactUri: String,
        photo: ByteArray,
        contentType: String,
    ): DataModuleResult<SolidContact>

    /** Deletes the contact's photo binary and removes its `vcard:hasPhoto` link. */
    public suspend fun removePhoto(
        ownerWebId: String,
        contactUri: String,
    ): DataModuleResult<SolidContact>

    /** Downloads the photo binary at [photoUri] (a `vcard:hasPhoto` target). */
    public suspend fun getPhoto(
        ownerWebId: String,
        photoUri: String,
    ): DataModuleResult<ContactPhoto>

    /**
     * Looks for a contact whose WebId-typed `vcard:url` equals [webId] across every
     * address book of [ownerWebId]. Intended for duplicate detection before adding a
     * scanned Solid profile. Costs one fetch per contact (O(books × contacts)).
     */
    public suspend fun findByWebId(
        ownerWebId: String,
        webId: String,
    ): DataModuleResult<ContactMatch>
}
