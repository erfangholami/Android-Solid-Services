package com.erfangholami.androidsolidservices.host.binder

import com.erfangholami.androidsolidservices.api.sharing.SharingManager
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
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every sharing verb needs Full access: on the resource it names, or on the whole pod. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SharingBinderTest {

    private val session = FakeHostSession()
    private val policy = RecordingPolicy()
    private val recorder = Recorder()
    private val scope = CoroutineScope(SupervisorJob())
    private val sharingManager = recording<SharingManager>(recorder)
    private val binder = SharingBinder(sharingManager, AccessGuard(session, policy) { CALLER }, scope, Dispatchers.Default)

    private val resource = "https://alice.pod/notes/"
    private val bob = "https://bob.pod/profile/card#me"
    private val onResource = VerbTarget.Resource(resource) to AccessLevel.FULL
    private val onPod = VerbTarget.Pod to AccessLevel.FULL
    private val request = ShareRequest("https://alice.pod/inbox/r1", bob, resource, ShareMode.READ, null, null)

    private fun single(call: (CapturingCallback) -> Unit): Outcome = CapturingCallback().also(call).await()
    private fun list(call: (CapturingListCallback) -> Unit): Outcome = CapturingListCallback().also(call).await()

    private val verbs = listOf(
        verb("getStoredGivenShares", onPod) { list { binder.getStoredGivenShares(ALICE, it) } },
        verb("refreshGivenShares", onPod) { list { binder.refreshGivenShares(ALICE, it) } },
        verb("getGivenSharesForResource", onResource) { list { binder.getGivenSharesForResource(ALICE, resource, it) } },
        verb("createShare", onResource) {
            single { binder.createShare(ALICE, resource, ShareMode.READ.ordinal, ShareReceiver.KIND_WEBID, bob, true, null, null, it) }
        },
        verb("updateShare", onResource) {
            single { binder.updateShare(ALICE, resource, ShareMode.WRITE.ordinal, ShareReceiver.KIND_WEBID, bob, null, null, it) }
        },
        verb("revokeShare", onResource) { single { binder.revokeShare(ALICE, resource, ShareReceiver.KIND_WEBID, bob, it) } },
        verb("purgeGivenShares", onResource) { list { binder.purgeGivenShares(ALICE, resource, true, true, it) } },
        verb("getStoredReceivedShares", onPod) { list { binder.getStoredReceivedShares(ALICE, it) } },
        verb("refreshReceivedShares", onPod) { list { binder.refreshReceivedShares(ALICE, it) } },
        verb("addReceivedShare", onPod) { single { binder.addReceivedShare(ALICE, resource, null, null, it) } },
        verb("removeReceivedShare", onPod) { single { binder.removeReceivedShare(ALICE, resource, bob, it) } },
        verb("getAccessGrants", onPod) { list { binder.getAccessGrants(ALICE, it) } },
        verb("acceptShareRequest", onResource) { single { binder.acceptShareRequest(ALICE, request, it) } },
        verb("rejectShareRequest", onResource) { single { binder.rejectShareRequest(ALICE, request, null, it) } },
        verb("rebuildGivenIndex", onPod) { list { binder.rebuildGivenIndex(ALICE, it) } },
        verb("publishCatalogEntry", onResource) {
            single { binder.publishCatalogEntry(ALICE, CatalogEntry(resource, "Notes", null, null), it) }
        },
        verb("removeCatalogEntry", onResource) { single { binder.removeCatalogEntry(ALICE, resource, it) } },
        verb("getOwnerCatalog", onPod) { list { binder.getOwnerCatalog(ALICE, bob, it) } },
        verb("makePrivate", onResource) { single { binder.makePrivate(ALICE, resource, it) } },
        verb("repairOwnerControl", onResource) { single { binder.repairOwnerControl(ALICE, resource, it) } },
        verb("syncReceivedShares", onPod) { list { binder.syncReceivedShares(ALICE, mutableListOf(), it) } },
    )

    private val table = VerbTable(policy, recorder, verbs)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `every verb asks for Full on its resource or the pod, then reaches the manager`() {
        table.assertEveryVerbIsGuardedAndReachesItsManager()
    }

    @Test
    fun `a denied verb answers with the policy's code and never reaches the manager`() {
        table.assertEveryDeniedVerbStopsBeforeItsManager()
    }

    @Test
    fun `a mode this build does not know is refused as an error, not a crash`() {
        val outcome = single { binder.createShare(ALICE, resource, 99, ShareReceiver.KIND_WEBID, bob, true, null, null, it) }

        assertEquals(ExceptionsErrorCode.UNKNOWN, (outcome as Outcome.Error).code)
        assertTrue(recorder.calls.isEmpty())
    }
}
