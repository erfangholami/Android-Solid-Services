package com.erfangholami.androidsolidservices.api.datamodule.typeindex

import com.erfangholami.androidsolidservices.api.notifications.FakeSolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeIndexResolverTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val profileDoc = "https://alice.pod/profile/card"
    private val storage = "https://alice.pod/"
    private val indexUri = "https://alice.pod/settings/privateTypeIndex.ttl"

    private val bookUri = "https://alice.pod/contacts/b1/index.ttl"
    private val ticketsContainer = "https://alice.pod/tickets/"

    private class VersionedIndexPod(
        private val webId: String,
        private val profileDoc: String,
        private val indexUri: String,
        private val profileQuads: List<RdfQuad>,
        initialIndexQuads: List<RdfQuad> = emptyList(),
    ) : SolidResourceManager {
        private var version = 1
        private var indexQuads: List<RdfQuad> = initialIndexQuads
        var updateCount = 0
            private set
        var lastIfMatch: String? = null
            private set
        var beforeUpdate: (() -> Unit)? = null

        fun index(): PrivateTypeIndex =
            PrivateTypeIndex(indexUri, "application/ld+json", indexQuads.toList(), null)

        fun concurrentRegistration(forClass: String, containerUri: String) {
            version++
            val subject = "$indexUri#other-app"
            indexQuads = indexQuads + listOf(
                RdfQuad(subject, RDF.TYPE, Solid.TYPE_REGISTRATION, null, null),
                RdfQuad(subject, Solid.FOR_CLASS, forClass, null, null),
                RdfQuad(subject, Solid.INSTANCE_CONTAINER, containerUri, null, null),
            )
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Resource> read(
            webId: String,
            resource: String,
            clazz: Class<T>,
        ): SolidResult<T> = when (resource) {
            this.webId, profileDoc -> SolidResult.Success(WebId(resource, profileQuads) as T)
            else -> {
                val headers = SolidHeaders(mapOf("ETag" to listOf("\"$version\"")))
                SolidResult.Success(
                    PrivateTypeIndex(resource, "application/ld+json", indexQuads.toList(), headers) as T
                )
            }
        }

        override suspend fun <T : Resource> update(
            webId: String,
            newResource: T,
            ifMatch: String?,
            ifUnmodifiedSince: String?,
        ): SolidResult<T> {
            beforeUpdate?.invoke()
            updateCount++
            lastIfMatch = ifMatch
            if (ifMatch != null && ifMatch != version.toString()) {
                return SolidResult.Failure(SolidError.fromHttp(412, "precondition failed"))
            }
            version++
            indexQuads = (newResource as SolidRDFResource).getAllQuads()
            return SolidResult.Success(newResource)
        }

        override suspend fun <T : Resource> create(webId: String, resource: T) = notImpl<T>()
        override suspend fun delete(webId: String, resourceUri: String, ifMatch: String?) = notImpl<Boolean>()
        override suspend fun <T : Resource> delete(webId: String, resource: T) = notImpl<T>()
        override suspend fun head(webId: String, uri: String) = SolidResult.Success(SolidMetadata.EMPTY)
        override suspend fun headPublic(uri: String) = notImpl<SolidMetadata>()
        override suspend fun <T : Resource> readPublic(uri: String, clazz: Class<T>) = notImpl<T>()
        override suspend fun patch(webId: String, uri: String, patch: N3Patch, ifMatch: String?) =
            SolidResult.Success(Unit)

        override suspend fun patchRaw(webId: String, uri: String, n3Body: String, ifMatch: String?) = notImpl<Unit>()
        override suspend fun putRaw(
            webId: String,
            uri: String,
            contentType: String,
            body: ByteArray,
            ifMatch: String?,
            linkHeader: String?,
        ) = notImpl<Unit>()

        override suspend fun post(
            webId: String,
            uri: String,
            contentType: String,
            body: ByteArray,
            additionalHeaders: Map<String, String>,
        ) = notImpl<String?>()

        override suspend fun <T : Resource> createInContainer(
            webId: String,
            containerUri: String,
            resource: T,
        ) = notImpl<String?>()

        private fun <T> notImpl(): SolidResult<T> =
            SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised")))
    }

    private fun linkedProfile() = listOf(
        RdfQuad(webId, PIM.STORAGE, storage, null, null),
        RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, profileDoc, null, null),
        RdfQuad(webId, Solid.PRIVATE_TYPE_INDEX, indexUri, null, null),
    )

    @Test
    fun `a registration racing another app's keeps both, instead of clobbering it`() = runBlocking {
        val pod = VersionedIndexPod(webId, profileDoc, indexUri, linkedProfile())
        pod.beforeUpdate = {
            pod.beforeUpdate = null
            pod.concurrentRegistration(Schema.TICKET, ticketsContainer)
        }

        TypeIndexResolver.addInstance(pod, webId, VCARD.ADDRESS_BOOK, bookUri, isPrivate = true)

        assertNotNull("the update must carry an If-Match precondition", pod.lastIfMatch)
        assertEquals("the 412 must be retried, not surfaced", 2, pod.updateCount)

        val index = pod.index()
        assertTrue(
            "our address book is registered",
            bookUri in index.getInstances(VCARD.ADDRESS_BOOK),
        )
        assertTrue(
            "the other app's registration survived — a blind write would have dropped it",
            ticketsContainer in index.getInstanceContainers(Schema.TICKET),
        )
    }

    @Test
    fun `re-registering an already-registered instance writes nothing`() = runBlocking {
        val pod = VersionedIndexPod(webId, profileDoc, indexUri, linkedProfile())
        TypeIndexResolver.addInstance(pod, webId, VCARD.ADDRESS_BOOK, bookUri, isPrivate = true)
        assertEquals(1, pod.updateCount)

        TypeIndexResolver.addInstance(pod, webId, VCARD.ADDRESS_BOOK, bookUri, isPrivate = true)

        assertEquals("the redundant registration is a no-op", 1, pod.updateCount)
        assertEquals(listOf(bookUri), pod.index().getInstances(VCARD.ADDRESS_BOOK))
    }

    @Test
    fun `removing an unregistered resource writes nothing`() = runBlocking {
        val pod = VersionedIndexPod(webId, profileDoc, indexUri, linkedProfile())

        TypeIndexResolver.removeResource(pod, webId, bookUri)

        assertEquals(0, pod.updateCount)
    }

    @Test
    fun `bootstrapping tolerates another app creating the index first`() {
        val profileQuads = listOf(
            RdfQuad(webId, PIM.STORAGE, storage, null, null),
            RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, profileDoc, null, null),
        )
        val rm = FakeSolidResourceManager().apply {
            onRead = { uri ->
                when (uri) {
                    webId, profileDoc -> SolidResult.Success(WebId(uri, profileQuads))
                    else -> SolidResult.Success(PrivateTypeIndex(uri, "application/ld+json", null, null))
                }
            }
            onCreate = { SolidResult.Failure(SolidError.fromHttp(409, "Resource already exists")) }
        }

        val index = runBlocking { TypeIndexResolver.getPrivateTypeIndex(rm, webId) }

        assertNotNull(index)
    }

    @Test
    fun `bootstrapping the private type index registers the link via PATCH, not a full-document PUT`() {
        val profileQuads = listOf(
            RdfQuad(webId, PIM.STORAGE, storage, null, null),
            RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, profileDoc, null, null),
        )
        val patched = mutableListOf<Pair<String, N3Patch>>()
        var updateCalled = false

        val rm = FakeSolidResourceManager().apply {
            onRead = { uri ->
                when (uri) {
                    webId, profileDoc -> SolidResult.Success(WebId(uri, profileQuads))
                    else -> SolidResult.Success(PrivateTypeIndex(uri, "application/ld+json", null, null))
                }
            }
            onHead = { SolidResult.Success(SolidMetadata.EMPTY) }
            onCreate = { SolidResult.Success(it) }
            onPatch = { uri, patch -> patched += uri to patch; SolidResult.Success(Unit) }
            onUpdate = { updateCalled = true; SolidResult.Success(it) }
        }

        runBlocking { TypeIndexResolver.getPrivateTypeIndex(rm, webId) }

        assertFalse("the profile must not be overwritten with a full-document PUT", updateCalled)
        assertEquals("the link is registered with exactly one PATCH", 1, patched.size)
        val (target, patch) = patched.single()
        assertEquals("the PATCH targets the profile document", profileDoc, target)
        assertTrue(
            "the PATCH inserts the solid:privateTypeIndex triple",
            patch.toN3String().contains(Solid.PRIVATE_TYPE_INDEX),
        )
    }
}
