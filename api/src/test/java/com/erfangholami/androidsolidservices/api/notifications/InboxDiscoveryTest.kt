package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI

/**
 * Tests inbox discovery for a foreign actor. The key behaviour: the inbox is used
 * only where the target actually advertises it (ldp:inbox triple or Link header) —
 * a `{storage}inbox/` URL is never fabricated, because posting a notification to a
 * container the target never declared delivers it into the void.
 */
class InboxDiscoveryTest {

    private val target = "https://carol.pod/profile/card#me"
    private val asWebId = "https://alice.pod/profile/card#me"

    private fun profile(vararg quads: RdfQuad): WebId =
        WebId(URI.create(target), quads.toList())

    @Test
    fun `resolveInboxOf returns null when the target advertises no inbox`() {
        // Storage is declared, but no inbox is — the old code fabricated {storage}inbox/.
        val profile = profile(RdfQuad(target, PIM.STORAGE, "https://carol.pod/", null, null))
        val rm = FakeSolidResourceManager(
            onReadPublic = { SolidResult.Success(profile) },
            onRead = { SolidResult.Success(profile) },
            onHeadPublic = { SolidResult.Success(SolidMetadata.EMPTY) },
        )

        val inbox = runBlocking { InboxDiscovery(rm).resolveInboxOf(target, asWebId) }

        assertNull("no {storage}inbox/ fabrication when the target declares no inbox", inbox)
    }

    @Test
    fun `resolveInboxOf returns the advertised ldp inbox`() {
        val profile = profile(
            RdfQuad(target, LDP.INBOX, "https://carol.pod/inbox/", null, null),
            RdfQuad(target, PIM.STORAGE, "https://carol.pod/", null, null),
        )
        val rm = FakeSolidResourceManager(onReadPublic = { SolidResult.Success(profile) })

        val inbox = runBlocking { InboxDiscovery(rm).resolveInboxOf(target, asWebId) }

        assertEquals(URI.create("https://carol.pod/inbox/"), inbox)
    }
}
