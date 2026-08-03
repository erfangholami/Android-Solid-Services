package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.testing.InMemoryPodResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidResourceManagerCapabilitiesTest {

    private val webId = "https://alice.pod/profile/card#me"

    @Test
    fun `exists is true for a present resource and false for a 404`() {
        val pod = InMemoryPodResourceManager()
        val present = "https://alice.pod/notes/n1"
        pod.put(SolidContainer(present))

        assertTrue(runBlocking { pod.exists(webId, present.toString()).getOrThrow() })
        assertFalse(runBlocking { pod.exists(webId, "https://alice.pod/notes/gone").getOrThrow() })
    }

    @Test
    fun `ensureContainer creates the target and its missing ancestors, stopping at an existing one`() {
        val pod = InMemoryPodResourceManager()
        pod.put(SolidContainer("https://alice.pod/"))
        val target = "https://alice.pod/a/b/c/"

        val result = runBlocking { pod.ensureContainer(webId, target) }

        assertTrue(result is SolidResult.Success)
        assertTrue(runBlocking { pod.exists(webId, "https://alice.pod/a/").getOrThrow() })
        assertTrue(runBlocking { pod.exists(webId, "https://alice.pod/a/b/").getOrThrow() })
        assertTrue(runBlocking { pod.exists(webId, target).getOrThrow() })
    }

    @Test
    fun `ensureContainer is a no-op when the container already exists`() {
        val pod = InMemoryPodResourceManager()
        val container = "https://alice.pod/c/"
        pod.put(SolidContainer(container))
        val before = pod.store.size

        val result = runBlocking { pod.ensureContainer(webId, container.toString()) }

        assertTrue(result is SolidResult.Success)
        assertEquals("no create when the container is already present", before, pod.store.size)
    }
}
