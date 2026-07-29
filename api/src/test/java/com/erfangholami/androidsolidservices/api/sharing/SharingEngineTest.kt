package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.api.access.WacBackend
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationsManagerImplementation
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerHelper
import com.erfangholami.androidsolidservices.api.sharing.implementation.SharingManagerImplementation
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        WacBackend(pod).grant(alice, resource, ShareMode.READ, bob, isContainer = false)

        val result = manager().createShare(
            alice, resource, ShareMode.WRITE, bob, notifyReceiver = false,
        )

        assertTrue(result is SolidResult.Failure)
        val bobShare = receiversOnResource().singleOrNull { it.receiver == bob }
        assertNotNull("a mode-change index failure must NOT revoke live access", bobShare)
    }
}
