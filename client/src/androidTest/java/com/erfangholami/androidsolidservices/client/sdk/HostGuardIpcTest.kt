package com.erfangholami.androidsolidservices.client.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.services.RealHostResourceService
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSResourceService
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives the production binder and the production access policy across a real process boundary.
 *
 * The other IPC tests bind hand-written fakes, which prove that what the SDK sends arrives with
 * the arguments it sent, but know nothing about permission. This one binds the real
 * `ResourceBinder` behind the real `ScopedAccessPolicy`, so a refusal here is the refusal Solid
 * Share would give, marshalled the same way.
 */
@RunWith(AndroidJUnit4::class)
class HostGuardIpcTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var connection: ServiceConnection? = null

    /** What the host answered: a value, or a typed refusal. Never both. */
    private class Answer(
        val value: Bundle?,
        val errorCode: Int?,
        val errorMessage: String?,
    ) {
        val refused: Boolean get() = errorCode != null
    }

    @After
    fun tearDown() {
        connection?.let { context.unbindService(it) }
        connection = null
    }

    private fun bind(action: String): IASSResourceService {
        val latch = CountDownLatch(1)
        var bound: IASSResourceService? = null
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                bound = IASSResourceService.Stub.asInterface(binder)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        connection = serviceConnection

        assertTrue(
            "the guard service did not bind",
            context.bindService(
                Intent(action).setPackage(context.packageName),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        assertTrue("timed out binding the guard service", latch.await(TIMEOUT, TimeUnit.SECONDS))
        return requireNotNull(bound)
    }

    private fun call(verb: String, invoke: (IASSParcelableCallback) -> Unit): Answer {
        val latch = CountDownLatch(1)
        var answer: Answer? = null
        invoke(
            object : IASSParcelableCallback.Stub() {
                override fun onResult(result: Bundle?) {
                    answer = Answer(result, null, null)
                    latch.countDown()
                }

                override fun onError(errorCode: Int, errorMessage: String?) {
                    answer = Answer(null, errorCode, errorMessage)
                    latch.countDown()
                }
            },
        )
        assertTrue("the host never answered $verb", latch.await(TIMEOUT, TimeUnit.SECONDS))
        return requireNotNull(answer)
    }

    private fun callList(verb: String, invoke: (IASSParcelableListCallback) -> Unit): Answer {
        val latch = CountDownLatch(1)
        var answer: Answer? = null
        invoke(
            object : IASSParcelableListCallback.Stub() {
                override fun onResult(result: Bundle?) {
                    answer = Answer(result, null, null)
                    latch.countDown()
                }

                override fun onError(errorCode: Int, errorMessage: String?) {
                    answer = Answer(null, errorCode, errorMessage)
                    latch.countDown()
                }
            },
        )
        assertTrue("the host never answered $verb", latch.await(TIMEOUT, TimeUnit.SECONDS))
        return requireNotNull(answer)
    }

    @Test
    fun a_read_is_allowed_by_a_View_grant() {
        val service = bind(RealHostResourceService.ACTION_VIEW_ONLY)

        val answer = call("head") { service.head(Fixtures.WEB_ID, Fixtures.RESOURCE, it) }

        assertNull("a read should not have been refused: ${answer.errorMessage}", answer.errorCode)
    }

    @Test
    fun a_listing_is_allowed_by_a_View_grant() {
        val service = bind(RealHostResourceService.ACTION_VIEW_ONLY)

        val answer = callList("listContainer") {
            service.listContainer(Fixtures.WEB_ID, Fixtures.CONTAINER, false, it)
        }

        assertNull("listing needs only View: ${answer.errorMessage}", answer.errorCode)
    }

    @Test
    fun a_write_is_refused_by_a_View_grant_and_the_message_names_both_levels() {
        val service = bind(RealHostResourceService.ACTION_VIEW_ONLY)

        val answer = call("deleteContainer") {
            service.deleteContainer(Fixtures.WEB_ID, Fixtures.CONTAINER, it)
        }

        assertTrue("a write under View must be refused", answer.refused)
        assertEquals(ExceptionsErrorCode.NOT_PERMISSION, answer.errorCode)
        val message = requireNotNull(answer.errorMessage)
        assertTrue("the message should name what is held: $message", message.contains("VIEW"))
        assertTrue("the message should name what is needed: $message", message.contains("EDIT"))
    }

    @Test
    fun the_same_write_is_allowed_once_the_grant_reaches_Edit() {
        val service = bind(RealHostResourceService.ACTION_EDIT)

        val answer = call("deleteContainer") {
            service.deleteContainer(Fixtures.WEB_ID, Fixtures.CONTAINER, it)
        }

        assertNull("Edit covers a delete: ${answer.errorMessage}", answer.errorCode)
    }

    @Test
    fun an_app_with_no_grant_is_refused_even_a_read() {
        val service = bind(RealHostResourceService.ACTION_NO_GRANT)

        val answer = call("head") { service.head(Fixtures.WEB_ID, Fixtures.RESOURCE, it) }

        assertEquals(ExceptionsErrorCode.NOT_PERMISSION, answer.errorCode)
    }

    private companion object {
        const val TIMEOUT = 10L
    }
}
