package com.erfangholami.androidsolidservices.shared.util

import java.net.URI

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that [encodeUri] / [encodeUriString] percent-encode only truly-illegal
 * characters and never corrupt identifiers that legitimately contain encoded
 * reserved characters (a decode/re-encode round-trip used to turn `%2F` into a
 * real path separator).
 */
class UriUtilsTest {

    @Test
    fun `already-encoded reserved characters are preserved`() {
        val raw = "https://pod.example/data/a%2Fb%23c/thing.ttl"
        assertEquals(raw, encodeUri(URI(raw)).toString())
    }

    @Test
    fun `encoding is idempotent`() {
        val raw = "https://pod.example/it%20em/x%2Fy"
        val once = encodeUri(URI(raw)).toString()
        val twice = encodeUri(URI(once)).toString()
        assertEquals(once, twice)
    }

    @Test
    fun `a raw space is percent-encoded`() {
        val encoded = encodeUriString("https://pod.example/my file.txt").toString()
        assertTrue(encoded.contains("%20"))
        assertTrue(!encoded.contains(' '))
    }

    @Test
    fun `unicode in the path is percent-encoded as UTF-8`() {
        val encoded = encodeUriString("https://pod.example/café/x").toString()
        assertTrue(encoded.contains("caf%C3%A9"))
    }

    @Test
    fun `encoded query and fragment survive`() {
        val raw = "https://pod.example/x?q=a%2Bb#frag%2Fment"
        assertEquals(raw, encodeUri(URI(raw)).toString())
    }

    @Test
    fun `an opaque uri is returned unchanged`() {
        val mailto = URI("mailto:jane@example.com")
        assertEquals(mailto, encodeUri(mailto))
    }

    @Test
    fun `a fragment webid is unchanged`() {
        val webId = "https://alice.pod/profile/card#me"
        assertEquals(webId, encodeUri(URI(webId)).toString())
    }
}
