package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.host.testing.ALICE
import com.erfangholami.androidsolidservices.host.testing.FakeModuleRoots
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CachingModuleRootResolverTest {

    private val root = "https://alice.pod/datamodule/contacts/"
    private val delegate = FakeModuleRoots(mapOf(DataModuleId.CONTACTS to listOf(root)))
    private var now = 1_000L
    private val resolver = CachingModuleRootResolver(delegate, ttlMillis = 100, clock = { now })

    @Test
    fun `a second lookup inside the ttl is answered from memory`() = runBlocking {
        assertEquals(listOf(root), resolver.rootContainers(DataModuleId.CONTACTS, ALICE))
        assertEquals(listOf(root), resolver.rootContainers(DataModuleId.CONTACTS, ALICE))

        assertEquals(1, delegate.resolved.size)
    }

    @Test
    fun `an expired answer is resolved again`() = runBlocking {
        resolver.rootContainers(DataModuleId.CONTACTS, ALICE)
        now += 101

        resolver.rootContainers(DataModuleId.CONTACTS, ALICE)

        assertEquals(2, delegate.resolved.size)
    }

    @Test
    fun `invalidating an account forgets that account only`() = runBlocking {
        val bob = "https://bob.pod/profile/card#me"
        resolver.rootContainers(DataModuleId.CONTACTS, ALICE)
        resolver.rootContainers(DataModuleId.CONTACTS, bob)

        resolver.invalidate(ALICE)
        resolver.rootContainers(DataModuleId.CONTACTS, ALICE)
        resolver.rootContainers(DataModuleId.CONTACTS, bob)

        assertEquals(3, delegate.resolved.size)
    }

    @Test
    fun `modules and accounts are cached apart`() = runBlocking {
        resolver.rootContainers(DataModuleId.CONTACTS, ALICE)
        assertEquals(emptyList<String>(), resolver.rootContainers(DataModuleId.TICKETS, ALICE))

        assertEquals(listOf(DataModuleId.CONTACTS, DataModuleId.TICKETS), delegate.resolved)
    }
}
