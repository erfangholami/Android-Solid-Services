package com.erfangholami.androidsolidservices.api.datamodule.typeindex

import com.erfangholami.androidsolidservices.api.notifications.FakeSolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Verifies the type-index bootstrap registers its link on the profile with a
 * targeted N3 PATCH rather than a full-document PUT — a PUT would re-serialise and
 * overwrite the whole profile, silently dropping concurrent or server-managed
 * triples and needing Write on the entire document.
 */
class TypeIndexResolverTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val profileDoc = "https://alice.pod/profile/card"
    private val storage = "https://alice.pod/"

    @Test
    fun `bootstrapping the private type index registers the link via PATCH, not a full-document PUT`() {
        val profileQuads = listOf(
            RdfQuad(webId, PIM.STORAGE, storage, null, null),
            RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, profileDoc, null, null),
        )
        val patched = mutableListOf<Pair<URI, N3Patch>>()
        var updateCalled = false

        val rm = FakeSolidResourceManager().apply {
            onRead = { uri ->
                when (uri.toString()) {
                    webId, profileDoc -> SolidResult.Success(WebId(uri, profileQuads))
                    else -> SolidResult.Success(PrivateTypeIndex(uri, "application/ld+json", null, null))
                }
            }
            onHead = { SolidResult.Success(SolidMetadata.EMPTY) }
            onCreate = { SolidResult.Success(it) }
            onPatch = { uri, patch -> patched += uri to patch; SolidResult.Success(Unit) }
            onUpdate = { updateCalled = true; SolidResult.Success(it) }
        }

        runBlocking { TypeIndexResolver.getPrivateTypeIndex(rm, webId) }

        assertFalse("the profile must not be overwritten with a full-document PUT", updateCalled)
        assertEquals("the link is registered with exactly one PATCH", 1, patched.size)
        val (target, patch) = patched.single()
        assertEquals("the PATCH targets the profile document", URI.create(profileDoc), target)
        assertTrue(
            "the PATCH inserts the solid:privateTypeIndex triple",
            patch.toN3String().contains(Solid.PRIVATE_TYPE_INDEX),
        )
    }
}
