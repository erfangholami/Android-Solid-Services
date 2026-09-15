package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.CALLER
import com.erfangholami.androidsolidservices.host.testing.FakeModuleRoots
import com.erfangholami.androidsolidservices.host.testing.InMemoryGrantStore
import com.erfangholami.androidsolidservices.host.testing.entry
import com.erfangholami.androidsolidservices.host.testing.grant
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy is the whole point of scoped grants, so every way an entry can cover a call, and
 * every way it cannot, is pinned here against the plan's verb table.
 */
class ScopedAccessPolicyTest {

    private val contactsRoot = "https://alice.pod/datamodule/contacts/"
    private val roots = FakeModuleRoots(mapOf(DataModuleId.CONTACTS to listOf(contactsRoot)))

    private fun policyWith(vararg grants: AppGrant) = ScopedAccessPolicy(InMemoryGrantStore(grants.toList()), roots)

    private fun ScopedAccessPolicy.check(target: VerbTarget, level: AccessLevel): AccessCheck =
        runBlocking { check(CALLER, ALICE, target, level) }

    private fun assertDenied(check: AccessCheck, vararg mentions: String) {
        assertTrue("expected a denial, got $check", check is AccessCheck.Denied)
        check as AccessCheck.Denied
        assertEquals(ExceptionsErrorCode.NOT_PERMISSION, check.code)
        mentions.forEach { assertTrue("'${check.message}' should mention '$it'", check.message.contains(it)) }
    }

    @Test
    fun `an app with no grant is denied everything`() {
        val policy = policyWith()

        assertDenied(policy.check(VerbTarget.Resource("https://alice.pod/x"), AccessLevel.VIEW), CALLER, ALICE)
        assertDenied(policy.check(VerbTarget.Pod, AccessLevel.VIEW))
        assertDenied(policy.check(VerbTarget.AnyEntry, AccessLevel.VIEW))
    }

    @Test
    fun `a pod entry covers a resource, a module and the pod up to its level`() {
        val policy = policyWith(grant(entry(GrantTarget.Pod, AccessLevel.ADD)))

        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Resource("https://alice.pod/notes/a"), AccessLevel.ADD))
        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Module(DataModuleId.TICKETS), AccessLevel.VIEW))
        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Pod, AccessLevel.ADD))
        assertDenied(policy.check(VerbTarget.Pod, AccessLevel.EDIT), "ADD", "EDIT", "the whole pod")
    }

    @Test
    fun `a resource entry covers its subtree and nothing else`() {
        val policy = policyWith(grant(entry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.EDIT)))

        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Resource("https://alice.pod/notes/a.txt"), AccessLevel.EDIT))
        assertDenied(policy.check(VerbTarget.Resource("https://alice.pod/notes/a.txt"), AccessLevel.FULL), "EDIT", "FULL")
        assertDenied(policy.check(VerbTarget.Resource("https://alice.pod/photos/b.jpg"), AccessLevel.VIEW), "https://alice.pod/photos/b.jpg")
        assertDenied(policy.check(VerbTarget.Pod, AccessLevel.VIEW))
        assertDenied(policy.check(VerbTarget.Module(DataModuleId.CONTACTS), AccessLevel.VIEW), "contacts module")
    }

    @Test
    fun `a module entry covers the module's verbs and the containers the host resolves`() {
        val policy = policyWith(grant(entry(GrantTarget.Module(DataModuleId.CONTACTS), AccessLevel.VIEW)))

        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Module(DataModuleId.CONTACTS), AccessLevel.VIEW))
        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Resource("${contactsRoot}b1/index.ttl"), AccessLevel.VIEW))
        assertDenied(policy.check(VerbTarget.Module(DataModuleId.CONTACTS), AccessLevel.ADD), "VIEW", "ADD")
        assertDenied(policy.check(VerbTarget.Module(DataModuleId.TICKETS), AccessLevel.VIEW))
        assertDenied(policy.check(VerbTarget.Resource("https://alice.pod/datamodule/tickets/t/ticket"), AccessLevel.VIEW))
    }

    @Test
    fun `module roots are resolved only when the grant holds a module entry`() {
        val policy = policyWith(grant(entry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.VIEW)))

        policy.check(VerbTarget.Resource("https://alice.pod/notes/a"), AccessLevel.VIEW)

        assertTrue(roots.resolved.isEmpty())
    }

    @Test
    fun `any entry satisfies the profile read at that entry's level`() {
        val policy = policyWith(grant(entry(GrantTarget.Module(DataModuleId.TICKETS), AccessLevel.VIEW)))

        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.AnyEntry, AccessLevel.VIEW))
        assertDenied(policy.check(VerbTarget.AnyEntry, AccessLevel.ADD), "this account")
    }

    @Test
    fun `the highest covering entry decides`() {
        val policy = policyWith(
            grant(
                entry(GrantTarget.Pod, AccessLevel.VIEW),
                entry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.FULL),
            ),
        )

        assertEquals(AccessCheck.Allowed, policy.check(VerbTarget.Resource("https://alice.pod/notes/a"), AccessLevel.FULL))
        assertDenied(policy.check(VerbTarget.Resource("https://alice.pod/photos/a"), AccessLevel.ADD), "VIEW", "ADD")
    }

    @Test
    fun `grants are keyed by package and account`() {
        val policy = policyWith(
            grant(entry(GrantTarget.Pod, AccessLevel.FULL), packageName = "com.other.app"),
            grant(entry(GrantTarget.Pod, AccessLevel.FULL), webId = "https://bob.pod/profile/card#me"),
        )

        assertDenied(policy.check(VerbTarget.Pod, AccessLevel.VIEW), CALLER)
    }
}
