package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.host.access.AccessGuard
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.CapturingCallback
import com.erfangholami.androidsolidservices.host.testing.CapturingListCallback
import com.erfangholami.androidsolidservices.host.testing.FakeHostSession
import com.erfangholami.androidsolidservices.host.testing.Outcome
import com.erfangholami.androidsolidservices.host.testing.Recorder
import com.erfangholami.androidsolidservices.host.testing.RecordingPolicy
import com.erfangholami.androidsolidservices.host.testing.VerbTable
import com.erfangholami.androidsolidservices.host.testing.recording
import com.erfangholami.androidsolidservices.host.testing.verb
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The inbox belongs to the account: reads need View on the pod, deletions Edit, and anything
 * that speaks as the user Full. The acting account is the first WebID each verb takes, which
 * for a request is the requester, not the owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NotificationsBinderTest {

    private val session = FakeHostSession()
    private val policy = RecordingPolicy()
    private val recorder = Recorder()
    private val scope = CoroutineScope(SupervisorJob())
    private val notificationsManager = recording<NotificationsManager>(recorder)
    private val binder =
        NotificationsBinder(notificationsManager, AccessGuard(session, policy) { CALLER }, scope, Dispatchers.Default)

    private val resource = "https://alice.pod/notes/"
    private val bob = "https://bob.pod/profile/card#me"
    private val read = VerbTarget.Pod to AccessLevel.VIEW
    private val write = VerbTarget.Pod to AccessLevel.EDIT
    private val send = VerbTarget.Pod to AccessLevel.FULL
    private val mode = ShareMode.READ.ordinal

    private fun single(call: (CapturingCallback) -> Unit): Outcome = CapturingCallback().also(call).await()
    private fun list(call: (CapturingListCallback) -> Unit): Outcome = CapturingListCallback().also(call).await()

    private val verbs = listOf(
        verb("listNotifications", read) { list { binder.listNotifications(ALICE, it) } },
        verb("listRequests", read) { list { binder.listRequests(ALICE, it) } },
        verb("ensureInbox", read) { single { binder.ensureInbox(ALICE, it) } },
        verb("deleteNotification", write) { single { binder.deleteNotification(ALICE, "https://alice.pod/inbox/n1", it) } },
        verb("compactInbox", write) { single { binder.compactInbox(ALICE, null, it) } },
        verb("sendOffer", send) { single { binder.sendOffer(ALICE, bob, resource, mode, null, null, it) } },
        verb("sendUndo", send) { single { binder.sendUndo(ALICE, bob, resource, it) } },
        verb("sendRequest", send) { single { binder.sendRequest(ALICE, bob, resource, mode, null, it) } },
        verb("sendReject", send) { single { binder.sendReject(ALICE, bob, resource, null, it) } },
        verb("sendUpdate", send) { single { binder.sendUpdate(ALICE, bob, resource, mode, null, null, it) } },
        verb("sendAccept", send) { single { binder.sendAccept(ALICE, bob, resource, mode, null, it) } },
        verb("recordDecisionGranted", send) { single { binder.recordDecisionGranted(ALICE, bob, resource, mode, null, it) } },
        verb("recordDecisionRejected", send) { single { binder.recordDecisionRejected(ALICE, bob, resource, -1, null, it) } },
    )

    private val table = VerbTable(policy, recorder, verbs)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `every verb asks for its level on the pod, then reaches the manager`() {
        table.assertEveryVerbIsGuardedAndReachesItsManager()
    }

    @Test
    fun `a denied verb answers with the policy's code and never reaches the manager`() {
        table.assertEveryDeniedVerbStopsBeforeItsManager()
    }

    @Test
    fun `a request is checked against the requester's account, not the owner's`() {
        session.sessions += bob

        single { binder.sendRequest(bob, ALICE, resource, mode, null, it) }

        assertEquals(bob, policy.checks.single().webId)
    }
}
