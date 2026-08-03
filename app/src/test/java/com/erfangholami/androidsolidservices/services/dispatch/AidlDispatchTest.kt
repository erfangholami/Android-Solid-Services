package com.erfangholami.androidsolidservices.services.dispatch

import android.os.Bundle
import android.os.IBinder
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every AIDL method runs its work in a coroutine launched from the service's lifecycle scope, with
 * no exception handler. Anything that escapes that coroutine reaches the thread's default handler
 * and takes the whole app down — and because the AIDL call has already returned, the caller's
 * continuation is never resumed and parks indefinitely. One crash, one hung third-party app, and
 * no error anywhere.
 *
 * The services are exported, so the trigger need not be a well-behaved caller: an ordinal outside
 * `ShareMode.entries` used to be enough.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AidlDispatchTest {

    @Test
    fun `a throwing block is reported to the caller rather than escaping the coroutine`() = runTest {
        var code: Int? = null
        var message: String? = null

        val job = CoroutineScope(coroutineContext).dispatchNetwork(
            dispatcher = StandardTestDispatcher(testScheduler),
            onError = { c, m -> code = c; message = m },
            onSuccess = { _: String -> throw AssertionError("the block threw; success must not run") },
        ) {
            throw IllegalArgumentException("Unknown ShareMode ordinal 99")
        }
        job.join()

        assertEquals(ExceptionsErrorCode.UNKNOWN, code)
        assertTrue(
            "the caller should learn what was wrong, got: $message",
            message?.contains("99") == true,
        )
    }

    @Test
    fun `a throwing acknowledged block is reported too`() = runTest {
        val callback = RecordingCallback()

        val job = CoroutineScope(coroutineContext).dispatchAcknowledged<Unit>(
            dispatcher = StandardTestDispatcher(testScheduler),
            callback = callback,
        ) {
            throw IllegalStateException("boom")
        }
        job.join()

        assertEquals(ExceptionsErrorCode.UNKNOWN, callback.errorCode)
        assertNull("the block threw; no result envelope must be sent", callback.envelope)
    }

    @Test
    fun `an acknowledged block answers with an empty envelope`() = runTest {
        val callback = RecordingCallback()

        val job = CoroutineScope(coroutineContext).dispatchAcknowledged(
            dispatcher = StandardTestDispatcher(testScheduler),
            callback = callback,
        ) {
            SolidResult.Success(Unit)
        }
        job.join()

        assertNull("an acknowledgement carries no value", callback.errorCode)
        assertTrue("the caller must be answered", callback.envelope?.isEmpty == true)
    }

    private class RecordingCallback : IASSParcelableCallback {
        var envelope: Bundle? = null
        var errorCode: Int? = null

        override fun onResult(result: Bundle?) {
            envelope = result
        }

        override fun onError(errorCode: Int, errorMessage: String?) {
            this.errorCode = errorCode
        }

        override fun asBinder(): IBinder? = null
    }

    @Test
    fun `a successful block still reaches onSuccess`() = runTest {
        var value: String? = null

        val job = CoroutineScope(coroutineContext).dispatchNetwork(
            dispatcher = StandardTestDispatcher(testScheduler),
            onError = { _, _ -> throw AssertionError("no error expected") },
            onSuccess = { v: String -> value = v },
        ) {
            SolidResult.Success("ok")
        }
        job.join()

        assertEquals("ok", value)
    }

    @Test
    fun `an unknown mode ordinal is rejected with a message naming it`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            requireShareMode(ShareMode.entries.size)
        }
        assertTrue(thrown.message!!.contains("${ShareMode.entries.size}"))
    }

    @Test
    fun `a known mode ordinal decodes`() {
        ShareMode.entries.forEach { assertEquals(it, requireShareMode(it.ordinal)) }
    }

    @Test
    fun `an unknown receiver kind is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { requireShareReceiver(99, "https://x/#me") }
        assertThrows(IllegalArgumentException::class.java) {
            requireShareReceiver(ShareReceiver.KIND_WEBID, null)
        }
    }

    @Test
    fun `a known receiver decodes`() {
        assertEquals(
            ShareReceiver.Public,
            requireShareReceiver(ShareReceiver.KIND_PUBLIC, null),
        )
        assertEquals(
            ShareReceiver.WebIdReceiver("https://alice.pod.example/#me"),
            requireShareReceiver(ShareReceiver.KIND_WEBID, "https://alice.pod.example/#me"),
        )
    }
}
