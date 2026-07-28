package com.erfangholami.androidsolidservices.shared.model.sharing

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareCollapseTest {

    private val resource = "https://alice.pod/notes/n1"
    private val other = "https://alice.pod/notes/n2"
    private val bob = ShareReceiver.WebIdReceiver("https://bob.pod/profile/card#me")
    private val carol = ShareReceiver.WebIdReceiver("https://carol.pod/profile/card#me")

    @Test
    fun `collapseByReceiver folds implied modes to one strongest row`() {
        val collapsed = listOf(
            GivenShare(bob, ShareMode.READ, resource),
            GivenShare(bob, ShareMode.WRITE, resource),
        ).collapseByReceiver()

        assertEquals(1, collapsed.size)
        assertEquals(ShareMode.WRITE, collapsed.single().mode)
        assertEquals(bob, collapsed.single().receiver)
    }

    @Test
    fun `collapseByReceiver keeps distinct receivers and resources separate`() {
        val collapsed = listOf(
            GivenShare(bob, ShareMode.READ, resource),
            GivenShare(carol, ShareMode.WRITE, resource),
            GivenShare(bob, ShareMode.READ, other),
        ).collapseByReceiver()

        assertEquals(3, collapsed.size)
        assertEquals(
            ShareMode.WRITE,
            collapsed.single { it.receiver == carol && it.resourceUri == resource }.mode,
        )
        assertEquals(
            ShareMode.READ,
            collapsed.single { it.receiver == bob && it.resourceUri == other }.mode,
        )
    }

    @Test
    fun `collapseByReceiver preserves the earliest known createdAt`() {
        val collapsed = listOf(
            GivenShare(bob, ShareMode.READ, resource, createdAt = null),
            GivenShare(bob, ShareMode.WRITE, resource, createdAt = "2026-07-01T00:00:00Z"),
        ).collapseByReceiver()

        assertEquals("2026-07-01T00:00:00Z", collapsed.single().createdAt)
    }

    @Test
    fun `collapseByReceiver collapses a Public grant`() {
        val collapsed = listOf(
            GivenShare(ShareReceiver.Public, ShareMode.READ, resource),
            GivenShare(ShareReceiver.Public, ShareMode.APPEND, resource),
        ).collapseByReceiver()

        assertEquals(1, collapsed.size)
        assertEquals(ShareReceiver.Public, collapsed.single().receiver)
        assertEquals(ShareMode.APPEND, collapsed.single().mode)
    }

    @Test
    fun `collapseByOwner folds received shares per owner and resource`() {
        val owner = "https://owner.pod/profile/card#me"
        val collapsed = listOf(
            ReceivedShare(owner, ShareMode.READ, resource, addedAt = "2026-07-01T00:00:00Z"),
            ReceivedShare(owner, ShareMode.WRITE, resource, addedAt = null),
        ).collapseByOwner()

        assertEquals(1, collapsed.size)
        assertEquals(ShareMode.WRITE, collapsed.single().mode)
        assertEquals("2026-07-01T00:00:00Z", collapsed.single().addedAt)
    }
}
