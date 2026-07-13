package com.erfangholami.androidsolidservices.api.sharing

import org.junit.Assert.assertEquals
import org.junit.Test

class SolidShareProfileTest {

    private val podRoot = "https://alice.pod/"

    @Test
    fun `storage layout reproduces the existing solidshare paths byte-for-byte`() {
        val layout = SolidShareProfile.storageLayout
        assertEquals("https://alice.pod/solidshare/", layout.rootContainer(podRoot))
        assertEquals(
            "https://alice.pod/solidshare/shares/",
            layout.sharesContainer(podRoot),
        )
        assertEquals(
            "https://alice.pod/solidshare/shares/given_shares.ttl",
            layout.givenIndex(podRoot),
        )
        assertEquals(
            "https://alice.pod/solidshare/shares/received_shares.ttl",
            layout.receivedIndex(podRoot),
        )
        assertEquals("https://alice.pod/solidshare/catalog.ttl", layout.catalog(podRoot))
        assertEquals(
            listOf("/solidshare/", "/inbox/", "/profile/card"),
            layout.excludedScanPaths(),
        )
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
            override fun rootContainer(podRoot: String): String = "${podRoot}myapp/"
            override fun sharesContainer(podRoot: String): String = "${podRoot}myapp/idx/"
            override fun givenIndex(podRoot: String): String = "${podRoot}myapp/idx/given.ttl"
            override fun receivedIndex(podRoot: String): String =
                "${podRoot}myapp/idx/received.ttl"

            override fun catalog(podRoot: String): String = "${podRoot}myapp/catalog.ttl"
            override fun excludedScanPaths(): List<String> = listOf("/myapp/")
        }
        assertEquals("https://alice.pod/myapp/idx/given.ttl", custom.givenIndex(podRoot))
        assertEquals(listOf("/myapp/"), custom.excludedScanPaths())
    }
}
