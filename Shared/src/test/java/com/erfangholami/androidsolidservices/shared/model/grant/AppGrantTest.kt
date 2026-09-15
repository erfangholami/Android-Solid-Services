package com.erfangholami.androidsolidservices.shared.model.grant

import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The effective level is "the highest level among the entries that cover the target". These
 * tests pin each way an entry can cover a call, and that nothing else does.
 */
class AppGrantTest {

    private val contactsRoots = mapOf(
        DataModuleId.CONTACTS to listOf("https://alice.pod/datamodule/contacts/"),
    )
    private val roots: (String) -> List<String> = { id -> contactsRoots[id].orEmpty() }

    private fun grant(vararg entries: GrantEntry) = AppGrant(
        packageName = "com.example.notes",
        webId = "https://alice.pod/profile/card#me",
        appLabel = "Notes",
        entries = entries.toList(),
        grantedAt = "2026-09-14T10:00:00Z",
    )

    @Test
    fun `a pod entry covers every resource and every module`() {
        val g = grant(GrantEntry(GrantTarget.Pod, AccessLevel.ADD))

        assertEquals(AccessLevel.ADD, g.podLevel())
        assertEquals(AccessLevel.ADD, g.levelOn("https://alice.pod/anything/at/all", roots))
        assertEquals(AccessLevel.ADD, g.levelOnModule(DataModuleId.TICKETS))
    }

    @Test
    fun `a resource entry covers its subtree and nothing else`() {
        val g = grant(GrantEntry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.EDIT))

        assertNull(g.podLevel())
        assertEquals(AccessLevel.EDIT, g.levelOn("https://alice.pod/notes/todo.txt", roots))
        assertNull(g.levelOn("https://alice.pod/photos/trip.jpg", roots))
        assertNull(g.levelOnModule(DataModuleId.CONTACTS))
    }

    @Test
    fun `a module entry covers the module's verbs and the containers the host resolves for it`() {
        val g = grant(GrantEntry(GrantTarget.Module(DataModuleId.CONTACTS), AccessLevel.VIEW))

        assertEquals(AccessLevel.VIEW, g.levelOnModule(DataModuleId.CONTACTS))
        assertNull(g.levelOnModule(DataModuleId.TICKETS))
        assertEquals(AccessLevel.VIEW, g.levelOn("https://alice.pod/datamodule/contacts/abc/index.ttl", roots))
        assertNull(g.levelOn("https://alice.pod/datamodule/tickets/xyz/ticket", roots))
    }

    @Test
    fun `a module with no resolved containers covers no resource`() {
        val g = grant(GrantEntry(GrantTarget.Module(DataModuleId.TICKETS), AccessLevel.FULL))

        assertEquals(AccessLevel.FULL, g.levelOnModule(DataModuleId.TICKETS))
        assertNull(g.levelOn("https://alice.pod/datamodule/tickets/xyz/ticket", roots))
    }

    @Test
    fun `the highest covering entry wins`() {
        val g = grant(
            GrantEntry(GrantTarget.Pod, AccessLevel.VIEW),
            GrantEntry(GrantTarget.Resource("https://alice.pod/notes/"), AccessLevel.FULL),
            GrantEntry(GrantTarget.Resource("https://alice.pod/notes/2026/"), AccessLevel.ADD),
        )

        assertEquals(AccessLevel.FULL, g.levelOn("https://alice.pod/notes/2026/todo.txt", roots))
        assertEquals(AccessLevel.VIEW, g.levelOn("https://alice.pod/photos/", roots))
        assertEquals(AccessLevel.VIEW, g.podLevel())
    }

    @Test
    fun `no entries means no level`() {
        val g = grant()

        assertNull(g.podLevel())
        assertNull(g.levelOn("https://alice.pod/notes/", roots))
        assertNull(g.levelOnModule(DataModuleId.CONTACTS))
    }
}
