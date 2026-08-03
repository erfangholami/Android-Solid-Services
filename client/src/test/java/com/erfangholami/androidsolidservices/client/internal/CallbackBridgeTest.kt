package com.erfangholami.androidsolidservices.client.internal

import com.erfangholami.androidsolidservices.client.sdk.SolidException
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every IPC call parks a coroutine until the service answers on a binder thread. A service that
 * answers twice, or answers after cancellation, must not crash the caller — resuming an already
 * completed continuation throws.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallbackBridgeTest {

    private suspend fun <T> bridged(register: (CallbackBridge<T>) -> Unit): T =
        suspendCancellableCoroutine { cont -> register(CallbackBridge(cont)) }

    @Test
    fun `onResult delivers the value`() = runTest {
        assertEquals("ok", bridged<String> { it.onResult("ok") })
    }

    @Test
    fun `onError maps the code to a typed exception`() = runTest {
        val thrown = runCatching {
            bridged<String> { it.onError(ExceptionsErrorCode.NO_INBOX, "no inbox") }
        }.exceptionOrNull()

        assertTrue(
            "expected NoInboxException, got $thrown",
            thrown is SolidException.SolidSharingException.NoInboxException,
        )
        assertEquals("no inbox", thrown?.message)
    }

    @Test
    fun `onError tolerates a null message from the service`() = runTest {
        val thrown = runCatching {
            bridged<String> { it.onError(ExceptionsErrorCode.ACCESS_DENIED, null) }
        }.exceptionOrNull()
        assertTrue(thrown is SolidException.SolidSharingException.AccessDeniedException)
    }

    @Test
    fun `onFailure propagates the throwable type and message`() = runTest {
        val boom = IllegalStateException("transport died")
        val thrown = runCatching { bridged<String> { it.onFailure(boom) } }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $thrown", thrown is IllegalStateException)
        assertEquals("transport died", thrown?.message)
    }

    @Test
    fun `a second answer from the service is ignored rather than crashing`() = runTest {
        val value = bridged<String> { bridge ->
            bridge.onResult("first")
            bridge.onResult("second")
            bridge.onError(ExceptionsErrorCode.UNKNOWN, "late error")
            bridge.onFailure(IllegalStateException("late failure"))
        }
        assertEquals("first", value)
    }

    @Test
    fun `answering after cancellation is a no-op`() = runTest {
        var captured: CallbackBridge<String>? = null
        val job = async { bridged<String> { captured = it } }
        yield()
        job.cancel()
        yield()

        captured?.onResult("too late")
        captured?.onError(ExceptionsErrorCode.UNKNOWN, "too late")
        captured?.onFailure(IllegalStateException("too late"))

        assertTrue("job should stay cancelled", job.isCancelled)
    }
}
