package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasUpdateTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val docUri = URI.create("https://alice.pod/contacts/b1/people.ttl")
    private val subject = docUri.toString()
    private val predAlice = "https://example.org/vocab#alice"
    private val predBob = "https://example.org/vocab#bob"

    /**
     * A fake pod modeling a single document with a monotonic ETag version and an
     * `If-Match` precondition: [update] rejects a stale `ifMatch` with a 412, exactly
     * as a real server does. [beforeUpdate] lets a test inject a concurrent writer that
     * lands between the caller's read and its conditional write.
     */
    private class VersionedPod(
        initialQuads: List<RdfQuad>,
        private val weakEtags: Boolean = false,
    ) : SolidResourceManager {
        private var version = 1
        private var quads: List<RdfQuad> = initialQuads
        var updateCount = 0
            private set
        var lastIfMatch: String? = null
            private set
        var lastIfUnmodifiedSince: String? = null
            private set
        var beforeUpdate: (() -> Unit)? = null

        fun currentQuads(): List<RdfQuad> = quads

        /** Simulates another writer committing a change, bumping the validators out from under an in-flight CAS. */
        fun concurrentWrite(quad: RdfQuad) {
            version++
            quads = quads + quad
        }

        private fun etagValue(): String = if (weakEtags) "W/\"$version\"" else "\"$version\""

        // A monotonic stand-in for a wall clock — every write advances "modification time".
        private fun lastModified(): String = "lm-$version"

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Resource> read(
            webid: String,
            resource: URI,
            clazz: Class<T>,
        ): SolidResult<T> {
            val headers = SolidHeaders(
                mapOf(
                    "ETag" to listOf(etagValue()),
                    "Last-Modified" to listOf(lastModified()),
                ),
            )
            return SolidResult.Success(SolidRDFResource(resource, quads.toList(), headers) as T)
        }

        override suspend fun <T : Resource> update(
            webid: String,
            newResource: T,
            ifMatch: String?,
            ifUnmodifiedSince: String?,
        ): SolidResult<T> {
            beforeUpdate?.invoke()
            updateCount++
            lastIfMatch = ifMatch
            lastIfUnmodifiedSince = ifUnmodifiedSince
            val preconditionFails = when {
                ifMatch != null -> ifMatch != version.toString()
                ifUnmodifiedSince != null -> ifUnmodifiedSince != lastModified()
                else -> false
            }
            if (preconditionFails) {
                return SolidResult.Failure(SolidError.fromHttp(412, "precondition failed"))
            }
            version++
            quads = (newResource as SolidRDFResource).getAllQuads()
            return SolidResult.Success(newResource)
        }

        override suspend fun <T : Resource> create(webid: String, resource: T) = notImpl<T>()
        override suspend fun delete(webid: String, resourceUri: URI, ifMatch: String?) = notImpl<Boolean>()
        override suspend fun <T : Resource> delete(webid: String, resource: T) = notImpl<T>()
        override suspend fun head(webid: String, uri: URI) = notImpl<SolidMetadata>()
        override suspend fun headPublic(uri: URI) = notImpl<SolidMetadata>()
        override suspend fun <T : Resource> readPublic(uri: URI, clazz: Class<T>) = notImpl<T>()
        override suspend fun patch(webid: String, uri: URI, patch: N3Patch, ifMatch: String?) = notImpl<Unit>()
        override suspend fun patchRaw(webid: String, uri: URI, n3Body: String, ifMatch: String?) = notImpl<Unit>()
        override suspend fun putRaw(
            webid: String,
            uri: URI,
            contentType: String,
            body: ByteArray,
            ifMatch: String?,
            linkHeader: String?,
        ) = notImpl<Unit>()

        override suspend fun post(
            webid: String,
            uri: URI,
            contentType: String,
            body: ByteArray,
            additionalHeaders: Map<String, String>,
        ) = notImpl<URI?>()

        override suspend fun <T : Resource> createInContainer(
            webid: String,
            containerUri: URI,
            resource: T,
        ) = notImpl<URI?>()

        private fun <T> notImpl(): SolidResult<T> =
            SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised")))
    }

    private fun VersionedPod.hasQuad(predicate: String): Boolean =
        currentQuads().any { it.subject == subject && it.predicate == predicate }

    @Test
    fun `writes on the first attempt when nobody else changed the resource`() = runBlocking {
        val pod = VersionedPod(emptyList())

        val result = pod.casUpdate(
            webId,
            read = { pod.read(webId, docUri, SolidRDFResource::class.java) },
            mutate = { it.addQuadLiteral(subject, predAlice, "1"); true },
        )

        assertTrue(result is SolidResult.Success)
        assertEquals(1, pod.updateCount)
        assertTrue(pod.hasQuad(predAlice))
        // A strong ETag was available, so CAS conditioned on If-Match, not the coarser fallback.
        assertEquals("1", pod.lastIfMatch)
        assertNull(pod.lastIfUnmodifiedSince)
    }

    @Test
    fun `falls back to If-Unmodified-Since when the server emits only a weak ETag`() = runBlocking {
        val pod = VersionedPod(emptyList(), weakEtags = true)
        pod.beforeUpdate = {
            pod.beforeUpdate = null
            pod.concurrentWrite(RdfQuad(subject, predBob, "bob", null, null))
        }

        val result = pod.casUpdate(
            webId,
            read = { pod.read(webId, docUri, SolidRDFResource::class.java) },
            mutate = { it.addQuadLiteral(subject, predAlice, "alice"); true },
        )

        assertTrue(result is SolidResult.Success)
        // No strong ETag to send, so CAS conditioned on Last-Modified — and still caught the conflict.
        assertNull(pod.lastIfMatch)
        assertNotNull(pod.lastIfUnmodifiedSince)
        assertEquals(2, pod.updateCount)
        assertTrue(pod.hasQuad(predBob))
        assertTrue(pod.hasQuad(predAlice))
    }

    @Test
    fun `re-reads and retries on a 412, preserving the concurrent writer's change`() = runBlocking {
        val pod = VersionedPod(emptyList())
        pod.beforeUpdate = {
            // A different writer commits Bob's triple right before our first conditional
            // write evaluates — so our If-Match is now stale and the server answers 412.
            pod.beforeUpdate = null
            pod.concurrentWrite(RdfQuad(subject, predBob, "bob", null, null))
        }

        val result = pod.casUpdate(
            webId,
            read = { pod.read(webId, docUri, SolidRDFResource::class.java) },
            mutate = { it.addQuadLiteral(subject, predAlice, "alice"); true },
        )

        assertTrue(result is SolidResult.Success)
        assertEquals(2, pod.updateCount)
        // The retry saw Bob's triple and kept it: neither writer clobbered the other.
        assertTrue(pod.hasQuad(predBob))
        assertTrue(pod.hasQuad(predAlice))
    }

    @Test
    fun `skips the write and succeeds when mutate reports no change`() = runBlocking {
        val pod = VersionedPod(emptyList())

        val result = pod.casUpdate(
            webId,
            read = { pod.read(webId, docUri, SolidRDFResource::class.java) },
            mutate = { false },
        )

        assertTrue(result is SolidResult.Success)
        assertEquals(0, pod.updateCount)
    }

    @Test
    fun `gives up with a precondition failure after exhausting the retry budget`() = runBlocking {
        val pod = VersionedPod(emptyList())
        // Every attempt collides: a fresh concurrent write bumps the version each time.
        pod.beforeUpdate = { pod.concurrentWrite(RdfQuad(subject, predBob, "again", null, null)) }

        val result = pod.casUpdate(
            webId,
            maxAttempts = 3,
            read = { pod.read(webId, docUri, SolidRDFResource::class.java) },
            mutate = { it.addQuadLiteral(subject, predAlice, "alice"); true },
        )

        assertTrue(result is SolidResult.Failure)
        assertEquals(SolidErrorCode.PRECONDITION_FAILED, (result as SolidResult.Failure).error.code)
        assertEquals(3, pod.updateCount)
    }
}
