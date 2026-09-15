package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.HostResolver
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import com.erfangholami.androidsolidservices.services.ASSAuthenticatorService
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives [SolidSignInClient] across a real binder.
 *
 * The grant is the interesting payload: a Parcelable with sealed targets inside, written by the
 * fake in another process and read back here through the envelope's class-loader handling.
 */
@RunWith(AndroidJUnit4::class)
class SignInClientIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val client: SolidSignInClient
        get() = SolidSignInClient.getInstance(sdk.context)

    @Test
    fun the_connection_flow_reaches_true(): Unit = runBlocking {
        withTimeout(CONNECT_TIMEOUT) { client.authServiceConnectionState().first { it } }
    }

    @Test
    fun a_call_before_the_binding_is_up_waits_for_it_rather_than_failing(): Unit = runBlocking {
        SolidSignInClient.resetForTests()
        val fresh = SolidSignInClient.getInstance(sdk.context)

        val account = withTimeout(CONNECT_TIMEOUT) { fresh.getAccount(ASSAuthenticatorService.AUTHORIZED_WEB_ID) }

        assertEquals(ASSAuthenticatorService.AUTHORIZED_WEB_ID, account?.webId)
    }

    @Test
    fun getAccount_returns_the_grant_for_an_authorized_webId(): Unit = runBlocking {
        val account = client.getAccount(ASSAuthenticatorService.AUTHORIZED_WEB_ID)

        assertEquals(ASSAuthenticatorService.AUTHORIZED_WEB_ID, account?.webId)
        assertEquals(sdk.context.packageName, account?.packageName)
        assertEquals(AccessLevel.FULL, account?.grant?.podLevel())
        assertEquals(listOf(GrantTarget.Pod), account?.grant?.entries?.map { it.target })
        assertEquals(ASSAuthenticatorService.GRANTED_AT, account?.grant?.grantedAt)
        sdk.recorded("getAppGrant")
            .assertArgs("webId" to ASSAuthenticatorService.AUTHORIZED_WEB_ID)
    }

    @Test
    fun getAccount_returns_null_when_the_app_holds_no_grant(): Unit = runBlocking {
        assertNull(client.getAccount(ASSAuthenticatorService.UNKNOWN_WEB_ID))
    }

    @Test
    fun disconnectFromSolid_reports_success_and_sends_the_webId(): Unit = runBlocking {
        val revoked = client.disconnectFromSolid(ASSAuthenticatorService.AUTHORIZED_WEB_ID)

        assertTrue(revoked)
        sdk.recorded("disconnectFromSolid")
            .assertArgs("webId" to ASSAuthenticatorService.AUTHORIZED_WEB_ID)
    }

    @Test
    fun disconnectFromSolid_surfaces_a_service_error_as_a_typed_exception(): Unit = runBlocking {
        val thrown = runCatching { client.disconnectFromSolid(ASSAuthenticatorService.UNKNOWN_WEB_ID) }
            .exceptionOrNull()

        assertTrue(
            "expected SolidNotLoggedInException, got $thrown",
            thrown is SolidException.SolidNotLoggedInException,
        )
    }

    @Test
    fun a_missing_host_app_fails_before_any_IPC(): Unit = runBlocking {
        SolidSignInClient.resetForTests()
        HostResolver.forceAbsent = true
        val uninstalled = SolidSignInClient.getInstance(sdk.context)

        val thrown = runCatching { uninstalled.getAccount("https://x.example/#me") }.exceptionOrNull()

        assertTrue(
            "expected SolidAppNotFoundException, got $thrown",
            thrown is SolidException.SolidAppNotFoundException,
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT = 10_000L
    }
}
