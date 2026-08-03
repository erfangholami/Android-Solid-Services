package com.erfangholami.androidsolidservices.api.datamodule.tickets

import com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation.SolidTicketsDataModuleHelper
import com.erfangholami.androidsolidservices.api.datamodule.tickets.implementation.TicketEngine
import com.erfangholami.androidsolidservices.api.testing.InMemoryPodResourceManager
import com.erfangholami.androidsolidservices.api.testing.inMemoryPod
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketImages
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketsIndexRDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TicketEngineTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val storage = "https://alice.pod/"
    private val ticketsContainer = "https://alice.pod/datamodule/tickets/"
    private val indexUri = "${ticketsContainer}index"
    private val privateIndexUri = "https://alice.pod/settings/privateTypeIndex"
    private val publicIndexUri = "https://alice.pod/settings/publicTypeIndex"

    private lateinit var fake: InMemoryPodResourceManager
    private lateinit var engine: TicketEngine

    @Before
    fun setUp() {
        SolidTicketsDataModuleHelper.resetForTest()
        fake = inMemoryPod(webId, privateIndexUri, publicIndexUri, conflictOnExistingCreate = true)
        engine = TicketEngine(SolidTicketsDataModuleHelper.getInstance(fake))
    }

    private fun privateIndex() = fake.store[privateIndexUri] as PrivateTypeIndex

    private fun create(newTicket: NewTicket = NewTicket(title = "Concert")) = runBlocking {
        engine.create(webId, newTicket, storage = storage).getOrThrow()
    }

    @Test
    fun `create stores the ticket in its own container with attachments, index row and registration`() =
        runBlocking {
            val created = engine.create(
                ownerWebId = webId,
                newTicket = NewTicket(title = "Concert", category = TicketCategory.EVENT),
                storage = storage,
                artifact = byteArrayOf(1, 2, 3),
                artifactContentType = "application/vnd.apple.pkpass",
                images = NewTicketImages(logo = byteArrayOf(4), strip = byteArrayOf(5)),
            ).getOrThrow()

            assertTrue(
                created.uri,
                Regex("${Regex.escape(ticketsContainer)}[0-9a-f-]+/ticket#this").matches(created.uri),
            )
            val ticketDir = created.uri.removeSuffix("ticket#this")

            assertArrayEquals(byteArrayOf(1, 2, 3), fake.rawPuts["${ticketDir}artifact.pkpass"])
            assertArrayEquals(byteArrayOf(4), fake.rawPuts["${ticketDir}logo.png"])
            assertArrayEquals(byteArrayOf(5), fake.rawPuts["${ticketDir}strip.png"])
            assertFalse(fake.rawPuts.containsKey("${ticketDir}icon.png"))
            assertEquals("${ticketDir}artifact.pkpass", created.artifactUri)
            assertEquals(
                TicketImages(logo = "${ticketDir}logo.png", strip = "${ticketDir}strip.png"),
                created.images,
            )

            val index = fake.store[indexUri] as TicketsIndexRDF
            assertEquals(created.uri, index.getTickets().single().uri)
            assertEquals(listOf(indexUri), privateIndex().getInstances(Schema.TICKET))
        }

    @Test
    fun `list discovers tickets through the index instance registration`() = runBlocking {
        create()
        val listed = engine.list(webId).getOrThrow()
        assertEquals(listOf("Concert"), listed.tickets.map { it.title })
    }

    @Test
    fun `delete removes the whole per-ticket container and its index row`() = runBlocking {
        val created = engine.create(
            ownerWebId = webId,
            newTicket = NewTicket(title = "Concert"),
            storage = storage,
            artifact = byteArrayOf(9),
            artifactContentType = "application/vnd.apple.pkpass",
            images = NewTicketImages(logo = byteArrayOf(7)),
        ).getOrThrow()
        val ticketDir = created.uri.removeSuffix("ticket#this")

        engine.delete(webId, created.uri).getOrThrow()

        assertTrue(fake.deletedUris.contains(ticketDir))
        assertFalse(fake.store.keys.any { it.startsWith(ticketDir) })
        assertTrue((fake.store[indexUri] as TicketsIndexRDF).getTickets().isEmpty())
    }

    @Test
    fun `update refreshes the cached index row`() = runBlocking {
        val created = create()
        engine.update(webId, created.uri, NewTicket(title = "Concert (moved)")).getOrThrow()
        val index = fake.store[indexUri] as TicketsIndexRDF
        assertEquals("Concert (moved)", index.getTickets().single().title)
    }

    @Test
    fun `putArtifact rewrites the artifact and provided image roles keeping the rest`() =
        runBlocking {
            val created = engine.create(
                ownerWebId = webId,
                newTicket = NewTicket(title = "Concert"),
                storage = storage,
                artifact = byteArrayOf(1),
                artifactContentType = "application/vnd.apple.pkpass",
                images = NewTicketImages(logo = byteArrayOf(2), strip = byteArrayOf(3)),
            ).getOrThrow()
            val ticketDir = created.uri.removeSuffix("ticket#this")

            val refreshed = engine.putArtifact(
                ownerWebId = webId,
                ticketUri = created.uri,
                artifact = byteArrayOf(9),
                artifactContentType = "application/vnd.apple.pkpass",
                images = NewTicketImages(strip = byteArrayOf(8)),
            ).getOrThrow()

            assertArrayEquals(byteArrayOf(9), fake.rawPuts["${ticketDir}artifact.pkpass"])
            assertArrayEquals(byteArrayOf(8), fake.rawPuts["${ticketDir}strip.png"])
            assertArrayEquals(byteArrayOf(2), fake.rawPuts["${ticketDir}logo.png"])
            assertEquals(
                TicketImages(logo = "${ticketDir}logo.png", strip = "${ticketDir}strip.png"),
                refreshed.images,
            )
            assertEquals("${ticketDir}artifact.pkpass", refreshed.artifactUri)
        }
}
