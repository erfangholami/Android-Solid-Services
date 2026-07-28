package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.datamodule.contacts.InMemoryPodResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class StreamingFallbackTest {

    private val webId = "https://alice.pod/profile/card#me"

    @Test
    fun `default writeStream buffers the source, reports progress, and delegates to putRaw`() {
        val pod = InMemoryPodResourceManager()
        val uri = "https://alice.pod/f.txt"
        val progress = mutableListOf<Long>()

        val result = runBlocking {
            pod.writeStream(webId, uri.toString(), "text/plain", onProgress = { written, _ -> progress += written }) {
                "data".byteInputStream()
            }
        }

        assertTrue(result is SolidResult.Success)
        assertEquals("data", pod.rawPuts[uri.toString()]?.decodeToString())
        assertEquals(listOf(4L), progress)
    }

    @Test
    fun `default readStream wraps a buffered read`() {
        val pod = InMemoryPodResourceManager()
        val uri = "https://alice.pod/f.txt"
        pod.put(SolidNonRDFResource(uri, "text/plain", "hi".byteInputStream()))

        runBlocking {
            pod.readStream(webId, uri.toString()).getOrThrow().use { res ->
                assertEquals("text/plain", res.contentType)
                assertEquals("hi", res.stream().readBytes().decodeToString())
            }
        }
    }
}
