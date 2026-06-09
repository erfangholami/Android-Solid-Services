package com.erfangholami.androidsolidservices.api.sharing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class SolidShareProfileTest {

    private val podRoot = URI.create("https://alice.pod/")

    @Test
    fun `storage layout reproduces the existing solidshare paths byte-for-byte`() {
        val layout = SolidShareProfile.storageLayout
        assertEquals(URI.create("https://alice.pod/solidshare/"), layout.rootContainer(podRoot))
        assertEquals(
            URI.create("https://alice.pod/solidshare/shares/"),
            layout.sharesContainer(podRoot),
        )
        assertEquals(
            URI.create("https://alice.pod/solidshare/shares/given_shares.ttl"),
            layout.givenIndex(podRoot),
        )
        assertEquals(
            URI.create("https://alice.pod/solidshare/shares/received_shares.ttl"),
            layout.receivedIndex(podRoot),
        )
        assertEquals(URI.create("https://alice.pod/solidshare/catalog.ttl"), layout.catalog(podRoot))
        assertEquals(listOf("/solidshare/"), layout.excludedScanPaths())
    }

    @Test
    fun `vocabulary reproduces the existing solidshare namespace`() {
        val vocab = SolidShareProfile.vocabulary
        assertEquals("https://solidshare.com/ns#", vocab.namespace)
        assertEquals("https://solidshare.com/ns#Share", vocab.shareType)
        assertEquals("https://solidshare.com/ns#resource", vocab.resource)
        assertEquals("https://solidshare.com/ns#receiver", vocab.receiver)
        assertEquals("https://solidshare.com/ns#owner", vocab.owner)
        assertEquals("https://solidshare.com/ns#CatalogEntry", vocab.catalogEntryType)
    }

    @Test
    fun `catalog feature is enabled by default`() {
        assertEquals(true, SolidShareProfile.catalogEnabled)
    }

    @Test
    fun `a custom layout retargets where the engine stores its bookkeeping`() {
        val custom = object : ShareStorageLayout {
            override fun rootContainer(podRoot: URI): URI = URI.create("${podRoot}myapp/")
            override fun sharesContainer(podRoot: URI): URI = URI.create("${podRoot}myapp/idx/")
            override fun givenIndex(podRoot: URI): URI = URI.create("${podRoot}myapp/idx/given.ttl")
            override fun receivedIndex(podRoot: URI): URI =
                URI.create("${podRoot}myapp/idx/received.ttl")

            override fun catalog(podRoot: URI): URI = URI.create("${podRoot}myapp/catalog.ttl")
            override fun excludedScanPaths(): List<String> = listOf("/myapp/")
        }
        assertEquals(URI.create("https://alice.pod/myapp/idx/given.ttl"), custom.givenIndex(podRoot))
        assertEquals(listOf("/myapp/"), custom.excludedScanPaths())
    }
}
