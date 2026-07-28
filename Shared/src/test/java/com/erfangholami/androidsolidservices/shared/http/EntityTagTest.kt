package com.erfangholami.androidsolidservices.shared.http

import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.util.getEntityTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityTagTest {

    @Test
    fun `parses a strong tag, stripping the quotes`() {
        val tag = EntityTag.parse("\"abc123\"")
        assertEquals(EntityTag("abc123", weak = false), tag)
    }

    @Test
    fun `parses a weak tag, dropping the W-slash prefix and quotes`() {
        val tag = EntityTag.parse("W/\"abc123\"")
        assertEquals("abc123", tag?.value)
        assertTrue(tag!!.weak)
    }

    @Test
    fun `treats the weak prefix case-insensitively`() {
        assertTrue(EntityTag.parse("w/\"x\"")!!.weak)
    }

    @Test
    fun `tolerates an unquoted value`() {
        assertEquals(EntityTag("bare", weak = false), EntityTag.parse("bare"))
    }

    @Test
    fun `returns null for absent or blank input`() {
        assertNull(EntityTag.parse(null))
        assertNull(EntityTag.parse("   "))
    }

    @Test
    fun `getEntityTag surfaces a weak validator that getETag hides`() {
        val headers = SolidHeaders(mapOf("ETag" to listOf("W/\"weak-1\"")))
        assertEquals("weak-1", headers.getEntityTag()?.value)
        assertTrue(headers.getEntityTag()!!.weak)
        assertNull(headers.getETag())
    }

    @Test
    fun `getETag returns the bare value for a strong validator`() {
        val headers = SolidHeaders(mapOf("etag" to listOf("\"strong-1\"")))
        assertEquals("strong-1", headers.getETag())
        assertFalse(headers.getEntityTag()!!.weak)
    }
}
