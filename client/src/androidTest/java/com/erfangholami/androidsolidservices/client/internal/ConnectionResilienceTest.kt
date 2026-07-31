package com.erfangholami.androidsolidservices.client.internal

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.sdk.SolidException
import com.erfangholami.androidsolidservices.client.sdk.SolidResourceClient
import com.erfangholami.androidsolidservices.client.sdk.SolidSharingClient
import com.erfangholami.androidsolidservices.services.ASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.IASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.model.auth.IASSLogoutCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What happens to an in-flight SDK call when the service process goes away.
 *
 * `ServiceConnector` catches `DeadObjectException`, rebinds and retries once — a path that only
 * runs when a real process really dies, so no unit test can reach it. It is also the path a user
 * hits every time Android reclaims the ASS app in the background, which makes it the least exotic
 * failure in the SDK despite being the hardest to reproduce.
 */
@RunWith(AndroidJUnit4::class)
class ConnectionResilienceTest {

    @get:Rule
    val sdk = FakeSdk()

    private val client: SolidResourceClient
        get() = SolidResourceClient.getInstance(sdk.context) { true }

    @Test
    fun a_call_parked_on_a_dying_service_fails_over_instead_of_hanging(): Unit = runBlocking {
        java.io.File(sdk.context.filesDir, ASSAuthenticatorService.HANG_MARKER).delete()

        val auth = ServiceConnector(
            sdk.context,
            ASSAuthenticatorService::class.java.name,
            sdk.context.packageName,
            IASSAuthenticatorService.Stub::asInterface,
        )
        try {
            withTimeout(CONNECT_TIMEOUT) { auth.connectionState.first { it } }

            val killer = launch(Dispatchers.Default) {
                delay(1_000)
                killFakeProcess()
            }

            val granted = withTimeout(RECOVERY_TIMEOUT) {
                auth.await<Boolean> { service, bridge ->
                    service.disconnectFromSolid(
                        ASSAuthenticatorService.HANG_ONCE_WEB_ID,
                        object : IASSLogoutCallback.Stub() {
                            override fun onResult(granted: Boolean) = bridge.onResult(granted)

                            override fun onError(errorCode: Int, errorMessage: String?) =
                                bridge.onError(errorCode, errorMessage)
                        },
                    )
                }
            }

            killer.join()
            assertTrue("the retry after the service death should have answered", granted)
        } finally {
            auth.unbind()
        }
    }

    @Test
    fun a_null_binding_reports_disconnected_rather_than_pretending(): Unit = runBlocking {
        val connector = ServiceConnector(
            sdk.context,
            NullBindingService::class.java.name,
            sdk.context.packageName,
            IASSAuthenticatorService.Stub::asInterface,
        )
        try {
            val everConnected = withTimeoutOrNull(NULL_BINDING_WAIT) {
                connector.connectionState.first { it }
            }

            assertNull("onBind returned null; the connection flow must never report true", everConnected)
            assertFalse(connector.isConnected())
            assertThrows(SolidException.SolidServiceConnectionException::class.java) {
                connector.require()
            }
        } finally {
            connector.unbind()
        }
    }

    @Test
    fun a_call_succeeds_again_after_the_service_process_is_killed(): Unit = runBlocking {
        assertTrue(client.exists(Fixtures.WEB_ID, Fixtures.RESOURCE))

        killFakeProcess()

        assertTrue(
            "the connector did not recover from a dead binder",
            client.exists(Fixtures.WEB_ID, Fixtures.RESOURCE),
        )
    }

    @Test
    fun the_connection_flow_reports_the_reconnection(): Unit = runBlocking {
        withTimeout(TIMEOUT) { client.resourceServiceConnectionState().first { it } }

        killFakeProcess()
        client.exists(Fixtures.WEB_ID, Fixtures.RESOURCE)

        withTimeout(TIMEOUT) { client.resourceServiceConnectionState().first { it } }
    }

    @Test
    fun concurrent_calls_on_one_connector_all_resolve(): Unit = runBlocking {
        val results = (1..8).map { index ->
            async { client.copy(Fixtures.WEB_ID, "${Fixtures.RESOURCE}-$index", Fixtures.DESTINATION) }
        }.awaitAll()

        assertEquals(8, results.size)
        assertTrue(results.all { it == Fixtures.DESTINATION })
    }

    @Test
    fun each_client_binds_its_own_service_independently(): Unit = runBlocking {
        val sharing = SolidSharingClient.getInstance(sdk.context)

        withTimeout(TIMEOUT) { client.resourceServiceConnectionState().first { it } }
        withTimeout(TIMEOUT) { sharing.connectionState().first { it } }

        assertTrue(client.exists(Fixtures.WEB_ID, Fixtures.RESOURCE))
        assertEquals(listOf(Fixtures.GIVEN_SHARE), sharing.getStoredGivenShares(Fixtures.WEB_ID))
    }

    private fun killFakeProcess() {
        val activityManager = sdk.context.getSystemService(ActivityManager::class.java)
        val target = "${sdk.context.packageName}:fakeass"
        val pid = activityManager.runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == target }
            ?.pid
            ?: throw AssertionError("the :fakeass process was not running, so nothing was killed")

        android.os.Process.killProcess(pid)

        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            val alive = activityManager.runningAppProcesses.orEmpty().any { it.processName == target }
            if (!alive) return
            Thread.sleep(50)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val CONNECT_TIMEOUT = 10_000L
        const val RECOVERY_TIMEOUT = 30_000L
        const val NULL_BINDING_WAIT = 2_000L
    }
}
