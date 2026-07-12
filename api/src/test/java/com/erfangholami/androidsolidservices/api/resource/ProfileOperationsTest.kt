package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.notifications.FakeSolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Tests the profile conveniences ([readProfile], [updateProfile], [setAvatar]) and the
 * reified [read] extension over the configurable [FakeSolidResourceManager].
 */
class ProfileOperationsTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val docUri = "https://alice.pod/profile/card"

    @Test
    fun `readProfile merges a foaf name that lives in an extended profile document`() {
        val extendedDoc = "https://alice.pod/profile/extended"
        val primary = WebId(
            URI.create(webId),
            listOf(RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, extendedDoc, null, null)),
        )
        val extended = WebId(URI.create(extendedDoc), listOf(RdfQuad(webId, FOAF.NAME, "Alice Jones", null, null)))
        val rm = FakeSolidResourceManager(onReadPublic = { uri ->
            when (uri.toString()) {
                webId -> SolidResult.Success(primary)
                extendedDoc -> SolidResult.Success(extended)
                else -> SolidResult.Failure(com.erfangholami.androidsolidservices.shared.result.SolidError.fromHttp(404, "x"))
            }
        })

        val profile = runBlocking { rm.readProfile(webId).getOrThrow() }

        assertEquals("Alice Jones", profile.getName())
    }

    @Test
    fun `updateProfile safe-replaces an existing foaf name on the profile document`() {
        val current = WebId(URI.create(webId), listOf(RdfQuad(webId, FOAF.NAME, "Old Name", null, null)))
        var target: URI? = null
        var captured: N3Patch? = null
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(current) },
            onPatch = { uri, p -> target = uri; captured = p; SolidResult.Success(Unit) },
        )

        runBlocking { rm.updateProfile(webId, name = "New Name") }

        assertEquals(URI.create(docUri), target)
        val n3 = captured!!.toN3String()
        assertTrue("inserts the new name", n3.contains(FOAF.NAME) && n3.contains("New Name"))
        assertNotNull("replaces the existing name (has a delete clause)", captured!!.deletes)
    }

    @Test
    fun `updateProfile with no fields makes no patch`() {
        val current = WebId(URI.create(webId), emptyList())
        var patched = false
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(current) },
            onPatch = { _, _ -> patched = true; SolidResult.Success(Unit) },
        )

        val result = runBlocking { rm.updateProfile(webId) }

        assertTrue(result is SolidResult.Success)
        assertFalse("nothing to change → no patch", patched)
    }

    @Test
    fun `setAvatar uploads a sibling image and points foaf img at it`() {
        val current = WebId(URI.create(webId), emptyList())
        var avatarUri: URI? = null
        var avatarBytes: ByteArray? = null
        var captured: N3Patch? = null
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(current) },
            onPatch = { _, p -> captured = p; SolidResult.Success(Unit) },
            onPutRaw = { uri, body, _ -> avatarUri = uri; avatarBytes = body; SolidResult.Success(Unit) },
        )

        runBlocking { rm.setAvatar(webId, "IMG".toByteArray(), "image/png") }

        assertEquals(URI.create("https://alice.pod/profile/avatar.png"), avatarUri)
        assertEquals("IMG", String(avatarBytes!!))
        assertTrue(captured!!.toN3String().contains(FOAF.IMG))
    }

    @Test
    fun `reified read forwards to the Class token overload`() {
        val profile = WebId(URI.create(webId), emptyList())
        val rm = FakeSolidResourceManager(onRead = { SolidResult.Success(profile) })

        val result = runBlocking { rm.read<WebId>(webId, URI.create(webId)) }

        assertTrue(result is SolidResult.Success)
        assertSame(profile, (result as SolidResult.Success).value)
    }
}
