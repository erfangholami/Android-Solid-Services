package com.erfangholami.androidsolidservices.services.dispatch

import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `a throwing unit block is reported too`() = runTest {
        var code: Int? = null

        val job = CoroutineScope(coroutineContext).dispatchUnit(
            dispatcher = StandardTestDispatcher(testScheduler),
            onError = { c, _ -> code = c },
            onResult = { throw AssertionError("the block threw; onResult must not run") },
        ) {
            throw IllegalStateException("boom")
        }
        job.join()

        assertEquals(ExceptionsErrorCode.UNKNOWN, code)
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
