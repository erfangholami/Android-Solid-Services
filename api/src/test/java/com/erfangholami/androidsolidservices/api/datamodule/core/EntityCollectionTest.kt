package com.erfangholami.androidsolidservices.api.datamodule.core

import com.erfangholami.androidsolidservices.api.testing.InMemoryPodResourceManager
import com.erfangholami.androidsolidservices.api.testing.inMemoryPod
import com.erfangholami.androidsolidservices.shared.model.datamodule.DATA_MODULE_ROOT
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_INDEX_NAME
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketsIndexRDF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntityCollectionTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val storage = "https://alice.pod/"
    private val privateIndexUri = "https://alice.pod/settings/privateTypeIndex"
    private val publicIndexUri = "https://alice.pod/settings/publicTypeIndex"
    private val root = "${storage}${DATA_MODULE_ROOT}widgets/"

    private val spec = CollectionSpec(
        registeredTypeIri = Schema.TICKET,
        entityTypeIri = Schema.TICKET,
        rootSuffix = "${DATA_MODULE_ROOT}widgets/",
        entityDocumentName = "widget",
        entityFragment = "#this",
        indexDocumentName = TICKETS_INDEX_NAME,
        indexCodec = TicketsIndexRDF::class.java,
        newIndex = { TicketsIndexRDF(it, "application/ld+json", null, null) },
    )

    private lateinit var fake: InMemoryPodResourceManager
    private lateinit var collection: EntityCollection<TicketsIndexRDF>

    @Before
    fun setUp() {
        fake = inMemoryPod(webId, privateIndexUri, publicIndexUri, conflictOnExistingCreate = true)
        collection = EntityCollection(fake, spec)
    }

    private fun privateIndex() = fake.store[privateIndexUri] as PrivateTypeIndex

    @Test
    fun `bootstrapping allocates under the data-module root and registers the index`() =
        runBlocking {
            val indexUri = collection.ensureIndex(webId, storage, isPrivate = true, container = null)

            assertEquals("$root$TICKETS_INDEX_NAME", indexUri)
            assertTrue(fake.store.containsKey(indexUri))
            assertEquals(listOf(indexUri), privateIndex().getInstances(Schema.TICKET))
        }

    @Test
    fun `an already registered index is reused rather than reallocated`() = runBlocking {
        val first = collection.ensureIndex(webId, storage, isPrivate = true, container = null)
        val second = collection.ensureIndex(webId, storage, isPrivate = true, container = null)

        assertEquals(first, second)
        assertEquals(listOf(first), privateIndex().getInstances(Schema.TICKET))
    }

    @Test
    fun `each entity is allocated its own container beside the index`() = runBlocking {
        val indexUri = collection.ensureIndex(webId, storage, isPrivate = true, container = null)

        val first = collection.allocateEntity(webId, indexUri)
        val second = collection.allocateEntity(webId, indexUri)

        assertNotEquals(first.container, second.container)
        assertTrue(first.container, first.container.startsWith(root))
        assertEquals("${first.container}widget", first.documentUri)
        assertEquals("${first.documentUri}#this", first.subjectUri)
        assertEquals(first.container, collection.entityContainerOf(first.documentUri))
    }

    @Test
    fun `the index of an entity is resolved by matching its collection container`() = runBlocking {
        val indexUri = collection.ensureIndex(webId, storage, isPrivate = true, container = null)
        val entity = collection.allocateEntity(webId, indexUri)

        assertEquals(indexUri, collection.indexFor(webId, entity.subjectUri))
    }

    @Test
    fun `an entity in a shared container is found by following its membership`() = runBlocking {
        val shared = "https://bob.pod/datamodule/widgets/w1/"
        val documentUri = "${shared}widget"
        fake.put(
            SolidContainer(
                shared,
                "text/turtle",
                listOf(RdfQuad(shared, LDP.CONTAINS, documentUri)),
                null,
            ),
        )

        val found = collection.findEntity(webId, shared)

        assertEquals(documentUri, found.documentUri)
        assertEquals("$documentUri#this", found.subjectUri)
    }

    @Test
    fun `an unconventionally named entity is still found by its type`() = runBlocking {
        val shared = "https://bob.pod/datamodule/widgets/w2/"
        val documentUri = "${shared}renamed.ttl"
        fake.put(
            SolidContainer(
                shared,
                "text/turtle",
                listOf(RdfQuad(shared, LDP.CONTAINS, documentUri)),
                null,
            ),
        )
        fake.put(
            TicketsIndexRDF(
                documentUri,
                "text/turtle",
                listOf(RdfQuad("$documentUri#it", RDF.TYPE, Schema.TICKET)),
                null,
            ),
        )

        val found = collection.findEntity(webId, shared)

        assertEquals("$documentUri#it", found.subjectUri)
    }
}
