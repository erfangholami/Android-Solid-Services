package com.erfangholami.androidsolidservices.api.resource

import com.erfangholami.androidsolidservices.api.notifications.FakeSolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOperationsTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val docUri = "https://alice.pod/profile/card"

    @Test
    fun `readProfile merges a foaf name that lives in an extended profile document`() {
        val extendedDoc = "https://alice.pod/profile/extended"
        val primary = WebId(
            webId,
            listOf(RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, extendedDoc, null, null)),
        )
        val extended = WebId(extendedDoc, listOf(RdfQuad(webId, FOAF.NAME, "Alice Jones", null, null)))
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
        val current = WebId(webId, listOf(RdfQuad(webId, FOAF.NAME, "Old Name", null, null)))
        var target: String? = null
        var captured: N3Patch? = null
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(current) },
            onPatch = { uri, p -> target = uri; captured = p; SolidResult.Success(Unit) },
        )

        runBlocking { rm.updateProfile(webId, name = "New Name") }

        assertEquals(docUri, target)
        val n3 = captured!!.toN3String()
        assertTrue("inserts the new name", n3.contains(FOAF.NAME) && n3.contains("New Name"))
        assertNotNull("replaces the existing name (has a delete clause)", captured!!.deletes)
    }

    @Test
    fun `updateProfile with no fields makes no patch`() {
        val current = WebId(webId, emptyList())
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
        val current = WebId(webId, emptyList())
        var avatarUri: String? = null
        var avatarBytes: ByteArray? = null
        var captured: N3Patch? = null
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(current) },
            onPatch = { _, p -> captured = p; SolidResult.Success(Unit) },
            onPutRaw = { uri, body, _ -> avatarUri = uri; avatarBytes = body; SolidResult.Success(Unit) },
        )

        runBlocking { rm.setAvatar(webId, "IMG".toByteArray(), "image/png") }

        assertEquals("https://alice.pod/profile/avatar.png", avatarUri)
        assertEquals("IMG", String(avatarBytes!!))
        assertTrue(captured!!.toN3String().contains(FOAF.IMG))
    }

    private val inruptWebId = "https://id.inrupt.com/alice"
    private val inruptStorage = "https://storage.inrupt.com/abc/"
    private val inruptExtended = "${inruptStorage}profile"

    private fun writable(): SolidMetadata = SolidMetadata.EMPTY.copy(
        wacAllow = WacAllow(userModes = setOf("read", "write", "control"), publicModes = emptySet()),
    )

    private fun inruptPrimary(): WebId = WebId(
        inruptWebId,
        listOf(
            RdfQuad(inruptWebId, FOAF.IS_PRIMARY_TOPIC_OF, inruptExtended),
            RdfQuad(inruptWebId, PIM.STORAGE, inruptStorage),
        ),
    )

    @Test
    fun `writableProfileDocument keeps a WebID document whose WAC-Allow grants write`() {
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(WebId(webId, emptyList())) },
            onHead = { uri ->
                if (uri == docUri) SolidResult.Success(writable()) else SolidResult.Failure(SolidError.fromHttp(404, "x"))
            },
        )

        assertEquals(docUri, runBlocking { rm.writableProfileDocument(webId).getOrThrow() })
    }

    @Test
    fun `writableProfileDocument picks the storage-hosted extended profile of an Inrupt WebID`() {
        val rm = FakeSolidResourceManager(
            onRead = { uri ->
                if (uri == inruptWebId) SolidResult.Success(inruptPrimary()) else SolidResult.Failure(SolidError.fromHttp(404, "x"))
            },
            onHead = { uri ->
                if (uri == inruptExtended) SolidResult.Success(writable()) else SolidResult.Failure(SolidError.fromHttp(401, "identity provider"))
            },
        )

        assertEquals(inruptExtended, runBlocking { rm.writableProfileDocument(inruptWebId).getOrThrow() })
    }

    @Test
    fun `writableProfileDocument falls back to the storage origin when no WAC-Allow is served`() {
        val rm = FakeSolidResourceManager(
            onRead = { uri ->
                if (uri == inruptWebId) SolidResult.Success(inruptPrimary()) else SolidResult.Failure(SolidError.fromHttp(404, "x"))
            },
            onHead = { SolidResult.Success(SolidMetadata.EMPTY) },
        )

        assertEquals(inruptExtended, runBlocking { rm.writableProfileDocument(inruptWebId).getOrThrow() })
    }

    @Test
    fun `updateProfile writes to the extended profile when the WebID document is read-only`() {
        val extendedDoc = WebId(inruptExtended, listOf(RdfQuad(inruptWebId, FOAF.NAME, "Old Name")))
        var target: String? = null
        var captured: N3Patch? = null
        val rm = FakeSolidResourceManager(
            onRead = { uri ->
                when (uri) {
                    inruptWebId -> SolidResult.Success(inruptPrimary())
                    inruptExtended -> SolidResult.Success(extendedDoc)
                    else -> SolidResult.Failure(SolidError.fromHttp(404, "x"))
                }
            },
            onHead = { uri ->
                if (uri == inruptExtended) SolidResult.Success(writable()) else SolidResult.Failure(SolidError.fromHttp(401, "identity provider"))
            },
            onPatch = { uri, p -> target = uri; captured = p; SolidResult.Success(Unit) },
        )

        runBlocking { rm.updateProfile(inruptWebId, name = "New Name") }

        assertEquals(inruptExtended, target)
        val n3 = captured!!.toN3String()
        assertTrue(n3.contains("New Name"))
        assertNotNull("the old name in the extended profile is replaced", captured!!.deletes)
    }

    @Test
    fun `setAvatar uploads beside the writable profile document`() {
        var avatarUri: String? = null
        var target: String? = null
        val rm = FakeSolidResourceManager(
            onRead = { uri ->
                when (uri) {
                    inruptWebId -> SolidResult.Success(inruptPrimary())
                    inruptExtended -> SolidResult.Success(WebId(inruptExtended, emptyList()))
                    else -> SolidResult.Failure(SolidError.fromHttp(404, "x"))
                }
            },
            onHead = { uri ->
                if (uri == inruptExtended) SolidResult.Success(writable()) else SolidResult.Failure(SolidError.fromHttp(401, "identity provider"))
            },
            onPatch = { uri, _ -> target = uri; SolidResult.Success(Unit) },
            onPutRaw = { uri, _, _ -> avatarUri = uri; SolidResult.Success(Unit) },
        )

        runBlocking { rm.setAvatar(inruptWebId, "IMG".toByteArray(), "image/png") }

        assertEquals("${inruptStorage}avatar.png", avatarUri)
        assertEquals(inruptExtended, target)
    }

    @Test
    fun `reified read forwards to the Class token overload`() {
        val profile = WebId(webId, emptyList())
        val rm = FakeSolidResourceManager(onRead = { SolidResult.Success(profile) })

        val result = runBlocking { rm.read<WebId>(webId, webId) }

        assertTrue(result is SolidResult.Success)
        assertSame(profile, (result as SolidResult.Success).value)
    }
}
