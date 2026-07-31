package com.erfangholami.androidsolidservices.client.internal

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.sdk.SolidResourceClient
import com.erfangholami.androidsolidservices.client.sdk.SolidSharingClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
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
    fun a_call_succeeds_again_after_the_service_process_is_killed(): Unit = runBlocking {
        assertTrue(client.exists(Fixtures.WEB_ID, Fixtures.RESOURCE))

        killFakeProcess()

        // The binder the connector holds is now dead. The call must rebind rather than propagate a
        // DeadObjectException — this is the whole recovery contract.
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
        // Binder dispatches each reply on its own thread, so several parked continuations are
        // resumed concurrently. One continuation resuming another's caller would show up here.
        val results = (1..8).map { index ->
            async { client.copy(Fixtures.WEB_ID, "${Fixtures.RESOURCE}-$index", Fixtures.DESTINATION) }
        }.awaitAll()

        assertEquals(8, results.size)
        assertTrue(results.all { it == Fixtures.DESTINATION })
    }

    @Test
    fun each_client_binds_its_own_service_independently(): Unit = runBlocking {
        // A shared connector would mean one client's disconnect silently breaking another's.
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

        // Wait for the death notification to land before the next call, otherwise the connector
        // still believes it holds a live binder and the retry path is never entered.
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            val alive = activityManager.runningAppProcesses.orEmpty().any { it.processName == target }
            if (!alive) return
            Thread.sleep(50)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
