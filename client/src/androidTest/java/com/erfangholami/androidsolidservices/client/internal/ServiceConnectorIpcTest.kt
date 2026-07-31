package com.erfangholami.androidsolidservices.client.internal

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.erfangholami.androidsolidservices.client.sdk.SolidException
import com.erfangholami.androidsolidservices.services.ASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLogoutCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives [ServiceConnector] against [ASSAuthenticatorService], which the instrumentation manifest
 * hosts in a separate process. Every call here therefore crosses a real binder boundary and is
 * really marshalled — the behaviour unit tests cannot reach and the ASS app is not needed for.
 */
@RunWith(AndroidJUnit4::class)
class ServiceConnectorIpcTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var connector: ServiceConnector<IASSAuthenticatorService>

    @Before
    fun bind() {
        connector = ServiceConnector(
            context,
            ASSAuthenticatorService::class.java.name,
            context.packageName,
            IASSAuthenticatorService.Stub::asInterface,
        )
    }

    @After
    fun unbind() {
        connector.unbind()
    }

    private suspend fun awaitConnected() = withTimeout(CONNECT_TIMEOUT) {
        connector.connectionState.first { it }
    }

    @Test
    fun binds_and_reports_connected(): Unit = runBlocking {
        awaitConnected()
        assertTrue("connector should report a live service", connector.isConnected())
    }

    @Test
    fun the_fake_really_runs_in_another_process() {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val processes = activityManager.runningAppProcesses.orEmpty().map { it.processName }
        assertTrue(
            "expected a :fakeass process, saw $processes",
            processes.any { it.endsWith(":fakeass") },
        )
    }

    @Test
    fun synchronous_call_marshals_across_the_boundary(): Unit = runBlocking {
        awaitConnected()
        val service = connector.require()

        assertTrue(service.hasLoggedIn())
        assertTrue(service.isAppAuthorized(ASSAuthenticatorService.AUTHORIZED_WEB_ID))
        assertFalse(service.isAppAuthorized(ASSAuthenticatorService.UNKNOWN_WEB_ID))
    }

    @Test
    fun callback_result_resumes_the_suspended_caller(): Unit = runBlocking {
        awaitConnected()
        val granted = withTimeout(CALL_TIMEOUT) {
            connector.await<Boolean> { service, bridge ->
                service.disconnectFromSolid(
                    ASSAuthenticatorService.AUTHORIZED_WEB_ID,
                    logoutCallback(bridge),
                )
            }
        }
        assertTrue(granted)
    }

    @Test
    fun callback_error_surfaces_as_a_typed_exception(): Unit = runBlocking {
        awaitConnected()
        val thrown = runCatching {
            withTimeout(CALL_TIMEOUT) {
                connector.await<Boolean> { service, bridge ->
                    service.disconnectFromSolid(
                        ASSAuthenticatorService.UNKNOWN_WEB_ID,
                        logoutCallback(bridge),
                    )
                }
            }
        }.exceptionOrNull()

        assertTrue(
            "expected SolidNotLoggedInException, got $thrown",
            thrown is SolidException.SolidNotLoggedInException,
        )
        assertEquals("not signed in", thrown?.message)
    }

    @Test
    fun a_service_that_answers_twice_does_not_crash_the_caller(): Unit = runBlocking {
        awaitConnected()
        val granted = withTimeout(CALL_TIMEOUT) {
            connector.await<Boolean> { service, bridge ->
                service.disconnectFromSolid(
                    ASSAuthenticatorService.DOUBLE_ANSWER_WEB_ID,
                    logoutCallback(bridge),
                )
            }
        }
        assertTrue("first answer wins, the second is dropped", granted)
    }

    @Test
    fun require_throws_once_unbound() {
        connector.unbind()
        val thrown = runCatching { connector.require() }.exceptionOrNull()
        assertTrue(
            "expected SolidServiceConnectionException, got $thrown",
            thrown is SolidException.SolidServiceConnectionException,
        )
    }

    @Test
    fun unbind_clears_the_connection_state(): Unit = runBlocking {
        awaitConnected()
        connector.unbind()
        assertFalse(connector.connectionState.value)
        assertFalse(connector.isConnected())
    }

    private fun logoutCallback(bridge: CallbackBridge<Boolean>) =
        object : IASSLogoutCallback.Stub() {
            override fun onResult(granted: Boolean) = bridge.onResult(granted)

            override fun onError(
                errorCode: Int,
                errorMessage: String?,
            ) = bridge.onError(errorCode, errorMessage)
        }

    private companion object {
        const val CONNECT_TIMEOUT = 10_000L
        const val CALL_TIMEOUT = 5_000L
    }
}
