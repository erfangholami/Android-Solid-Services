package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.access.AclAuthorization
import com.erfangholami.androidsolidservices.shared.model.access.SolidACLResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

/**
 * Behavioural tests for [WacBackend] — the code that writes Web Access Control
 * rules that grant access to pod resources. Exercises the full read-modify-write
 * ACL cycle against an in-memory pod that round-trips the document through the
 * library's own N-Triples codec.
 */
class WacBackendTest {

    private val alice = "https://alice.pod/profile/card#me"
    private val bob = "https://bob.pod/profile/card#me"
    private val carol = "https://carol.pod/profile/card#me"
    private val resource = URI.create("https://alice.pod/notes/n1")
    private val container = URI.create("https://alice.pod/shared/")

    private lateinit var pod: InMemoryAccessPod
    private lateinit var backend: WacBackend

    @Before
    fun setUp() {
        pod = InMemoryAccessPod()
        backend = WacBackend(pod)
    }

    private fun aclOf(res: URI): SolidACLResource = runBlocking {
        pod.read(alice, pod.aclUriFor(res), SolidACLResource::class.java).getOrThrow()
    }

    private fun grant(res: URI, mode: ShareMode, receiver: ShareReceiver, container: Boolean = false) =
        runBlocking { backend.grant(alice, res, mode, receiver, isContainer = container) }

    private fun List<AclAuthorization>.forAgent(webId: String) =
        singleOrNull { auth -> auth.agents.any { it.toString() == webId } }

    private fun List<AclAuthorization>.ownerRule(webId: String) =
        single { auth -> auth.agents.any { it.toString() == webId } && ACL.CONTROL in auth.modes }

    @Test
    fun `grant Edit writes Read plus Write and re-asserts owner control`() {
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(bob))

        val auths = aclOf(resource).getAuthorizations()
        val bobRule = auths.forAgent(bob)!!
        assertEquals(setOf(ACL.READ, ACL.WRITE), bobRule.modes)
        assertEquals(listOf(resource), bobRule.accessTo)
        assertTrue("a non-container grant carries no acl:default", bobRule.default.isEmpty())

        assertEquals(setOf(ACL.READ, ACL.WRITE, ACL.CONTROL), auths.ownerRule(alice).modes)
    }

    @Test
    fun `grant Add writes Read plus Append`() {
        grant(resource, ShareMode.APPEND, ShareReceiver.WebIdReceiver(bob))
        assertEquals(setOf(ACL.READ, ACL.APPEND), aclOf(resource).getAuthorizations().forAgent(bob)!!.modes)
    }

    @Test
    fun `grant on a container sets acl default so descendants inherit`() {
        grant(container, ShareMode.READ, ShareReceiver.WebIdReceiver(bob), container = true)
        val bobRule = aclOf(container).getAuthorizations().forAgent(bob)!!
        assertEquals(listOf(container), bobRule.default)
        assertEquals(setOf(ACL.READ), bobRule.modes)
    }

    @Test
    fun `listShares on a child with no own ACL surfaces the container's inherited default grant`() {
        grant(container, ShareMode.READ, ShareReceiver.WebIdReceiver(bob), container = true)

        val child = URI.create("${container}note-x")
        val list = runBlocking { backend.listShares(alice, child) }

        assertEquals(1, list.size)
        assertEquals(ShareReceiver.WebIdReceiver(bob), list.single().receiver)
        assertEquals(ShareMode.READ, list.single().mode)
    }

    @Test
    fun `append-only grant with implied modes off writes Append with no Read`() {
        runBlocking {
            backend.grant(
                alice, container, ShareMode.APPEND, ShareReceiver.Public,
                isContainer = true, includeImpliedModes = false,
            )
        }
        val publicRule = aclOf(container).getAuthorizations()
            .single { it.agentClasses.any { c -> c.toString() == ShareReceiver.Public.toRdfSubject() } }
        assertEquals(setOf(ACL.APPEND), publicRule.modes)
        assertFalse("an inbox append grant must not leak Read", ACL.READ in publicRule.modes)
    }

    @Test
    fun `listShares collapses implied modes to one strongest row and hides the owner`() {
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(bob))

        val shares = runBlocking { backend.listShares(alice, resource) }
        assertEquals(1, shares.size)
        assertEquals(ShareMode.WRITE, shares.single().mode)
        assertEquals(ShareReceiver.WebIdReceiver(bob), shares.single().receiver)
    }

    @Test
    fun `re-granting a receiver replaces their level without duplicate rows`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(bob))

        val shares = runBlocking { backend.listShares(alice, resource) }
        assertEquals(1, shares.size)
        assertEquals(ShareMode.WRITE, shares.single().mode)
    }

    @Test
    fun `two receivers each get their own row`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(carol))

        val shares = runBlocking { backend.listShares(alice, resource) }
            .associate { it.receiver to it.mode }
        assertEquals(ShareMode.READ, shares[ShareReceiver.WebIdReceiver(bob)])
        assertEquals(ShareMode.WRITE, shares[ShareReceiver.WebIdReceiver(carol)])
    }

    @Test
    fun `revoke removes the receiver but keeps the owner rule`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        runBlocking { backend.revoke(alice, resource, ShareReceiver.WebIdReceiver(bob), isContainer = false) }

        assertTrue(runBlocking { backend.listShares(alice, resource) }.isEmpty())
        assertEquals(
            setOf(ACL.READ, ACL.WRITE, ACL.CONTROL),
            aclOf(resource).getAuthorizations().ownerRule(alice).modes,
        )
    }

    @Test
    fun `revoking one of two receivers leaves the other intact`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(carol))
        runBlocking { backend.revoke(alice, resource, ShareReceiver.WebIdReceiver(bob), isContainer = false) }

        val shares = runBlocking { backend.listShares(alice, resource) }
        assertEquals(1, shares.size)
        assertEquals(ShareReceiver.WebIdReceiver(carol), shares.single().receiver)
    }

    @Test
    fun `a 412 on the ACL write is retried and the grant still lands`() {
        pod.failNextPutWith412 = true
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))

        assertEquals(1, runBlocking { backend.listShares(alice, resource) }.size)
        assertTrue("the write should have been retried after the 412", pod.putLog.size >= 2)
    }
}
