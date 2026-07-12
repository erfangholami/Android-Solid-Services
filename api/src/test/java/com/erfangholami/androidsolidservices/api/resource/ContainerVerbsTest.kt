package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * Tests the derived container verbs — [SolidResourceManager.listContainer],
 * [SolidResourceManager.copy], [SolidResourceManager.move], [SolidResourceManager.rename] —
 * over a filesystem-like in-memory pod that models GET (container listing + binary bytes),
 * HEAD, create, PUT, and recursive delete.
 */
class ContainerVerbsTest {

    private val webId = "https://alice.pod/profile/card#me"

    /** A pod modelling leaves (bytes + content-type) and containers, with a HEAD counter. */
    private class FsPod : SolidResourceManager {
        val files = linkedMapOf<String, Pair<ByteArray, String>>()
        val containers = linkedSetOf<String>()
        var headCount = 0
            private set

        fun putFile(uri: String, body: String, contentType: String = "text/plain") {
            files[uri] = body.toByteArray() to contentType
        }

        fun putContainer(uri: String) {
            containers += uri
        }

        fun bodyAt(uri: String): String? = files[uri]?.first?.let { String(it) }

        private fun directChildrenOf(container: String): List<String> =
            (files.keys + containers).filter { child ->
                if (!child.startsWith(container) || child == container) return@filter false
                val rest = child.removePrefix(container).trimEnd('/')
                rest.isNotEmpty() && !rest.contains('/')
            }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Resource> read(
            webId: String,
            resource: URI,
            clazz: Class<T>,
        ): SolidResult<T> {
            val uri = resource.toString()
            return if (SolidContainer::class.java.isAssignableFrom(clazz)) {
                if (uri !in containers) SolidResult.Failure(SolidError.fromHttp(404, uri))
                else {
                    val quads = directChildrenOf(uri).map { RdfQuad(uri, LDP.CONTAINS, it, null, null) }
                    SolidResult.Success(SolidContainer(URI.create(uri), quads) as T)
                }
            } else {
                val f = files[uri] ?: return SolidResult.Failure(SolidError.fromHttp(404, uri))
                SolidResult.Success(
                    SolidNonRDFResource(URI.create(uri), f.second, ByteArrayInputStream(f.first)) as T,
                )
            }
        }

        override suspend fun head(webId: String, uri: URI): SolidResult<SolidMetadata> {
            headCount++
            val s = uri.toString()
            return if (s in files || s in containers) SolidResult.Success(SolidMetadata.EMPTY)
            else SolidResult.Failure(SolidError.fromHttp(404, "not found: $s"))
        }

        override suspend fun <T : Resource> create(webId: String, resource: T): SolidResult<T> {
            if (resource is SolidContainer) containers += resource.getIdentifier().toString()
            return SolidResult.Success(resource)
        }

        override suspend fun putRaw(
            webId: String,
            uri: URI,
            contentType: String,
            body: ByteArray,
            ifMatch: String?,
            linkHeader: String?,
        ): SolidResult<Unit> {
            files[uri.toString()] = body to contentType
            return SolidResult.Success(Unit)
        }

        override suspend fun delete(webId: String, resourceUri: URI, ifMatch: String?): SolidResult<Boolean> {
            val uri = resourceUri.toString()
            files.remove(uri)
            containers.remove(uri)
            if (uri.endsWith("/")) {
                files.keys.filter { it.startsWith(uri) }.toList().forEach { files.remove(it) }
                containers.filter { it.startsWith(uri) }.toList().forEach { containers.remove(it) }
            }
            return SolidResult.Success(true)
        }

        override suspend fun <T : Resource> delete(webId: String, resource: T): SolidResult<T> {
            delete(webId, resource.getIdentifier(), null)
            return SolidResult.Success(resource)
        }

