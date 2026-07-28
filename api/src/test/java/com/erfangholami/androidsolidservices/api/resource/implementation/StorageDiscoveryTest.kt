package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.notifications.FakeSolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

class StorageDiscoveryTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val root = "https://alice.pod/"

    private fun profile(vararg quads: RdfQuad): WebId = WebId(webId, quads.toList())

    @Test
    fun `discovers storage from the pim storage triple`() {
        val profile = profile(RdfQuad(webId, PIM.STORAGE, root.toString(), null, null))
        val rm = FakeSolidResourceManager(onRead = { SolidResult.Success(profile) })

        assertEquals(root, runBlocking { StorageDiscovery.discover(rm, webId) })
    }

    @Test
    fun `walks up to the container advertising pim Storage when the profile has no triple`() {
        val profile = profile()
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(profile) },
            onHead = { uri ->
                SolidResult.Success(SolidMetadata.EMPTY.copy(isStorage = uri == root))
            },
        )

        assertEquals(root, runBlocking { StorageDiscovery.discover(rm, webId) })
    }

    @Test
    fun `returns null when neither the profile nor the hierarchy reveals a storage`() {
        val profile = profile()
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(profile) },
            onHead = { SolidResult.Success(SolidMetadata.EMPTY) },
        )

        assertNull(runBlocking { StorageDiscovery.discover(rm, webId) })
    }
}
