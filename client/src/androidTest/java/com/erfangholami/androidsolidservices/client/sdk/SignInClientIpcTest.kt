package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import com.erfangholami.androidsolidservices.services.ASSAuthenticatorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives [SolidSignInClient] across a real binder.
 *
 * Unlike the other clients this one is callback-based rather than `suspend`, and its callbacks
 * arrive on a binder thread — so a caller who touches UI state from them is on the wrong thread.
 *
 * It also differs in a way callers have to know about: its methods throw
 * [SolidException.SolidServiceConnectionException] immediately when the binding is not up yet,
 * whereas the `suspend` clients wait for it. Binding is asynchronous, so calling `getAccount`
 * straight after obtaining the client is a race — which is why [awaitConnection] runs first here,
 * and why the class documents collecting [SolidSignInClient.authServiceConnectionState] as step one.
 */
@RunWith(AndroidJUnit4::class)
class SignInClientIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val client: SolidSignInClient
        get() = SolidSignInClient.getInstance(sdk.context, sdk.context.applicationInfo) { true }

    @Before
    fun awaitConnection() {
        runBlocking {
            withTimeout(CONNECT_TIMEOUT) { client.authServiceConnectionState().first { it } }
        }
    }

    @Test
    fun the_connection_flow_reaches_true(): Unit = runBlocking {
        withTimeout(CONNECT_TIMEOUT) { client.authServiceConnectionState().first { it } }
    }

    @Test
    fun a_call_before_the_binding_is_up_fails_fast_rather_than_waiting() {
        // Pins the asymmetry above. A fresh client has not bound yet, and this client reports that
        // rather than blocking — callers must gate on the connection flow.
        SolidSignInClient.resetForTests()
        val fresh = SolidSignInClient.getInstance(sdk.context, sdk.context.applicationInfo) { true }

        val thrown = runCatching { fresh.getAccount(ASSAuthenticatorService.AUTHORIZED_WEB_ID) }
            .exceptionOrNull()

        assertTrue(
            "expected SolidServiceConnectionException, got $thrown",
            thrown is SolidException.SolidServiceConnectionException,
        )
    }

    @Test
    fun getAccount_returns_an_account_for_an_authorized_webId() {
        val account = client.getAccount(ASSAuthenticatorService.AUTHORIZED_WEB_ID)

        assertEquals(ASSAuthenticatorService.AUTHORIZED_WEB_ID, account?.webId)
        assertEquals(sdk.context.packageName, account?.packageName)
        sdk.recorded("isAppAuthorized")
            .assertArgs("webId" to ASSAuthenticatorService.AUTHORIZED_WEB_ID)
    }

    @Test
    fun getAccount_returns_null_when_the_app_is_not_authorized() {
        // Null is the "not granted yet" signal callers branch on; it must not be an exception.
        assertNull(client.getAccount(ASSAuthenticatorService.UNKNOWN_WEB_ID))
    }

    @Test
    fun requestLogin_delivers_the_selected_webId() {
        val latch = CountDownLatch(1)
        var selected: String? = null
        var error: SolidException? = null

        client.requestLogin { webId, failure ->
            selected = webId
            error = failure
            latch.countDown()
        }

        assertTrue("requestLogin never called back", latch.await(CALL_TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals(ASSAuthenticatorService.AUTHORIZED_WEB_ID, selected)
        assertNull(error)
    }

    @Test
    fun disconnectFromSolid_reports_success_and_sends_the_webId() {
        val latch = CountDownLatch(1)
        var granted = false

        client.disconnectFromSolid(ASSAuthenticatorService.AUTHORIZED_WEB_ID) {
            granted = it
            latch.countDown()
        }

        assertTrue(latch.await(CALL_TIMEOUT, TimeUnit.MILLISECONDS))
        assertTrue(granted)
        sdk.recorded("disconnectFromSolid")
            .assertArgs("webId" to ASSAuthenticatorService.AUTHORIZED_WEB_ID)
    }

    @Test
    fun disconnectFromSolid_reports_false_rather_than_throwing_on_a_service_error() {
        // The callback has no error channel, so a failure has to arrive as `false`.
        val latch = CountDownLatch(1)
        var granted = true

        client.disconnectFromSolid(ASSAuthenticatorService.UNKNOWN_WEB_ID) {
            granted = it
            latch.countDown()
        }

        assertTrue(latch.await(CALL_TIMEOUT, TimeUnit.MILLISECONDS))
        assertTrue("a service error must surface as false", !granted)
    }

    @Test
    fun a_missing_ASS_app_fails_before_any_IPC() {
        SolidSignInClient.resetForTests()
        val uninstalled =
            SolidSignInClient.getInstance(sdk.context, sdk.context.applicationInfo) { false }

        val thrown = runCatching { uninstalled.getAccount("https://x.example/#me") }.exceptionOrNull()

        assertTrue(
            "expected SolidAppNotFoundException, got $thrown",
            thrown is SolidException.SolidAppNotFoundException,
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT = 10_000L
        const val CALL_TIMEOUT = 5_000L
    }
}
