package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationsManagerImplementation
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerHelper
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerImplementation
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

/**
 * Tests the `createShare` rollback contract: when the share-index write fails
 * after the WAC grant was written, a **new** share is rolled back (the grant is
 * revoked so the ACL and index don't diverge), but a **mode change** for a
 * receiver who already had access keeps the live grant rather than stripping
 * access the index simply failed to record.
 *
 * The engine's three collaborators are process-global singletons, reset here
 * via their `resetForTest` seam so the test's in-memory pod is used.
 */
class SharingEngineTest {

    private val alice = "https://alice.pod/profile/card#me"
    private val bob = ShareReceiver.WebIdReceiver("https://bob.pod/profile/card#me")
    private val podRoot = "https://alice.pod/"
    private val resource = "https://alice.pod/notes/n1"

    private lateinit var pod: InMemorySharingPod

    @Before
    fun setUp() {
        SharingManagerImplementation.resetForTest()
        SharingManagerHelper.resetForTest()
        NotificationsManagerImplementation.resetForTest()
        pod = InMemorySharingPod(alice, podRoot)
    }

    private fun manager() = SharingManagerImplementation.getInstance(pod)

    private fun receiversOnResource(): List<GivenShare> =
        runBlocking { WacBackend(pod).listShares(alice, resource) }

    @Test
    fun `a failed index write on a new share rolls back the WAC grant`() = runBlocking {
        val result = manager().createShare(
            alice, resource, ShareMode.WRITE, bob, notifyReceiver = false,
        )

        assertTrue("the index-write failure must surface", result is SolidResult.Failure)
        assertTrue(
            "a rolled-back new share must leave the receiver with no access",
            receiversOnResource().none { it.receiver == bob },
        )
    }

    @Test
    fun `a failed index write on a mode change keeps the receiver's live access`() = runBlocking {
        // Bob already holds Read from a prior successful share.
        WacBackend(pod).grant(alice, resource, ShareMode.READ, bob, isContainer = false)

        val result = manager().createShare(
            alice, resource, ShareMode.WRITE, bob, notifyReceiver = false,
        )

        assertTrue(result is SolidResult.Failure)
        val bobShare = receiversOnResource().singleOrNull { it.receiver == bob }
        assertNotNull("a mode-change index failure must NOT revoke live access", bobShare)
    }
}
