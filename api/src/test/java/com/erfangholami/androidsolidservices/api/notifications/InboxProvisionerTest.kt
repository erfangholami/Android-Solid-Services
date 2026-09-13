package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.notifications.implementation.InboxProvisioner
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxProvisionerTest {

    private val webId = "https://id.inrupt.com/alice"
    private val extended = "https://storage.inrupt.com/abc/profile"
    private val inbox = "https://storage.inrupt.com/abc/inbox/"

    @Test
    fun `ensurePublicRead grants public read on a document nobody else can read`() {
        val backend = RecordingAccessBackend()
        val rm = FakeSolidResourceManager(
            onHeadPublic = { SolidResult.Failure(SolidError.fromHttp(401, "private")) },
        )

        val granted = runBlocking {
            InboxProvisioner(rm) { _, _ -> backend }.ensurePublicRead(webId, extended)
        }

        assertTrue(granted)
        assertEquals(
            listOf(RecordedGrant(extended, ShareMode.READ, ShareReceiver.Public, false, false)),
            backend.grants,
        )
    }

    @Test
    fun `ensurePublicRead leaves an already public document alone`() {
        val backend = RecordingAccessBackend()
        val rm = FakeSolidResourceManager(
            onHeadPublic = { SolidResult.Success(SolidMetadata.EMPTY) },
        )

        val granted = runBlocking {
            InboxProvisioner(rm) { _, _ -> backend }.ensurePublicRead(webId, extended)
        }

        assertFalse(granted)
        assertTrue(backend.grants.isEmpty())
    }

    @Test
    fun `grantPublicAppend grants append only, on the container`() {
        val backend = RecordingAccessBackend()
        val rm = FakeSolidResourceManager()

        runBlocking { InboxProvisioner(rm) { _, _ -> backend }.grantPublicAppend(webId, inbox) }

        assertEquals(
            listOf(RecordedGrant(inbox, ShareMode.APPEND, ShareReceiver.Public, true, false)),
            backend.grants,
        )
    }
}