        override suspend fun <T : Resource> readPublic(uri: URI, clazz: Class<T>) = read("", uri, clazz)
        override suspend fun headPublic(uri: URI) = head("", uri)
        override suspend fun <T : Resource> update(webId: String, newResource: T, ifMatch: String?, ifUnmodifiedSince: String?) = notImpl<T>()
        override suspend fun patch(webId: String, uri: URI, patch: N3Patch, ifMatch: String?) = notImpl<Unit>()
        override suspend fun patchRaw(webId: String, uri: URI, n3Body: String, ifMatch: String?) = notImpl<Unit>()
        override suspend fun post(webId: String, uri: URI, contentType: String, body: ByteArray, additionalHeaders: Map<String, String>) = notImpl<URI?>()
        override suspend fun <T : Resource> createInContainer(webId: String, containerUri: URI, resource: T) = notImpl<URI?>()

        private fun <T> notImpl(): SolidResult<T> =
            SolidResult.Failure(SolidError.fromThrowable(NotImplementedError("not exercised")))
    }

    @Test
    fun `listContainer returns the children in a single GET with no per-child HEAD`() {
        val pod = FsPod().apply {
            putContainer("https://alice.pod/c/")
            putFile("https://alice.pod/c/a.txt", "A")
            putContainer("https://alice.pod/c/sub/")
        }

        val children = runBlocking { pod.listContainer(webId, URI.create("https://alice.pod/c/")).getOrThrow() }

        assertEquals(
            setOf("https://alice.pod/c/a.txt", "https://alice.pod/c/sub/"),
            children.map { it.identifier }.toSet(),
        )
        assertEquals("listing must not HEAD each child", 0, pod.headCount)
    }

    @Test
    fun `copy duplicates a single resource's bytes verbatim`() {
        val pod = FsPod().apply { putFile("https://alice.pod/a.txt", "hello", "text/plain") }

        val result = runBlocking {
            pod.copy(webId, URI.create("https://alice.pod/a.txt"), URI.create("https://alice.pod/b.txt"))
        }

        assertTrue(result is SolidResult.Success)
        assertEquals("hello", pod.bodyAt("https://alice.pod/b.txt"))
        assertEquals("source is left in place", "hello", pod.bodyAt("https://alice.pod/a.txt"))
    }

    @Test
    fun `copy recreates a whole container tree at the destination`() {
        val pod = FsPod().apply {
            putContainer("https://alice.pod/")
            putContainer("https://alice.pod/src/")
            putFile("https://alice.pod/src/a.txt", "AAA")
            putContainer("https://alice.pod/src/sub/")
            putFile("https://alice.pod/src/sub/b.txt", "BBB")
        }

        val result = runBlocking {
            pod.copy(webId, URI.create("https://alice.pod/src/"), URI.create("https://alice.pod/dst/"))
        }

        assertTrue(result is SolidResult.Success)
        assertEquals("AAA", pod.bodyAt("https://alice.pod/dst/a.txt"))
        assertEquals("BBB", pod.bodyAt("https://alice.pod/dst/sub/b.txt"))
        assertTrue("https://alice.pod/dst/" in pod.containers)
        assertTrue("https://alice.pod/dst/sub/" in pod.containers)
    }

    @Test
    fun `move copies then deletes the source`() {
        val pod = FsPod().apply { putFile("https://alice.pod/a.txt", "data") }

        val result = runBlocking {
            pod.move(webId, URI.create("https://alice.pod/a.txt"), URI.create("https://alice.pod/moved.txt"))
        }

        assertTrue(result is SolidResult.Success)
        assertEquals("data", pod.bodyAt("https://alice.pod/moved.txt"))
        assertNull("the source must be gone after a move", pod.bodyAt("https://alice.pod/a.txt"))
    }

    @Test
    fun `rename moves a resource to a sibling name in the same container`() {
        val pod = FsPod().apply { putFile("https://alice.pod/notes/old.txt", "n") }

        val result = runBlocking {
            pod.rename(webId, URI.create("https://alice.pod/notes/old.txt"), "new.txt")
        }

        assertEquals(URI.create("https://alice.pod/notes/new.txt"), (result as SolidResult.Success).value)
        assertEquals("n", pod.bodyAt("https://alice.pod/notes/new.txt"))
        assertFalse("https://alice.pod/notes/old.txt" in pod.files.keys)
    }
}
