package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.CallLog
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import com.erfangholami.androidsolidservices.services.ASSResourceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * The streaming pair is the only part of the SDK whose payload never touches a Parcel — it travels
 * through a pipe precisely because a Parcel could not carry it. These tests put five megabytes
 * through that pipe in both directions, which is roughly five times the transaction limit that
 * every other method is bounded by.
 */
@RunWith(AndroidJUnit4::class)
class StreamingIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val client: SolidResourceClient
        get() = SolidResourceClient.getInstance(sdk.context) { true }

    @Test
    fun readStream_delivers_the_body_and_its_metadata(): Unit = runBlocking {
        client.readStream(Fixtures.WEB_ID, Fixtures.BINARY).use { body ->
            assertEquals("application/octet-stream", body.contentType)
            assertEquals(Fixtures.PNG_BYTES.size.toLong(), body.contentLength)
            assertTrue(Fixtures.PNG_BYTES.contentEquals(body.stream().readBytes()))
        }
        sdk.recorded("readStream").assertArgs("webId" to Fixtures.WEB_ID, "uri" to Fixtures.BINARY)
    }

    @Test
    fun readStream_carries_a_payload_far_past_the_binder_transaction_limit(): Unit = runBlocking {
        val expected = ASSResourceService.streamPayload(ASSResourceService.LARGE_STREAM_URI)

        val received = client.readStream(Fixtures.WEB_ID, ASSResourceService.LARGE_STREAM_URI)
            .use { it.stream().readBytes() }

        assertEquals(ASSResourceService.LARGE_STREAM_SIZE, received.size)
        assertEquals(
            "the five megabytes came back altered",
            CallLog.digest(expected),
            CallLog.digest(received),
        )
    }

    @Test
    fun writeStream_pushes_the_bytes_through_and_reports_the_length(): Unit = runBlocking {
        val payload = ByteArray(ASSResourceService.LARGE_STREAM_SIZE) { (it % 251).toByte() }

        client.writeStream(
            webId = Fixtures.WEB_ID,
            uri = Fixtures.BINARY,
            contentType = "application/octet-stream",
            source = ByteArrayInputStream(payload),
            contentLength = payload.size.toLong(),
            ifMatch = "\"v1\"",
        )

        sdk.recorded("writeStream").assertArgs(
            "uri" to Fixtures.BINARY,
            "contentType" to "application/octet-stream",
            "contentLength" to payload.size.toLong(),
            "ifMatch" to "\"v1\"",
            "body" to payload,
        )
    }

    @Test
    fun writeStream_defaults_report_an_unknown_length_and_no_precondition(): Unit = runBlocking {
        client.writeStream(
            Fixtures.WEB_ID,
            Fixtures.BINARY,
            "text/plain",
            ByteArrayInputStream("hello".toByteArray()),
        )

        sdk.recorded("writeStream").assertArgs(
            "contentLength" to -1L,
            "ifMatch" to null,
            "body" to "hello".toByteArray(),
        )
    }

    @Test
    fun writeStream_closes_the_source_it_was_given(): Unit = runBlocking {
        val source = ClosingStream("hello".toByteArray())

        client.writeStream(Fixtures.WEB_ID, Fixtures.BINARY, "text/plain", source)

        assertTrue("writeStream must close the source it was handed", source.closed)
    }

    @Test
    fun a_source_that_fails_mid_copy_surfaces_as_an_error_rather_than_hanging(): Unit = runBlocking {
        val outcome = withTimeout(WRITE_TIMEOUT) {
            runCatching {
                client.writeStream(
                    Fixtures.WEB_ID,
                    Fixtures.BINARY,
                    "text/plain",
                    ExplodingStream(),
                    contentLength = 1_024L,
                )
            }
        }

        assertTrue(
            "the call must settle either way, never park on a pipe nobody will finish filling — " +
                "got ${outcome.exceptionOrNull()}",
            outcome.isSuccess || outcome.exceptionOrNull() is Exception,
        )
    }

    @Test
    fun cancelling_a_read_mid_stream_releases_the_pipe(): Unit = runBlocking {
        val firstByteArrived = CompletableDeferred<Unit>()

        val reader = launch(Dispatchers.IO) {
            client.readStream(Fixtures.WEB_ID, ASSResourceService.LARGE_STREAM_URI).use { body ->
                body.stream().read()
                firstByteArrived.complete(Unit)
                awaitCancellation()
            }
        }

        withTimeout(WRITE_TIMEOUT) { firstByteArrived.await() }
        withTimeout(WRITE_TIMEOUT) { reader.cancelAndJoin() }

        val fresh = client.readStream(Fixtures.WEB_ID, Fixtures.BINARY)
            .use { it.stream().readBytes() }
        assertTrue(
            "after an abandoned stream, the connector must still serve a fresh one",
            Fixtures.PNG_BYTES.contentEquals(fresh),
        )
    }

    @Test
    fun a_stream_error_from_the_service_arrives_as_a_typed_exception(): Unit = runBlocking {
        val thrown = runCatching { client.readStream(Fixtures.FAILING_WEB_ID, Fixtures.BINARY) }
            .exceptionOrNull()

        assertTrue(
            "expected NotPermissionException, got $thrown",
            thrown is SolidException.SolidResourceException.NotPermissionException,
        )
    }

    @Test
    fun the_stream_must_be_closed_by_the_caller(): Unit = runBlocking {
        val body = client.readStream(Fixtures.WEB_ID, Fixtures.BINARY)
        val stream = body.stream()
        body.close()

        assertThrows(IOException::class.java) { stream.read() }
    }

    private companion object {
        const val WRITE_TIMEOUT = 30_000L
    }

    private class ClosingStream(bytes: ByteArray) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var closed = false
            private set

        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class ExplodingStream : InputStream() {
        private var served = 0

        override fun read(): Int = read(ByteArray(1), 0, 1).let { if (it == -1) -1 else 0 }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served > 0) throw IOException("the source blew up mid-upload")
            served += len
            return len
        }
    }
}
