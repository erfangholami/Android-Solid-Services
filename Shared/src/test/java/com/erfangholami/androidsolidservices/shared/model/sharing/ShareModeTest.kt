package com.erfangholami.androidsolidservices.shared.model.sharing

import com.erfangholami.androidsolidservices.shared.vocab.ACL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the WAC mode mapping that underpins every grant: View/Add/Edit ↔
 * Read/Append/Write, the explicit implied-Read expansion (WAC has no mode
 * subsumption), and the "strongest wins" fold used to collapse a grant's
 * several acl:modes back to one logical level.
 */
class ShareModeTest {

    @Test
    fun `impliedAclModes expands each level, always including Read`() {
        assertEquals(setOf(ACL.READ), ShareMode.READ.impliedAclModes())
        assertEquals(setOf(ACL.READ, ACL.APPEND), ShareMode.APPEND.impliedAclModes())
        assertEquals(setOf(ACL.READ, ACL.WRITE), ShareMode.WRITE.impliedAclModes())
    }

    @Test
    fun `toAclPredicate and fromAclPredicate round-trip every mode`() {
        ShareMode.entries.forEach { mode ->
            assertEquals(mode, ShareMode.fromAclPredicate(mode.toAclPredicate()))
        }
    }

    @Test
    fun `fromAclPredicate is null for an unknown predicate`() {
        assertNull(ShareMode.fromAclPredicate(ACL.CONTROL))
    }

    @Test
    fun `strongest over predicates is Write over Append over Read`() {
        assertEquals(ShareMode.WRITE, ShareMode.strongest(setOf(ACL.READ, ACL.APPEND, ACL.WRITE)))
        assertEquals(ShareMode.APPEND, ShareMode.strongest(setOf(ACL.READ, ACL.APPEND)))
        assertEquals(ShareMode.READ, ShareMode.strongest(setOf(ACL.READ)))
        assertNull(ShareMode.strongest(emptySet<String>()))
        assertNull(ShareMode.strongest(setOf(ACL.CONTROL)))
    }

    @Test
    fun `strongest over modes is Write over Append over Read`() {
        assertEquals(ShareMode.WRITE, ShareMode.strongest(listOf(ShareMode.READ, ShareMode.WRITE)))
        assertEquals(ShareMode.APPEND, ShareMode.strongest(listOf(ShareMode.READ, ShareMode.APPEND)))
        assertEquals(ShareMode.READ, ShareMode.strongest(listOf(ShareMode.READ)))
        assertNull(ShareMode.strongest(emptyList()))
    }
}
