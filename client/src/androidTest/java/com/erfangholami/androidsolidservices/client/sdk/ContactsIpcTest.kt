package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives every [SolidContactsDataModule] operation across a real binder.
 *
 * Two things here are only reachable this way. `ContactData` is a twenty-field vCard model that has
 * to survive parcelling intact — a partial round trip silently drops a phone number. And the module
 * reaches its service through a **sub-binder** handed back by the data-modules service, so the
 * returned `IBinder` must itself stay callable across the process boundary.
 */
@RunWith(AndroidJUnit4::class)
class ContactsIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val contacts: SolidContactsDataModule
        get() = SolidContactsDataModule.getInstance(sdk.context)

    @Test
    fun list_returns_both_index_halves(): Unit = runBlocking {
        assertEquals(Fixtures.ADDRESS_BOOK_LIST, contacts.books.list(Fixtures.WEB_ID))
        sdk.recorded("listAddressBooks").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun ensureContainer_passes_both_optional_locations(): Unit = runBlocking {
        contacts.books.ensureContainer(Fixtures.WEB_ID, Fixtures.STORAGE, Fixtures.CONTAINER)
        sdk.recorded("ensureAddressBookContainer").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "storage" to Fixtures.STORAGE,
            "container" to Fixtures.CONTAINER,
        )

        contacts.books.ensureContainer(Fixtures.WEB_ID)
        sdk.recorded("ensureAddressBookContainer").assertArgs("storage" to null, "container" to null)
    }

    @Test
    fun get_returns_the_book_with_its_contacts_and_groups(): Unit = runBlocking {
        val book = contacts.books.get(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK)

        assertEquals(Fixtures.ADDRESS_BOOK_MODEL, book)
        sdk.recorded("getAddressBook").assertArgs("addressBookUri" to Fixtures.ADDRESS_BOOK)
    }

    @Test
    fun create_defaults_to_the_private_type_index(): Unit = runBlocking {
        contacts.books.create(Fixtures.WEB_ID, "Work")
        sdk.recorded("createAddressBook").assertArgs(
            "title" to "Work",
            "isPrivate" to true,
            "storage" to null,
            "container" to null,
        )

        contacts.books.create(Fixtures.WEB_ID, "Public", isPrivate = false, storage = Fixtures.STORAGE)
        sdk.recorded("createAddressBook").assertArgs(
            "title" to "Public",
            "isPrivate" to false,
            "storage" to Fixtures.STORAGE,
        )
    }

    @Test
    fun rename_sends_the_uri_then_the_new_name(): Unit = runBlocking {
        contacts.books.rename(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK, "Personal")
        sdk.recorded("renameAddressBook").assertArgs(
            "addressBookUri" to Fixtures.ADDRESS_BOOK,
            "newName" to "Personal",
        )
    }

    @Test
    fun delete_sends_the_book_uri(): Unit = runBlocking {
        contacts.books.delete(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK)
        sdk.recorded("deleteAddressBook").assertArgs("addressBookUri" to Fixtures.ADDRESS_BOOK)
    }

    @Test
    fun ensureDefault_carries_the_default_title(): Unit = runBlocking {
        contacts.books.ensureDefault(Fixtures.WEB_ID)
        sdk.recorded("ensureDefaultAddressBook").assertArgs("storage" to null, "title" to "Contacts")

        contacts.books.ensureDefault(Fixtures.WEB_ID, Fixtures.STORAGE, "Address book")
        sdk.recorded("ensureDefaultAddressBook").assertArgs(
            "storage" to Fixtures.STORAGE,
            "title" to "Address book",
        )
    }

    @Test
    fun get_returns_the_contact_with_every_vcard_field_intact(): Unit = runBlocking {
        val contact = contacts.contacts.get(Fixtures.WEB_ID, Fixtures.CONTACT)

        assertEquals(Fixtures.SOLID_CONTACT, contact)
        assertEquals(Fixtures.CONTACT_DATA, contact?.data)
    }

    @Test
    fun list_returns_the_contact_list(): Unit = runBlocking {
        assertEquals(Fixtures.CONTACT_LIST, contacts.contacts.list(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK))
        sdk.recorded("listContacts").assertArgs("addressBookUri" to Fixtures.ADDRESS_BOOK)
    }

    @Test
    fun create_sends_the_full_ContactData_and_the_group_uris(): Unit = runBlocking {
        contacts.contacts.create(
            webId = Fixtures.WEB_ID,
            addressBookUri = Fixtures.ADDRESS_BOOK,
            data = Fixtures.CONTACT_DATA,
            groupUris = listOf(Fixtures.GROUP),
        )

        sdk.recorded("createContact").assertArgs(
            "addressBookUri" to Fixtures.ADDRESS_BOOK,
            "data" to Fixtures.CONTACT_DATA,
            "groupUris" to listOf(Fixtures.GROUP),
        )
    }

    @Test
    fun create_with_no_groups_sends_an_empty_list_not_null(): Unit = runBlocking {
        contacts.contacts.create(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK, Fixtures.CONTACT_DATA)
        sdk.recorded("createContact").assertArgs("groupUris" to emptyList<String>())
    }

    @Test
    fun update_sends_book_then_contact_then_data(): Unit = runBlocking {
        contacts.contacts.update(
            webId = Fixtures.WEB_ID,
            addressBookUri = Fixtures.ADDRESS_BOOK,
            contactUri = Fixtures.CONTACT,
            data = Fixtures.CONTACT_DATA,
        )

        sdk.recorded("updateContact").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "addressBookUri" to Fixtures.ADDRESS_BOOK,
            "contactUri" to Fixtures.CONTACT,
            "data" to Fixtures.CONTACT_DATA,
        )
    }

    @Test
    fun delete_sends_book_then_contact(): Unit = runBlocking {
        contacts.contacts.delete(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK, Fixtures.CONTACT)
        sdk.recorded("deleteContact").assertArgs(
            "addressBookUri" to Fixtures.ADDRESS_BOOK,
            "contactUri" to Fixtures.CONTACT,
        )
    }

    @Test
    fun setPhoto_carries_the_bytes_and_the_content_type(): Unit = runBlocking {
        contacts.contacts.setPhoto(
            webId = Fixtures.WEB_ID,
            contactUri = Fixtures.CONTACT,
            photo = Fixtures.PNG_BYTES,
            contentType = "image/png",
        )

        sdk.recorded("setContactPhoto").assertArgs(
            "contactUri" to Fixtures.CONTACT,
            "photo" to Fixtures.PNG_BYTES,
            "contentType" to "image/png",
        )
    }

    @Test
    fun removePhoto_sends_the_contact_uri(): Unit = runBlocking {
        contacts.contacts.removePhoto(Fixtures.WEB_ID, Fixtures.CONTACT)
        sdk.recorded("removeContactPhoto").assertArgs("contactUri" to Fixtures.CONTACT)
    }

    @Test
    fun getPhoto_returns_the_bytes_unchanged(): Unit = runBlocking {
        val photo = contacts.contacts.getPhoto(Fixtures.WEB_ID, Fixtures.BINARY)

        assertEquals(Fixtures.CONTACT_PHOTO, photo)
        assertTrue(Fixtures.PNG_BYTES.contentEquals(photo?.bytes))
        sdk.recorded("getContactPhoto").assertArgs("photoUri" to Fixtures.BINARY)
    }

    @Test
    fun findByWebId_sends_the_target_as_a_separate_argument(): Unit = runBlocking {
        val match = contacts.contacts.findByWebId(Fixtures.WEB_ID, Fixtures.PEER_WEB_ID)

        assertEquals(Fixtures.CONTACT_MATCH, match)
        sdk.recorded("findContactByWebId").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "targetWebId" to Fixtures.PEER_WEB_ID,
        )
    }

    @Test
    fun create_sends_the_title_and_seed_members(): Unit = runBlocking {
        contacts.groups.create(
            webId = Fixtures.WEB_ID,
            addressBookUri = Fixtures.ADDRESS_BOOK,
            title = "Team",
            contactUris = listOf(Fixtures.CONTACT),
        )

        sdk.recorded("createGroup").assertArgs(
            "addressBookUri" to Fixtures.ADDRESS_BOOK,
            "title" to "Team",
            "contactUris" to listOf(Fixtures.CONTACT),
        )
    }

    @Test
    fun get_returns_the_group_with_its_members(): Unit = runBlocking {
        assertEquals(Fixtures.FULL_GROUP, contacts.groups.get(Fixtures.WEB_ID, Fixtures.GROUP))
        sdk.recorded("getGroup").assertArgs("groupUri" to Fixtures.GROUP)
    }

    @Test
    fun delete_sends_book_then_group(): Unit = runBlocking {
        contacts.groups.delete(Fixtures.WEB_ID, Fixtures.ADDRESS_BOOK, Fixtures.GROUP)
        sdk.recorded("deleteGroup").assertArgs(
            "addressBookUri" to Fixtures.ADDRESS_BOOK,
            "groupUri" to Fixtures.GROUP,
        )
    }

    @Test
    fun addMember_sends_group_then_contact(): Unit = runBlocking {
        contacts.groups.addMember(Fixtures.WEB_ID, Fixtures.GROUP, Fixtures.CONTACT)
        sdk.recorded("addGroupMember").assertArgs(
            "groupUri" to Fixtures.GROUP,
            "contactUri" to Fixtures.CONTACT,
        )
    }

    @Test
    fun removeMember_sends_group_then_contact(): Unit = runBlocking {
        contacts.groups.removeMember(Fixtures.WEB_ID, Fixtures.GROUP, Fixtures.CONTACT)
        sdk.recorded("removeGroupMember").assertArgs(
            "groupUri" to Fixtures.GROUP,
            "contactUri" to Fixtures.CONTACT,
        )
    }

    @Test
    fun a_service_error_arrives_as_the_mapped_exception_type(): Unit = runBlocking {
        val thrown = runCatching { contacts.books.list(Fixtures.FAILING_WEB_ID) }.exceptionOrNull()

        assertTrue(
            "expected NotSupportedClassException, got $thrown",
            thrown is SolidException.SolidResourceException.NotSupportedClassException,
        )
    }
}
