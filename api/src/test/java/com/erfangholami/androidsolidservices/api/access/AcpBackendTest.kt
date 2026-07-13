package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.ACP
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

/**
 * Behavioural tests for [AcpBackend] — the Access Control Policy backend used by
 * ESS/CSS pods in ACP mode. Grants are observed through [AcpBackend.listShares]
 * (the round-trip a caller sees) and, where structure matters, by inspecting the
 * ACR quads the backend PUT.
 */
class AcpBackendTest {

    private val alice = "https://alice.pod/profile/card#me"
    private val bob = "https://bob.pod/profile/card#me"
    private val carol = "https://carol.pod/profile/card#me"
    private val resource = "https://alice.pod/notes/n1"
    private val container = "https://alice.pod/shared/"

    private lateinit var pod: InMemoryAccessPod
    private lateinit var backend: AcpBackend

    @Before
    fun setUp() {
        pod = InMemoryAccessPod()
        backend = AcpBackend(pod)
    }

    private fun grant(res: String, mode: ShareMode, receiver: ShareReceiver, container: Boolean = false) =
        runBlocking { backend.grant(alice, res, mode, receiver, isContainer = container) }

    private fun shares(res: String) = runBlocking { backend.listShares(alice, res) }

    private fun acrQuadsOf(res: String) = runBlocking {
        pod.read(alice, pod.aclUriFor(res).toString(), SolidRDFResource::class.java).getOrThrow().getAllQuads()
    }

    private fun hasOwnerControl(res: String): Boolean {
        val quads = acrQuadsOf(res)
        val ownerMatches = quads.any { it.predicate == ACP.AGENT && it.`object` == alice }
        val controlAllowed = quads.any { it.predicate == ACP.ALLOW && it.`object` == ACL.CONTROL }
        return ownerMatches && controlAllowed
    }

    /** The `acp:allow` modes of the policy whose matcher targets `acp:PublicAgent`. */
    private fun publicPolicyModes(res: String): Set<String> {
        val quads = acrQuadsOf(res)
        val publicMatchers = quads
            .filter { it.predicate == ACP.AGENT && it.`object` == ACP.PUBLIC_AGENT }
            .map { it.subject }
            .toSet()
        val policies = quads
            .filter { it.predicate == ACP.ALL_OF && it.`object` in publicMatchers }
            .map { it.subject }
            .toSet()
        return quads
            .filter { it.subject in policies && it.predicate == ACP.ALLOW }
            .map { it.`object` }
            .toSet()
    }

    @Test
    fun `grant Edit surfaces as a single Edit row for the receiver`() {
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(bob))

        val list = shares(resource)
        assertEquals(1, list.size)
        assertEquals(ShareMode.WRITE, list.single().mode)
        assertEquals(ShareReceiver.WebIdReceiver(bob), list.single().receiver)
        assertTrue("owner self-control policy must be present", hasOwnerControl(resource))
    }

    @Test
    fun `container grant adds acp memberAccessControl for inheritance`() {
        grant(container, ShareMode.READ, ShareReceiver.WebIdReceiver(bob), container = true)
        assertTrue(
            "a container grant must mirror accessControl as memberAccessControl",
            acrQuadsOf(container).any { it.predicate == ACP.MEMBER_ACCESS_CONTROL },
        )
    }

    @Test
    fun `append-only grant with implied modes off allows only Append`() {
        runBlocking {
            backend.grant(
                alice, container, ShareMode.APPEND, ShareReceiver.Public,
                isContainer = true, includeImpliedModes = false,
            )
        }
        val publicShare = shares(container).single { it.receiver == ShareReceiver.Public }
        assertEquals(ShareMode.APPEND, publicShare.mode)
        assertEquals(
            "the public policy must allow only Append, with no implied Read",
            setOf(ACL.APPEND),
            publicPolicyModes(container),
        )
    }

    @Test
    fun `public grant round-trips as the Public receiver`() {
        grant(resource, ShareMode.READ, ShareReceiver.Public)
        assertEquals(ShareReceiver.Public, shares(resource).single().receiver)
    }

    @Test
    fun `re-granting a receiver replaces their policy without duplicate rows`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(bob))

        val list = shares(resource)
        assertEquals(1, list.size)
        assertEquals(ShareMode.WRITE, list.single().mode)
    }

    @Test
    fun `two receivers each get their own policy`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(carol))

        val byReceiver = shares(resource).associate { it.receiver to it.mode }
        assertEquals(ShareMode.READ, byReceiver[ShareReceiver.WebIdReceiver(bob)])
        assertEquals(ShareMode.WRITE, byReceiver[ShareReceiver.WebIdReceiver(carol)])
    }

    @Test
    fun `revoke removes the receiver policy but keeps owner control`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        runBlocking { backend.revoke(alice, resource, ShareReceiver.WebIdReceiver(bob), isContainer = false) }

        assertTrue(shares(resource).isEmpty())
        assertTrue("revoke must not strip the owner's own control", hasOwnerControl(resource))
    }

    @Test
    fun `revoking one of two receivers leaves the other intact`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        grant(resource, ShareMode.WRITE, ShareReceiver.WebIdReceiver(carol))
        runBlocking { backend.revoke(alice, resource, ShareReceiver.WebIdReceiver(bob), isContainer = false) }

        assertEquals(ShareReceiver.WebIdReceiver(carol), shares(resource).single().receiver)
    }

    @Test
    fun `grant fails fast on an unreadable ACR instead of wiping co-shares`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        pod.unreadable += pod.aclUriFor(resource).toString()
        val putsBefore = pod.putLog.size

        assertThrows(SharingException.UnsupportedAuthBackend::class.java) {
            grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(carol))
        }
        assertEquals(
            "an indeterminate ACR must not be overwritten (which would drop Bob's grant)",
            putsBefore, pod.putLog.size,
        )
    }

    @Test
    fun `revoke fails fast on an unreadable ACR`() {
        grant(resource, ShareMode.READ, ShareReceiver.WebIdReceiver(bob))
        pod.unreadable += pod.aclUriFor(resource).toString()
        val putsBefore = pod.putLog.size

        assertThrows(SharingException.UnsupportedAuthBackend::class.java) {
            runBlocking {
                backend.revoke(alice, resource, ShareReceiver.WebIdReceiver(bob), isContainer = false)
            }
        }
        assertEquals(putsBefore, pod.putLog.size)
    }

    @Test
    fun `grant rejects a group receiver on ACP and writes nothing`() {
        assertThrows(SharingException.UnsupportedAuthBackend::class.java) {
            grant(resource, ShareMode.READ, ShareReceiver.GroupReceiver("https://alice.pod/groups/friends#this"))
        }
        assertTrue("no ACR may be written for a rejected group grant", pod.putLog.isEmpty())
    }
}
