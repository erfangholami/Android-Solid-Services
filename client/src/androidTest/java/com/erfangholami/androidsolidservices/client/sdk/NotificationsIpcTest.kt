package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives every [SolidNotificationsClient] operation across a real binder.
 *
 * Almost every method here takes two WebIDs in a row, and the order is *not* consistent between
 * them: `sendOffer` is (owner, receiver) while `sendRequest` is (requester, owner). Get one
 * backwards and the notification is delivered to the wrong inbox announcing the wrong party — with
 * no error anywhere. These tests pin each ordering individually.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val notifications: SolidNotificationsClient
        get() = SolidNotificationsClient.getInstance(sdk.context)

    @Test
    fun listNotifications_returns_the_inbox(): Unit = runBlocking {
        assertEquals(
            listOf(Fixtures.SHARE_NOTIFICATION),
            notifications.listNotifications(Fixtures.WEB_ID),
        )
        sdk.recorded("listNotifications").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun listRequests_returns_the_incoming_requests(): Unit = runBlocking {
        assertEquals(listOf(Fixtures.SHARE_REQUEST), notifications.listRequests(Fixtures.WEB_ID))
        sdk.recorded("listRequests").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun sendOffer_sends_owner_then_receiver(): Unit = runBlocking {
        notifications.sendOffer(
            ownerWebId = Fixtures.WEB_ID,
            receiverWebId = Fixtures.PEER_WEB_ID,
            resourceUri = Fixtures.RESOURCE,
            mode = ShareMode.READ,
        )

        sdk.recorded("sendOffer").assertArgs(
            "ownerWebId" to Fixtures.WEB_ID,
            "receiverWebId" to Fixtures.PEER_WEB_ID,
            "resourceUri" to Fixtures.RESOURCE,
            "mode" to ShareMode.READ.ordinal,
        )
    }

    @Test
    fun sendUndo_sends_owner_then_receiver(): Unit = runBlocking {
        notifications.sendUndo(Fixtures.WEB_ID, Fixtures.PEER_WEB_ID, Fixtures.RESOURCE)

        sdk.recorded("sendUndo").assertArgs(
            "ownerWebId" to Fixtures.WEB_ID,
            "receiverWebId" to Fixtures.PEER_WEB_ID,
            "resourceUri" to Fixtures.RESOURCE,
        )
    }

    @Test
    fun sendRequest_sends_requester_first_unlike_the_offer_methods(): Unit = runBlocking {
        notifications.sendRequest(
            requesterWebId = Fixtures.WEB_ID,
            ownerWebId = Fixtures.PEER_WEB_ID,
            resourceUri = Fixtures.RESOURCE,
            requestedMode = ShareMode.APPEND,
            summary = "may I contribute?",
        )

        sdk.recorded("sendRequest").assertArgs(
            "requesterWebId" to Fixtures.WEB_ID,
            "ownerWebId" to Fixtures.PEER_WEB_ID,
            "requestedMode" to ShareMode.APPEND.ordinal,
            "summary" to "may I contribute?",
        )
    }

    @Test
    fun sendRequest_leaves_an_absent_summary_null(): Unit = runBlocking {
        notifications.sendRequest(
            Fixtures.WEB_ID,
            Fixtures.PEER_WEB_ID,
            Fixtures.RESOURCE,
            ShareMode.READ,
        )
        sdk.recorded("sendRequest").assertArgs("summary" to null)
    }

    @Test
    fun sendReject_sends_owner_then_requester_with_an_optional_reason(): Unit = runBlocking {
        notifications.sendReject(
            ownerWebId = Fixtures.WEB_ID,
            requesterWebId = Fixtures.PEER_WEB_ID,
            resourceUri = Fixtures.RESOURCE,
            reason = "not right now",
        )

        sdk.recorded("sendReject").assertArgs(
            "ownerWebId" to Fixtures.WEB_ID,
            "requesterWebId" to Fixtures.PEER_WEB_ID,
            "reason" to "not right now",
        )

        notifications.sendReject(Fixtures.WEB_ID, Fixtures.PEER_WEB_ID, Fixtures.RESOURCE)
        sdk.recorded("sendReject").assertArgs("reason" to null)
    }

    @Test
    fun sendUpdate_sends_owner_then_receiver_with_the_new_mode(): Unit = runBlocking {
        notifications.sendUpdate(
            Fixtures.WEB_ID,
            Fixtures.PEER_WEB_ID,
            Fixtures.RESOURCE,
            ShareMode.WRITE,
        )

        sdk.recorded("sendUpdate").assertArgs(
            "ownerWebId" to Fixtures.WEB_ID,
            "receiverWebId" to Fixtures.PEER_WEB_ID,
            "mode" to ShareMode.WRITE.ordinal,
        )
    }

    @Test
    fun sendAccept_carries_the_originating_request_uri(): Unit = runBlocking {
        notifications.sendAccept(
            ownerWebId = Fixtures.WEB_ID,
            requesterWebId = Fixtures.PEER_WEB_ID,
            resourceUri = Fixtures.RESOURCE,
            mode = ShareMode.READ,
            requestUri = Fixtures.REQUEST_URI,
        )

        sdk.recorded("sendAccept").assertArgs(
            "ownerWebId" to Fixtures.WEB_ID,
            "requesterWebId" to Fixtures.PEER_WEB_ID,
            "mode" to ShareMode.READ.ordinal,
            "requestUri" to Fixtures.REQUEST_URI,
        )

        notifications.sendAccept(
            Fixtures.WEB_ID,
            Fixtures.PEER_WEB_ID,
            Fixtures.RESOURCE,
            ShareMode.READ,
        )
        sdk.recorded("sendAccept").assertArgs("requestUri" to null)
    }

    @Test
    fun recordDecisionGranted_writes_to_the_owners_own_history(): Unit = runBlocking {
        notifications.recordDecisionGranted(
            ownerWebId = Fixtures.WEB_ID,
            requesterWebId = Fixtures.PEER_WEB_ID,
            resourceUri = Fixtures.RESOURCE,
            mode = ShareMode.APPEND,
            requestUri = Fixtures.REQUEST_URI,
        )

        sdk.recorded("recordDecisionGranted").assertArgs(
            "ownerWebId" to Fixtures.WEB_ID,
            "requesterWebId" to Fixtures.PEER_WEB_ID,
            "mode" to ShareMode.APPEND.ordinal,
            "requestUri" to Fixtures.REQUEST_URI,
        )
    }

    @Test
    fun recordDecisionRejected_sends_minus_one_when_no_mode_applies(): Unit = runBlocking {
        notifications.recordDecisionRejected(
            ownerWebId = Fixtures.WEB_ID,
            requesterWebId = Fixtures.PEER_WEB_ID,
            resourceUri = Fixtures.RESOURCE,
            reason = "declined",
        )

        sdk.recorded("recordDecisionRejected").assertArgs(
            "mode" to -1,
            "reason" to "declined",
        )
    }

    @Test
    fun recordDecisionRejected_still_sends_a_mode_when_one_is_given(): Unit = runBlocking {
        notifications.recordDecisionRejected(
            Fixtures.WEB_ID,
            Fixtures.PEER_WEB_ID,
            Fixtures.RESOURCE,
            ShareMode.WRITE,
        )
        sdk.recorded("recordDecisionRejected").assertArgs(
            "mode" to ShareMode.WRITE.ordinal,
            "reason" to null,
        )
    }

    @Test
    fun compactInbox_carries_an_optional_cutoff(): Unit = runBlocking {
        notifications.compactInbox(Fixtures.WEB_ID, "2026-01-01T00:00:00Z")
        sdk.recorded("compactInbox").assertArgs("olderThanIso" to "2026-01-01T00:00:00Z")

        notifications.compactInbox(Fixtures.WEB_ID)
        sdk.recorded("compactInbox").assertArgs("olderThanIso" to null)
    }

    @Test
    fun ensureInbox_returns_the_inbox_uri(): Unit = runBlocking {
        assertEquals(Fixtures.INBOX, notifications.ensureInbox(Fixtures.WEB_ID))
        sdk.recorded("ensureInbox").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun deleteNotification_returns_the_boolean_outcome(): Unit = runBlocking {
        assertTrue(notifications.deleteNotification(Fixtures.WEB_ID, Fixtures.NOTIFICATION))
        sdk.recorded("deleteNotification").assertArgs("notificationUri" to Fixtures.NOTIFICATION)
    }

    @Test
    fun a_service_error_arrives_as_the_mapped_sharing_exception(): Unit = runBlocking {
        val thrown = runCatching { notifications.ensureInbox(Fixtures.FAILING_WEB_ID) }
            .exceptionOrNull()

        assertTrue(
            "expected NoInboxException, got $thrown",
            thrown is SolidException.SolidSharingException.NoInboxException,
        )
    }
}
