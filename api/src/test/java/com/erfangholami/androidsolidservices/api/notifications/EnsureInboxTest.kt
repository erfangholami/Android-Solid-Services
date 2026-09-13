package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxProvisioner
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationsManagerImplementation
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsureInboxTest {

    private val webId = "https://id.inrupt.com/bob"
    private val storage = "https://storage.inrupt.com/abc/"
    private val extended = "${storage}profile"
    private val inbox = "${storage}inbox/"

    @Test
    fun `an inbox advertised only in a private extended profile gets that profile made public`() {
        val webIdDoc = WebId(
            webId,
            listOf(
                RdfQuad(webId, FOAF.IS_PRIMARY_TOPIC_OF, extended),
                RdfQuad(webId, PIM.STORAGE, storage),
            ),
        )
        val extendedDoc = WebId(extended, listOf(RdfQuad(webId, LDP.INBOX, inbox)))
        val backend = RecordingAccessBackend()
        val rm = FakeSolidResourceManager(
            onRead = { uri ->
                when (uri) {
                    webId -> SolidResult.Success(webIdDoc)
                    extended -> SolidResult.Success(extendedDoc)
                    else -> SolidResult.Failure(SolidError.fromHttp(404, "x"))
                }
            },
            onHeadPublic = { uri ->
                if (uri == extended) {
                    SolidResult.Failure(SolidError.fromHttp(401, "private"))
                } else {
                    SolidResult.Success(SolidMetadata.EMPTY)
                }
            },
        )
        val manager = NotificationsManagerImplementation(
            rm,
            SolidShareNotificationProfile,
            InboxProvisioner(rm) { _, _ -> backend },
        )

        val result = runBlocking { manager.ensureInbox(webId) }

        assertEquals(inbox, result.getOrThrow())
        assertTrue(
            "the inbox keeps its public append",
            RecordedGrant(inbox, ShareMode.APPEND, ShareReceiver.Public, true, false) in backend.grants,
        )
        assertTrue(
            "the advertising document becomes public-readable",
            RecordedGrant(extended, ShareMode.READ, ShareReceiver.Public, false, false) in backend.grants,
        )
    }

    @Test
    fun `an inbox in the WebID document itself triggers no profile grant`() {
        val webIdDoc = WebId(
            webId,
            listOf(RdfQuad(webId, LDP.INBOX, inbox), RdfQuad(webId, PIM.STORAGE, storage)),
        )
        val backend = RecordingAccessBackend()
        val rm = FakeSolidResourceManager(
            onRead = { SolidResult.Success(webIdDoc) },
            onHeadPublic = { SolidResult.Failure(SolidError.fromHttp(401, "private")) },
        )
        val manager = NotificationsManagerImplementation(
            rm,
            SolidShareNotificationProfile,
            InboxProvisioner(rm) { _, _ -> backend },
        )

        runBlocking { manager.ensureInbox(webId) }

        assertEquals(
            listOf(RecordedGrant(inbox, ShareMode.APPEND, ShareReceiver.Public, true, false)),
            backend.grants,
        )
    }
}
