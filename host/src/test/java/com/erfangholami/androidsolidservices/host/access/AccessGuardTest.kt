package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.FakeHostSession
import com.erfangholami.androidsolidservices.host.testing.RecordingPolicy
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard orders its three gates: caller, session, policy. Each stops the chain with the
 * code the client SDK expects, and the policy is never asked about a caller it cannot name or
 * an account with no session.
 */
class AccessGuardTest {

    private val session = FakeHostSession()
    private val policy = RecordingPolicy()
    private val resource = Requirement(VerbTarget.Resource("https://alice.pod/notes/a"), AccessLevel.VIEW)

    @Test
    fun `an unresolvable caller is refused before the session is consulted`() = runBlocking {
        val guard = AccessGuard(session, policy) { null }

        val check = guard.check(null, ALICE, resource)

        assertEquals(ExceptionsErrorCode.UNKNOWN, (check as AccessCheck.Denied).code)
        assertTrue(policy.checks.isEmpty())
    }

    @Test
    fun `an account with no live session is refused before the policy runs`() = runBlocking {
        session.sessions.clear()
        val guard = AccessGuard(session, policy) { CALLER }

        val check = guard.check(CALLER, ALICE, resource)

        assertEquals(ExceptionsErrorCode.SOLID_NOT_LOGGED_IN, (check as AccessCheck.Denied).code)
        assertTrue(check.message.contains(ALICE))
        assertTrue(policy.checks.isEmpty())
    }

    @Test
    fun `every requirement is put to the policy in order and the first denial wins`() = runBlocking {
        val guard = AccessGuard(session, policy) { CALLER }
        val destination = Requirement(VerbTarget.Resource("https://alice.pod/notes/b"), AccessLevel.EDIT)

        assertEquals(AccessCheck.Allowed, guard.check(CALLER, ALICE, resource, destination))
        assertEquals(
            listOf(
                RecordingPolicy.Check(CALLER, ALICE, resource.target, resource.level),
                RecordingPolicy.Check(CALLER, ALICE, destination.target, destination.level),
            ),
            policy.checks,
        )

        policy.answer = AccessCheck.Denied(ExceptionsErrorCode.NOT_PERMISSION, "no")
        policy.checks.clear()

        assertEquals(policy.answer, guard.check(CALLER, ALICE, resource, destination))
        assertEquals(1, policy.checks.size)
    }

    @Test
    fun `the session is awaited before anything else is answered`() = runBlocking {
        val guard = AccessGuard(session, policy) { CALLER }

        guard.check(CALLER, ALICE, resource)

        assertEquals(1, session.readyCalls)
    }

    @Test
    fun `a gate captures the caller when it is made, not when it runs`() = runBlocking {
        var caller: String? = CALLER
        val guard = AccessGuard(session, policy) { caller }

        val gate = guard.gate(ALICE, VerbTarget.Pod, AccessLevel.VIEW)
        caller = null

        assertEquals(AccessCheck.Allowed, gate())
        assertEquals(CALLER, policy.checks.single().caller)
    }
}
