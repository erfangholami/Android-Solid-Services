package com.erfangholami.androidsolidservices.api.datamodule.contacts

import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.AddressBookEngine
import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.ContactEngine
import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.ContactsPodAccess
import com.erfangholami.androidsolidservices.api.datamodule.contacts.implementation.GroupEngine
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType
import com.erfangholami.androidsolidservices.shared.model.contacts.contactData
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.contacts.AddressBookRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.GroupsIndexRDF
import com.erfangholami.androidsolidservices.shared.rdf.contacts.NameEmailIndexRDF
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import com.erfangholami.androidsolidservices.shared.result.getOrThrow
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContactEngineTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val storage = "https://alice.pod/"
    private val bookContainer = "https://alice.pod/contacts/b1/"
    private val bookUri = "${bookContainer}index.ttl#this"
    private val peopleUri = "${bookContainer}people.ttl"
    private val groupsUri = "${bookContainer}groups.ttl"
    private val privateIndexUri = "https://alice.pod/settings/privateTypeIndex"
    private val publicIndexUri = "https://alice.pod/settings/publicTypeIndex"
    private val janeWebId = "https://jane.pod/profile/card#me"

    private lateinit var fake: InMemoryPodResourceManager
    private lateinit var contactEngine: ContactEngine
    private lateinit var groupEngine: GroupEngine
    private lateinit var bookEngine: AddressBookEngine

    @Before
    fun setUp() {
        fake = InMemoryPodResourceManager()
        val pod = ContactsPodAccess(fake)
        groupEngine = GroupEngine(pod)
        contactEngine = ContactEngine(pod, groupEngine)
        bookEngine = AddressBookEngine(pod)

        fake.put(
            WebId(
                URI.create(webId),
                listOf(
                    RdfQuad(webId, Solid.PRIVATE_TYPE_INDEX, privateIndexUri),
                    RdfQuad(webId, Solid.PUBLIC_TYPE_INDEX, publicIndexUri),
                ),
            ),
        )
        fake.put(
            PrivateTypeIndex(URI.create(privateIndexUri), "application/ld+json", null, null)
                .apply { addAddressBook(bookUri) },
        )
        fake.put(
            PublicTypeIndex(URI.create(publicIndexUri), "application/ld+json", null, null),
        )
        fake.put(
            AddressBookRDF(
                identifier = URI.create(bookUri),
                contentType = "application/ld+json",
                quads = null,
                headers = null,
            ).apply {
                setOwner(webId)
                setTitle("Contacts")
                setNameEmailIndex(peopleUri)
                setGroupsIndex(groupsUri)
            },
        )
        fake.put(NameEmailIndexRDF(URI.create(peopleUri)))
        fake.put(GroupsIndexRDF(URI.create(groupsUri)))
    }

    private fun createJane() = runBlocking {
        contactEngine.create(
            ownerWebId = webId,
            addressBookUri = bookUri,
            data = contactData {
                fullName = "Jane"
                phone("+31612345678", PhoneType.CELL)
                webId(janeWebId)
            },
        ).getOrThrow()
    }

    @Test
    fun `create registers the contact in the people index and assigns a uid`() = runBlocking {
        val created = createJane()
        assertTrue(created.uri.startsWith("${bookContainer}Person/"))
        assertNotNull(created.data.uid)
        assertTrue(created.data.uid!!.startsWith("urn:uuid:"))
        val people = fake.store[peopleUri] as NameEmailIndexRDF
        assertEquals("Jane", people.getContacts(bookUri).single().name)
    }

    @Test
    fun `update refreshes cached names and preserves the photo link`() = runBlocking {
        val created = createJane()
        groupEngine.create(webId, bookUri, "Friends", listOf(created.uri)).getOrThrow()
        contactEngine.setPhoto(webId, created.uri, byteArrayOf(1, 2, 3), "image/jpeg")
            .getOrThrow()

        val updated = contactEngine.update(
            ownerWebId = webId,
            addressBookUri = bookUri,
            contactUri = created.uri,
            data = created.data.copy(fullName = "Jane Renamed"),
        ).getOrThrow()

        assertNotNull(updated.photoUri)
        val people = fake.store[peopleUri] as NameEmailIndexRDF
        assertEquals("Jane Renamed", people.getContacts(bookUri).single().name)
        val group = fake.store.values.filterIsInstance<GroupRDF>().single()
        assertEquals("Jane Renamed", group.getContacts().single().name)
    }

    @Test
    fun `delete cleans the people index, group memberships, and the contact container`() =
        runBlocking {
            val created = createJane()
            groupEngine.create(webId, bookUri, "Friends", listOf(created.uri)).getOrThrow()

            contactEngine.delete(webId, bookUri, created.uri).getOrThrow()

            val people = fake.store[peopleUri] as NameEmailIndexRDF
            assertEquals(0, people.getContacts(bookUri).size)
            val group = fake.store.values.filterIsInstance<GroupRDF>().single()
            assertEquals(0, group.getContacts().size)
            assertTrue(
                fake.deletedUris.any {
                    it.startsWith("${bookContainer}Person/") && it.endsWith("/")
                },
            )
        }

    @Test
    fun `delete surfaces failure and keeps the index row when the container delete fails`() =
        runBlocking {
            val created = createJane()
            val contactContainer = created.uri.substring(0, created.uri.lastIndexOf('/') + 1)
            fake.failDeletesFor.add(contactContainer)

            val result = contactEngine.delete(webId, bookUri, created.uri)

            assertTrue(result is DataModuleResult.Exception)
            val people = fake.store[peopleUri] as NameEmailIndexRDF
            assertEquals(1, people.getContacts(bookUri).size)
        }

    @Test
    fun `update without a uid preserves the existing contact uid`() = runBlocking {
        val created = createJane()
        val originalUid = created.data.uid
        assertNotNull(originalUid)

        val updated = contactEngine.update(
            ownerWebId = webId,
            addressBookUri = bookUri,
            contactUri = created.uri,
            data = created.data.copy(uid = null, fullName = "Jane Renamed"),
        ).getOrThrow()

        assertEquals(originalUid, updated.data.uid)
    }

    @Test
    fun `findByWebId returns the match with its book and misses cleanly`() = runBlocking {
        createJane()
        val match = contactEngine.findByWebId(webId, janeWebId).getOrThrow()
        assertTrue(match.exists())
        assertEquals(bookUri, match.addressBookUri)
        assertEquals(janeWebId, match.contact?.data?.webId())

        val miss = contactEngine.findByWebId(webId, "https://ghost.pod/profile/card#me")
            .getOrThrow()
        assertFalse(miss.exists())
    }

    @Test
    fun `ensureDefault returns the existing private book without creating another`() =
        runBlocking {
            val book = bookEngine.ensureDefault(webId, storage).getOrThrow()
            assertEquals(bookUri, book.uri)
            val typeIndex = fake.store[privateIndexUri] as PrivateTypeIndex
            assertEquals(listOf(bookUri), typeIndex.getAddressBooks())
        }

    @Test
    fun `ensureDefault creates a private book when none exists`() = runBlocking {
        val typeIndex = fake.store[privateIndexUri] as PrivateTypeIndex
        typeIndex.removeAddressBook(bookUri)

        val book = bookEngine.ensureDefault(webId, storage, title = "Contacts").getOrThrow()
        assertEquals("Contacts", book.title)
        assertTrue(book.uri.startsWith("${storage}contacts/"))
        val refreshedIndex = fake.store[privateIndexUri] as PrivateTypeIndex
        assertEquals(listOf(book.uri), refreshedIndex.getAddressBooks())
    }

    @Test
    fun `getAll returns empty when the people index is missing`() = runBlocking {
        fake.store.remove(peopleUri)
        assertTrue(contactEngine.getAll(webId, bookUri).getOrThrow().contacts.isEmpty())
    }

    @Test
    fun `getAll returns empty when the address book is missing`() = runBlocking {
        fake.store.remove(bookUri)
        assertTrue(contactEngine.getAll(webId, bookUri).getOrThrow().contacts.isEmpty())
    }

    @Test
    fun `get returns empty contacts and groups when the indexes are missing`() = runBlocking {
        fake.store.remove(peopleUri)
        fake.store.remove(groupsUri)
        val book = bookEngine.get(webId, bookUri).getOrThrow()
        assertTrue(book.contacts.isEmpty())
        assertTrue(book.groups.isEmpty())
    }

    @Test
    fun `list returns empty when the type indexes are unreadable`() = runBlocking {
        fake.store.remove(privateIndexUri)
        fake.store.remove(publicIndexUri)
        val list = bookEngine.list(webId).getOrThrow()
        assertTrue(list.privateAddressBookUris.isEmpty())
        assertTrue(list.publicAddressBookUris.isEmpty())
    }

    @Test
    fun `ensureContainer creates the contacts container and returns the book list`() = runBlocking {
        assertFalse(fake.store.containsKey("${storage}contacts/"))
        val list = bookEngine.ensureContainer(webId, storage).getOrThrow()
        assertTrue(fake.store.containsKey("${storage}contacts/"))
        assertEquals(listOf(bookUri), list.privateAddressBookUris)
    }

    @Test
    fun `create provisions the nested contact containers`() = runBlocking {
        assertFalse(fake.store.keys.any { it.endsWith("/Person/") })
        val created = createJane()
        val contactContainer = created.uri.substring(0, created.uri.lastIndexOf('/') + 1)
        assertTrue(fake.store.containsKey(contactContainer))
        assertTrue(fake.store.keys.any { it.endsWith("/Person/") })
    }

    @Test
    fun `book delete removes the whole container and its type-index registration`() = runBlocking {
        createJane()
        bookEngine.delete(webId, bookUri).getOrThrow()
        assertTrue(fake.store.keys.none { it.startsWith(bookContainer) })
        val typeIndex = fake.store[privateIndexUri] as PrivateTypeIndex
        assertFalse(typeIndex.getAddressBooks().contains(bookUri))
    }

    @Test
    fun `book delete keeps the type-index registration when the container delete fails`() =
        runBlocking {
            createJane()
            fake.failDeletesFor.add(bookContainer)

            val result = bookEngine.delete(webId, bookUri)

            assertTrue(result is DataModuleResult.Exception)
            val typeIndex = fake.store[privateIndexUri] as PrivateTypeIndex
            assertTrue(typeIndex.getAddressBooks().contains(bookUri))
        }

    @Test
    fun `create book provisions the book container`() = runBlocking {
        (fake.store[privateIndexUri] as PrivateTypeIndex).removeAddressBook(bookUri)
        val book = bookEngine.ensureDefault(webId, storage, title = "Contacts").getOrThrow()
        val newBookContainer = book.uri.substring(0, book.uri.lastIndexOf('/') + 1)
        assertTrue(fake.store.containsKey(newBookContainer))
        assertTrue(fake.store.containsKey("${storage}contacts/"))
    }
}
