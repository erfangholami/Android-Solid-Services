package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.GroupStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.CapturingCallback
import com.erfangholami.androidsolidservices.host.testing.FakeHostSession
import com.erfangholami.androidsolidservices.host.testing.FakeModuleRoots
import com.erfangholami.androidsolidservices.host.testing.Outcome
import com.erfangholami.androidsolidservices.host.testing.Recorder
import com.erfangholami.androidsolidservices.host.testing.RecordingPolicy
import com.erfangholami.androidsolidservices.host.testing.VerbTable
import com.erfangholami.androidsolidservices.host.testing.recording
import com.erfangholami.androidsolidservices.host.testing.verb
import com.erfangholami.androidsolidservices.shared.model.contacts.contactData
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Module verbs are checked on their module at View, Add or Edit; a verb that takes an explicit
 * container is checked on that container instead; and a verb that can add a container tells
 * the root resolver to forget the account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DataModulesBinderTest {

    private val session = FakeHostSession()
    private val policy = RecordingPolicy()
    private val recorder = Recorder()
    private val roots = FakeModuleRoots()
    private val scope = CoroutineScope(SupervisorJob())

    private val contactsModule = object : SolidContactsDataModule {
        override val books: AddressBookStore = recording(recorder)
        override val contacts: ContactStore = recording(recorder)
        override val groups: GroupStore = recording(recorder)
        override suspend fun rootContainers(ownerWebId: String): SolidResult<List<String>> = SolidResult.Success(emptyList())
    }

    private val ticketsModule = object : SolidTicketsDataModule {
        override val tickets: TicketStore = recording(recorder)
        override suspend fun rootContainers(ownerWebId: String): SolidResult<List<String>> = SolidResult.Success(emptyList())
    }

    private val binder = DataModulesBinder(
        contactsModule,
        ticketsModule,
        AccessGuard(session, policy) { CALLER },
        roots,
        scope,
        Dispatchers.Default,
    )
    private val contacts = binder.contactsDataModuleInterface
    private val tickets = binder.ticketsDataModuleInterface

    private val book = "https://alice.pod/datamodule/contacts/b1/index.ttl#this"
    private val contact = "https://alice.pod/datamodule/contacts/b1/Person/p1/index.ttl#this"
    private val group = "https://alice.pod/datamodule/contacts/b1/Group/g1/index.ttl#this"
    private val ticket = "https://alice.pod/datamodule/tickets/t1/ticket#this"
    private val data = contactData { fullName = "Jane Doe" }

    private fun contactsAt(level: AccessLevel) = VerbTarget.Module(DataModuleId.CONTACTS) to level
    private fun ticketsAt(level: AccessLevel) = VerbTarget.Module(DataModuleId.TICKETS) to level

    private fun single(call: (CapturingCallback) -> Unit): Outcome = CapturingCallback().also(call).await()

    private val verbs = listOf(
        verb("listAddressBooks", contactsAt(AccessLevel.VIEW), managerVerb = "list") { single { contacts.listAddressBooks(ALICE, it) } },
        verb("ensureAddressBookContainer", contactsAt(AccessLevel.ADD), managerVerb = "ensureContainer") {
            single { contacts.ensureAddressBookContainer(ALICE, null, null, it) }
        },
        verb("getAddressBook", contactsAt(AccessLevel.VIEW), managerVerb = "get") { single { contacts.getAddressBook(ALICE, book, it) } },
        verb("createAddressBook", contactsAt(AccessLevel.ADD), managerVerb = "create") {
            single { contacts.createAddressBook(ALICE, "Friends", true, null, null, it) }
        },
        verb("renameAddressBook", contactsAt(AccessLevel.EDIT), managerVerb = "rename") {
            single { contacts.renameAddressBook(ALICE, book, "Family", it) }
        },
        verb("deleteAddressBook", contactsAt(AccessLevel.EDIT), managerVerb = "delete") { single { contacts.deleteAddressBook(ALICE, book, it) } },
        verb("ensureDefaultAddressBook", contactsAt(AccessLevel.ADD), managerVerb = "ensureDefault") {
            single { contacts.ensureDefaultAddressBook(ALICE, null, "Contacts", it) }
        },
        verb("getContact", contactsAt(AccessLevel.VIEW), managerVerb = "get") { single { contacts.getContact(ALICE, contact, it) } },
        verb("listContacts", contactsAt(AccessLevel.VIEW), managerVerb = "list") { single { contacts.listContacts(ALICE, book, it) } },
        verb("createContact", contactsAt(AccessLevel.ADD), managerVerb = "create") {
            single { contacts.createContact(ALICE, book, data, mutableListOf(), it) }
        },
        verb("updateContact", contactsAt(AccessLevel.EDIT), managerVerb = "update") {
            single { contacts.updateContact(ALICE, book, contact, data, it) }
        },
        verb("deleteContact", contactsAt(AccessLevel.EDIT), managerVerb = "delete") { single { contacts.deleteContact(ALICE, book, contact, it) } },
        verb("setContactPhoto", contactsAt(AccessLevel.EDIT), managerVerb = "setPhoto") {
            single { contacts.setContactPhoto(ALICE, contact, byteArrayOf(), "image/png", it) }
        },
        verb("removeContactPhoto", contactsAt(AccessLevel.EDIT), managerVerb = "removePhoto") {
            single { contacts.removeContactPhoto(ALICE, contact, it) }
        },
        verb("getContactPhoto", contactsAt(AccessLevel.VIEW), managerVerb = "getPhoto") {
            single { contacts.getContactPhoto(ALICE, "$contact/photo.png", it) }
        },
        verb("findContactByWebId", contactsAt(AccessLevel.VIEW), managerVerb = "findByWebId") {
            single { contacts.findContactByWebId(ALICE, "https://bob.pod/profile/card#me", it) }
        },
        verb("createGroup", contactsAt(AccessLevel.ADD), managerVerb = "create") {
            single { contacts.createGroup(ALICE, book, "Team", mutableListOf(), it) }
        },
        verb("getGroup", contactsAt(AccessLevel.VIEW), managerVerb = "get") { single { contacts.getGroup(ALICE, group, it) } },
        verb("deleteGroup", contactsAt(AccessLevel.EDIT), managerVerb = "delete") { single { contacts.deleteGroup(ALICE, book, group, it) } },
        verb("addGroupMember", contactsAt(AccessLevel.EDIT), managerVerb = "addMember") {
            single { contacts.addGroupMember(ALICE, group, contact, it) }
        },
        verb("removeGroupMember", contactsAt(AccessLevel.EDIT), managerVerb = "removeMember") {
            single { contacts.removeGroupMember(ALICE, group, contact, it) }
        },
        verb("listTickets", ticketsAt(AccessLevel.VIEW), managerVerb = "list") { single { tickets.listTickets(ALICE, it) } },
        verb("getTicket", ticketsAt(AccessLevel.VIEW), managerVerb = "get") { single { tickets.getTicket(ALICE, ticket, it) } },
        verb("createTicket", ticketsAt(AccessLevel.ADD), managerVerb = "create") {
            single { tickets.createTicket(ALICE, NewTicket(title = "Concert"), null, null, null, null, true, null, it) }
        },
        verb("updateTicket", ticketsAt(AccessLevel.EDIT), managerVerb = "update") {
            single { tickets.updateTicket(ALICE, ticket, NewTicket(title = "Concert"), it) }
        },
        verb("putTicketArtifact", ticketsAt(AccessLevel.EDIT), managerVerb = "putArtifact") {
            single { tickets.putTicketArtifact(ALICE, ticket, byteArrayOf(), "application/pdf", null, it) }
        },
        verb("deleteTicket", ticketsAt(AccessLevel.EDIT), managerVerb = "delete") { single { tickets.deleteTicket(ALICE, ticket, it) } },
        verb("getTicketArtifact", ticketsAt(AccessLevel.VIEW), managerVerb = "getArtifact") {
            single { tickets.getTicketArtifact(ALICE, "https://alice.pod/datamodule/tickets/t1/artifact.pdf", it) }
        },
    )

    private val table = VerbTable(policy, recorder, verbs)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `every verb asks for its level on its module, then reaches the store`() {
        table.assertEveryVerbIsGuardedAndReachesItsManager()
    }

    @Test
    fun `a denied verb answers with the policy's code and never reaches the store`() {
        table.assertEveryDeniedVerbStopsBeforeItsManager()
    }

    @Test
    fun `an explicit container is checked as a resource instead of the module`() {
        val custom = "https://alice.pod/custom/"

        single { contacts.createAddressBook(ALICE, "Friends", true, null, custom, it) }
        single { tickets.createTicket(ALICE, NewTicket(title = "Concert"), null, null, null, null, true, custom, it) }

        assertEquals(
            listOf(VerbTarget.Resource(custom) to AccessLevel.ADD, VerbTarget.Resource(custom) to AccessLevel.ADD),
            policy.checks.map { it.target to it.level },
        )
    }

    @Test
    fun `verbs that can add a container tell the resolver to forget the account`() {
        single { contacts.ensureAddressBookContainer(ALICE, null, null, it) }
        single { contacts.createAddressBook(ALICE, "Friends", true, null, null, it) }
        single { contacts.ensureDefaultAddressBook(ALICE, null, "Contacts", it) }
        single { tickets.createTicket(ALICE, NewTicket(title = "Concert"), null, null, null, null, true, null, it) }
        single { contacts.listAddressBooks(ALICE, it) }
        single { tickets.updateTicket(ALICE, ticket, NewTicket(title = "Concert"), it) }

        assertEquals(listOf(ALICE, ALICE, ALICE, ALICE), roots.invalidated)
    }
}
