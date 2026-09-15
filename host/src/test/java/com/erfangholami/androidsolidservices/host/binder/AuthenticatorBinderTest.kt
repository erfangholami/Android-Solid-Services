package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.CapturingCallback
import com.erfangholami.androidsolidservices.host.testing.FakeHostSession
import com.erfangholami.androidsolidservices.host.testing.InMemoryGrantStore
import com.erfangholami.androidsolidservices.host.testing.Outcome
import com.erfangholami.androidsolidservices.host.testing.RecordingPolicy
import com.erfangholami.androidsolidservices.host.testing.entry
import com.erfangholami.androidsolidservices.host.testing.grant
import com.erfangholami.androidsolidservices.shared.ipc.booleanValue
import com.erfangholami.androidsolidservices.shared.ipc.parcelable
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AuthenticatorBinderTest {

    private val session = FakeHostSession()
    private val grants = InMemoryGrantStore()
    private val scope = CoroutineScope(SupervisorJob())
    private var caller: String? = CALLER
    private val binder = AuthenticatorBinder(
        session,
        grants,
        AccessGuard(session, RecordingPolicy()) { caller },
        scope,
        Dispatchers.Default,
    )
    private val approved = grant(entry(GrantTarget.Pod, AccessLevel.EDIT))

    private fun single(call: (CapturingCallback) -> Unit): Outcome = CapturingCallback().also(call).await()

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `hasLoggedIn reports the session's answer`() {
        assertTrue(binder.hasLoggedIn())
        session.loggedIn = false
        assertFalse(binder.hasLoggedIn())
    }

    @Test
    fun `isAppAuthorized is true only for an app that holds a grant for that account`() = runBlocking {
        assertFalse(binder.isAppAuthorized(ALICE))

        grants.put(approved)

        assertTrue(binder.isAppAuthorized(ALICE))
        assertFalse(binder.isAppAuthorized("https://bob.pod/profile/card#me"))
    }

    @Test
    fun `an unresolvable caller is never authorized`() = runBlocking {
        grants.put(approved)
        caller = null

        assertFalse(binder.isAppAuthorized(ALICE))
    }

    @Test
    fun `getAppGrant answers with the grant, or with nothing`() = runBlocking {
        assertNull((single { binder.getAppGrant(ALICE, it) } as Outcome.Result).bundle.parcelable(AppGrant::class.java))

        grants.put(approved)

        assertEquals(approved, (single { binder.getAppGrant(ALICE, it) } as Outcome.Result).bundle.parcelable(AppGrant::class.java))
    }

    @Test
    fun `disconnectFromSolid revokes the caller's grant for that account only`() = runBlocking {
        grants.put(approved)
        grants.put(grant(entry(GrantTarget.Pod, AccessLevel.EDIT), webId = "https://bob.pod/profile/card#me"))

        val outcome = single { binder.disconnectFromSolid(ALICE, it) } as Outcome.Result

        assertTrue(outcome.bundle.booleanValue())
        assertNull(grants.get(CALLER, ALICE))
        assertEquals(1, grants.grants().first().size)
    }

    @Test
    fun `disconnectFromSolid with an unresolvable caller is a typed error`() {
        caller = null

        val outcome = single { binder.disconnectFromSolid(ALICE, it) }

        assertEquals(ExceptionsErrorCode.UNKNOWN, (outcome as Outcome.Error).code)
    }
}
