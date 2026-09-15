package com.erfangholami.androidsolidservices.shared.model.grant

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantTargetTest {

    private val container = GrantTarget.Resource("https://alice.pod/notes/")
    private val file = GrantTarget.Resource("https://alice.pod/notes/todo.txt")

    @Test
    fun `a resource covers itself`() {
        assertTrue(container.covers("https://alice.pod/notes/"))
        assertTrue(file.covers("https://alice.pod/notes/todo.txt"))
    }

    @Test
    fun `a container covers everything below it`() {
        assertTrue(container.covers("https://alice.pod/notes/todo.txt"))
        assertTrue(container.covers("https://alice.pod/notes/2026/"))
        assertTrue(container.covers("https://alice.pod/notes/2026/deep/file.md"))
    }

    @Test
    fun `a container does not cover a sibling that shares its name as a prefix`() {
        assertFalse(container.covers("https://alice.pod/notes2/"))
        assertFalse(container.covers("https://alice.pod/notes2/todo.txt"))
        assertFalse(container.covers("https://alice.pod/notes"))
    }

    @Test
    fun `a plain resource covers nothing but itself`() {
        assertFalse(file.covers("https://alice.pod/notes/todo.txt.bak"))
        assertFalse(file.covers("https://alice.pod/notes/todo.txt/"))
        assertFalse(file.covers("https://alice.pod/notes/"))
    }

    @Test
    fun `coverage never crosses hosts`() {
        assertFalse(container.covers("https://bob.pod/notes/todo.txt"))
    }

    @Test
    fun `the persisted shape names each kind by its serial name`() {
        val targets = listOf(
            GrantTarget.Pod,
            GrantTarget.Resource("https://alice.pod/notes/"),
            GrantTarget.Module("contacts"),
        )
        val json = Json.encodeToString(ListSerializer(GrantTarget.serializer()), targets)

        assertEquals(
            """[{"type":"pod"},{"type":"resource","uri":"https://alice.pod/notes/"},{"type":"module","id":"contacts"}]""",
            json,
        )
        assertEquals(targets, Json.decodeFromString(ListSerializer(GrantTarget.serializer()), json))
    }
}
