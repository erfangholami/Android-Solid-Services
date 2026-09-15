package com.erfangholami.androidsolidservices.shared.model.grant

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A requested path is untrusted input from another app, resolved by the host against the user's
 * storage. Every way it could escape the root has to come back as `null`, never as an IRI.
 */
class RequestedTargetTest {

    private val storage = "https://alice.pod/"

    @Test
    fun `a container path resolves below the storage root and keeps its trailing slash`() {
        assertEquals("https://alice.pod/notes/", RequestedTarget.Path("notes/").resolveAgainst(storage))
    }

    @Test
    fun `a file path resolves without a trailing slash`() {
        assertEquals(
            "https://alice.pod/photos/2026/trip.jpg",
            RequestedTarget.Path("photos/2026/trip.jpg").resolveAgainst(storage),
        )
    }

    @Test
    fun `leading slashes on the path are ignored`() {
        assertEquals("https://alice.pod/notes/", RequestedTarget.Path("/notes/").resolveAgainst(storage))
        assertEquals("https://alice.pod/notes/", RequestedTarget.Path("///notes/").resolveAgainst(storage))
    }

    @Test
    fun `a storage root without a trailing slash still joins with exactly one`() {
        assertEquals("https://alice.pod/notes/", RequestedTarget.Path("notes/").resolveAgainst("https://alice.pod"))
    }

    @Test
    fun `a blank path is not a target`() {
        assertNull(RequestedTarget.Path("").resolveAgainst(storage))
        assertNull(RequestedTarget.Path("   ").resolveAgainst(storage))
        assertNull(RequestedTarget.Path("/").resolveAgainst(storage))
    }

    @Test
    fun `a path cannot step outside the root`() {
        assertNull(RequestedTarget.Path("../etc/").resolveAgainst(storage))
        assertNull(RequestedTarget.Path("notes/../private/").resolveAgainst(storage))
        assertNull(RequestedTarget.Path("./notes/").resolveAgainst(storage))
    }

    @Test
    fun `a path cannot smuggle an absolute IRI`() {
        assertNull(RequestedTarget.Path("https://bob.pod/notes/").resolveAgainst(storage))
    }

    @Test
    fun `an empty segment in the middle of a path is rejected`() {
        assertNull(RequestedTarget.Path("notes//2026/").resolveAgainst(storage))
    }

    @Test
    fun `the persisted shape names each kind by its serial name`() {
        val targets = listOf(
            RequestedTarget.Pod,
            RequestedTarget.Path("notes/"),
            RequestedTarget.Module("tickets"),
        )
        val json = Json.encodeToString(ListSerializer(RequestedTarget.serializer()), targets)

        assertEquals(
            """[{"type":"pod"},{"type":"path","relativePath":"notes/"},{"type":"module","id":"tickets"}]""",
            json,
        )
        assertEquals(targets, Json.decodeFromString(ListSerializer(RequestedTarget.serializer()), json))
    }
}
