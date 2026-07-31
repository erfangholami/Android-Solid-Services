package com.erfangholami.androidsolidservices.client.sdk

import android.os.ParcelFileDescriptor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * [SolidStream] hands the caller the read end of a live pipe. The pipe stays open until it is
 * closed, so a caller who forgets leaks a file descriptor on both sides — the documented contract
 * is `use { }`, and these tests pin that closing really does release it.
 */
@RunWith(RobolectricTestRunner::class)
class SolidStreamTest {

    private fun streamOf(payload: ByteArray, contentLength: Long = payload.size.toLong()): SolidStream {
        val pipe = ParcelFileDescriptor.createPipe()
        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(payload) }
        return SolidStream("text/turtle", contentLength, pipe[0])
    }

    @Test
    fun `the body arrives intact`() {
        val payload = "<#it> a <#Thing> .".toByteArray()
        streamOf(payload).use { body ->
            assertArrayEquals(payload, body.stream().readBytes())
        }
    }

    @Test
    fun `content type and length are exposed as given`() {
        streamOf(ByteArray(3), contentLength = -1L).use { body ->
            assertEquals("text/turtle", body.contentType)
            assertEquals("-1 is the documented 'server gave no length' value", -1L, body.contentLength)
        }
    }

    @Test
    fun `closing the stream closes the descriptor`() {
        val body = streamOf("abc".toByteArray())
        val stream = body.stream()
        body.close()

        assertThrows(
            "reading a closed SolidStream must fail rather than return stale or empty bytes",
            IOException::class.java,
        ) { stream.read() }
    }

    @Test
    fun `use closes the stream even when the block throws`() {
        val body = streamOf("abc".toByteArray())
        val stream = body.stream()

        runCatching { body.use { error("caller blew up mid-read") } }

        assertThrows(IOException::class.java) { stream.read() }
    }

    @Test
    fun `closing twice is harmless`() {
        val body = streamOf("abc".toByteArray())
        body.close()
        body.close()
    }
}
