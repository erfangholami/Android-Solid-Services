package com.erfangholami.androidsolidservices.shared.model.grant

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder is the whole model: `includes` compares ordinals, and a host persists the names.
 * Both are pinned so a reorder or a rename cannot pass unnoticed.
 */
class AccessLevelTest {

    @Test
    fun `the ladder is View, Add, Edit, Full in that order`() {
        assertEquals(
            listOf(AccessLevel.VIEW, AccessLevel.ADD, AccessLevel.EDIT, AccessLevel.FULL),
            AccessLevel.entries,
        )
    }

    @Test
    fun `a level includes itself and everything below it`() {
        AccessLevel.entries.forEach { level ->
            AccessLevel.entries.forEach { required ->
                assertEquals(
                    "$level includes $required",
                    level.ordinal >= required.ordinal,
                    level.includes(required),
                )
            }
        }
        assertTrue(AccessLevel.FULL.includes(AccessLevel.VIEW))
        assertFalse(AccessLevel.EDIT.includes(AccessLevel.FULL))
        assertFalse(AccessLevel.VIEW.includes(AccessLevel.ADD))
    }

    @Test
    fun `strongest picks the most permissive level and is null when empty`() {
        assertEquals(AccessLevel.EDIT, AccessLevel.strongest(listOf(AccessLevel.VIEW, AccessLevel.EDIT, AccessLevel.ADD)))
        assertEquals(AccessLevel.VIEW, AccessLevel.strongest(listOf(AccessLevel.VIEW)))
        assertNull(AccessLevel.strongest(emptyList()))
    }

    @Test
    fun `the persisted names are the enum names`() {
        AccessLevel.entries.forEach { level ->
            assertEquals("\"${level.name}\"", Json.encodeToString(AccessLevel.serializer(), level))
        }
    }
}
