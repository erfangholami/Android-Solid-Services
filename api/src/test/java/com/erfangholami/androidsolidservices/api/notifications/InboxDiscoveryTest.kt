package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxDiscovery
import com.erfangholami.androidsolidservices.api.notifications.implementation.OwnInbox
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxDiscoveryTest {

    private val target = "https://carol.pod/profile/card#me"
    private val asWebId = "https://alice.pod/profile/card#me"

    private fun profile(vararg quads: RdfQuad): WebId =
        WebId(target, quads.toList())

    @Test
    fun `resolveInboxOf returns null when the target advertises no inbox`() {
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

        assertEquals("https://carol.pod/inbox/", inbox)
    }

    @Test
    fun `guessInboxOf derives the conventional inbox from the public storage`() {
        val profile = profile(RdfQuad(target, PIM.STORAGE, "https://carol.pod", null, null))
        val rm = FakeSolidResourceManager(onReadPublic = { SolidResult.Success(profile) })

        val guess = runBlocking { InboxDiscovery(rm).guessInboxOf(target) }

        assertEquals("https://carol.pod/inbox/", guess)
    }

    @Test
    fun `guessInboxOf is null without a public storage`() {
        val rm = FakeSolidResourceManager(onReadPublic = { SolidResult.Success(profile()) })

        assertNull(runBlocking { InboxDiscovery(rm).guessInboxOf(target) })
    }

    @Test
    fun `resolveOwnInboxDetailed names the extended document that advertises the inbox`() {
        val extended = "https://carol.pod/profile/extended"
        val primary = profile(RdfQuad(target, FOAF.IS_PRIMARY_TOPIC_OF, extended, null, null))
        val extendedDoc = WebId(
            extended,
            listOf(RdfQuad(target, LDP.INBOX, "https://carol.pod/inbox/", null, null)),
        )
        val rm = FakeSolidResourceManager(onRead = { uri ->
            when (uri) {
                target -> SolidResult.Success(primary)
                extended -> SolidResult.Success(extendedDoc)
                else -> SolidResult.Failure(SolidError.fromHttp(404, "x"))
            }
        })

        val own = runBlocking { InboxDiscovery(rm).resolveOwnInboxDetailed(target) }

        assertEquals(OwnInbox("https://carol.pod/inbox/", advertisedIn = extended), own)
    }

    @Test
    fun `resolveOwnInboxDetailed reports no advertising document for an inbox in the WebID document`() {
        val primary = profile(RdfQuad(target, LDP.INBOX, "https://carol.pod/inbox/", null, null))
        val rm = FakeSolidResourceManager(onRead = { SolidResult.Success(primary) })

        val own = runBlocking { InboxDiscovery(rm).resolveOwnInboxDetailed(target) }

        assertEquals(OwnInbox("https://carol.pod/inbox/", advertisedIn = null), own)
    }
}
