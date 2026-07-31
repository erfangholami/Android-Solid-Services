package com.erfangholami.androidsolidservices.client.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.FakeSdk
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.client.internal.fakes.assertArgs
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives every [SolidSharingClient] operation across a real binder.
 *
 * This client flattens two sealed/enum types onto the wire as bare integers, and the integers
 * decide who gets what access. A mode that arrives as 2 instead of 0 grants Write where the caller
 * asked for Read — a security-relevant defect that returns a perfectly valid `GivenShare`. The
 * recorded arguments are the only place that shows up.
 */
@RunWith(AndroidJUnit4::class)
class SharingIpcTest {

    @get:Rule
    val sdk = FakeSdk()

    private val sharing: SolidSharingClient
        get() = SolidSharingClient.getInstance(sdk.context)

    private val peer = ShareReceiver.WebIdReceiver(Fixtures.PEER_WEB_ID)

    // region given shares

    @Test
    fun getStoredGivenShares_returns_the_index(): Unit = runBlocking {
        assertEquals(listOf(Fixtures.GIVEN_SHARE), sharing.getStoredGivenShares(Fixtures.WEB_ID))
        sdk.recorded("getStoredGivenShares").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun refreshGivenShares_reaches_the_verifying_method_not_the_stored_one(): Unit = runBlocking {
        sharing.refreshGivenShares(Fixtures.WEB_ID)
        sdk.recorded("refreshGivenShares").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun getGivenSharesForResource_sends_the_resource(): Unit = runBlocking {
        sharing.getGivenSharesForResource(Fixtures.WEB_ID, Fixtures.RESOURCE)
        sdk.recorded("getGivenSharesForResource").assertArgs("resourceUri" to Fixtures.RESOURCE)
    }

    @Test
    fun createShare_sends_each_mode_as_its_own_ordinal(): Unit = runBlocking {
        ShareMode.entries.forEach { mode ->
            sharing.createShare(Fixtures.WEB_ID, Fixtures.RESOURCE, mode, peer)
            sdk.recorded("createShare").assertArgs("mode" to mode.ordinal)
        }
    }

    @Test
    fun createShare_flattens_each_receiver_kind_correctly(): Unit = runBlocking {
        val receivers = listOf(
            peer,
            ShareReceiver.GroupReceiver(Fixtures.GROUP),
            ShareReceiver.Public,
        )

        receivers.forEach { receiver ->
            sharing.createShare(Fixtures.WEB_ID, Fixtures.RESOURCE, ShareMode.READ, receiver)
            sdk.recorded("createShare").assertArgs(
                "receiverKind" to receiver.kind(),
                "receiverValue" to receiver.value(),
            )
        }
    }

    @Test
    fun createShare_notifies_by_default_and_honours_an_opt_out(): Unit = runBlocking {
        sharing.createShare(Fixtures.WEB_ID, Fixtures.RESOURCE, ShareMode.READ, peer)
        sdk.recorded("createShare").assertArgs("notifyReceiver" to true)

        sharing.createShare(
            Fixtures.WEB_ID,
            Fixtures.RESOURCE,
            ShareMode.READ,
            peer,
            notifyReceiver = false,
        )
        sdk.recorded("createShare").assertArgs("notifyReceiver" to false)
    }

    @Test
    fun createShare_returns_the_created_share(): Unit = runBlocking {
        val share = sharing.createShare(Fixtures.WEB_ID, Fixtures.RESOURCE, ShareMode.APPEND, peer)
        assertEquals(Fixtures.GIVEN_SHARE, share)
    }

    @Test
    fun updateShare_sends_mode_and_receiver_in_the_same_shape_as_create(): Unit = runBlocking {
        sharing.updateShare(Fixtures.WEB_ID, Fixtures.RESOURCE, ShareMode.WRITE, peer)

        sdk.recorded("updateShare").assertArgs(
            "resourceUri" to Fixtures.RESOURCE,
            "mode" to ShareMode.WRITE.ordinal,
            "receiverKind" to ShareReceiver.KIND_WEBID,
            "receiverValue" to Fixtures.PEER_WEB_ID,
        )
    }

    @Test
    fun revokeShare_sends_the_receiver_without_a_mode(): Unit = runBlocking {
        sharing.revokeShare(Fixtures.WEB_ID, Fixtures.RESOURCE, ShareReceiver.Public)

        sdk.recorded("revokeShare").assertArgs(
            "resourceUri" to Fixtures.RESOURCE,
            "receiverKind" to ShareReceiver.KIND_PUBLIC,
            "receiverValue" to null,
        )
    }

    @Test
    fun rebuildGivenIndex_reaches_its_own_method(): Unit = runBlocking {
        sharing.rebuildGivenIndex(Fixtures.WEB_ID)
        sdk.recorded("rebuildGivenIndex").assertArgs("webId" to Fixtures.WEB_ID)
    }

    // endregion

    // region received shares

    @Test
    fun getStoredReceivedShares_returns_the_index(): Unit = runBlocking {
        assertEquals(
            listOf(Fixtures.RECEIVED_SHARE),
            sharing.getStoredReceivedShares(Fixtures.WEB_ID),
        )
    }

    @Test
    fun refreshReceivedShares_reaches_the_verifying_method(): Unit = runBlocking {
        sharing.refreshReceivedShares(Fixtures.WEB_ID)
        sdk.recorded("refreshReceivedShares").assertArgs("webId" to Fixtures.WEB_ID)
    }

    @Test
    fun addReceivedShare_returns_the_tracked_share(): Unit = runBlocking {
        val share = sharing.addReceivedShare(Fixtures.WEB_ID, Fixtures.RESOURCE)

        assertEquals(Fixtures.RECEIVED_SHARE, share)
        sdk.recorded("addReceivedShare").assertArgs("resourceUri" to Fixtures.RESOURCE)
    }

    @Test
    fun removeReceivedShare_sends_resource_then_owner(): Unit = runBlocking {
        // Two IRIs and a WebID in a row; only the record tells them apart.
        sharing.removeReceivedShare(Fixtures.WEB_ID, Fixtures.RESOURCE, Fixtures.PEER_WEB_ID)

        sdk.recorded("removeReceivedShare").assertArgs(
            "webId" to Fixtures.WEB_ID,
            "resourceUri" to Fixtures.RESOURCE,
            "ownerWebId" to Fixtures.PEER_WEB_ID,
        )
    }

    @Test
    fun syncReceivedShares_carries_the_notification_list(): Unit = runBlocking {
        sharing.syncReceivedShares(Fixtures.WEB_ID, listOf(Fixtures.SHARE_NOTIFICATION))

        sdk.recorded("syncReceivedShares").assertArgs(
            "notifications" to listOf(Fixtures.SHARE_NOTIFICATION),
        )
    }

    // endregion

    // region requests, catalog and repairs

    @Test
    fun getAccessGrants_returns_the_unified_list(): Unit = runBlocking {
        assertEquals(listOf(Fixtures.ACCESS_GRANT), sharing.getAccessGrants(Fixtures.WEB_ID))
    }

    @Test
    fun acceptShareRequest_sends_the_whole_request_parcelled(): Unit = runBlocking {
        val share = sharing.acceptShareRequest(Fixtures.WEB_ID, Fixtures.SHARE_REQUEST)

        assertEquals(Fixtures.GIVEN_SHARE, share)
        sdk.recorded("acceptShareRequest").assertArgs("request" to Fixtures.SHARE_REQUEST)
    }

    @Test
    fun rejectShareRequest_carries_an_optional_reason(): Unit = runBlocking {
        sharing.rejectShareRequest(Fixtures.WEB_ID, Fixtures.SHARE_REQUEST, "not this time")
        sdk.recorded("rejectShareRequest").assertArgs(
            "request" to Fixtures.SHARE_REQUEST,
            "reason" to "not this time",
        )

        sharing.rejectShareRequest(Fixtures.WEB_ID, Fixtures.SHARE_REQUEST)
        sdk.recorded("rejectShareRequest").assertArgs("reason" to null)
    }

    @Test
    fun publishCatalogEntry_sends_the_entry_parcelled(): Unit = runBlocking {
        sharing.publishCatalogEntry(Fixtures.WEB_ID, Fixtures.CATALOG_ENTRY)
        sdk.recorded("publishCatalogEntry").assertArgs("entry" to Fixtures.CATALOG_ENTRY)
    }

    @Test
    fun removeCatalogEntry_sends_the_resource(): Unit = runBlocking {
        sharing.removeCatalogEntry(Fixtures.WEB_ID, Fixtures.RESOURCE)
        sdk.recorded("removeCatalogEntry").assertArgs("resourceUri" to Fixtures.RESOURCE)
    }

    @Test
    fun getOwnerCatalog_sends_viewer_then_owner(): Unit = runBlocking {
        // Both parameters are WebIDs, and reversing them reads someone else's catalog as yourself.
        val entries = sharing.getOwnerCatalog(Fixtures.WEB_ID, Fixtures.PEER_WEB_ID)

        assertEquals(listOf(Fixtures.CATALOG_ENTRY), entries)
        sdk.recorded("getOwnerCatalog").assertArgs(
            "viewerWebId" to Fixtures.WEB_ID,
            "ownerWebId" to Fixtures.PEER_WEB_ID,
        )
    }

    @Test
    fun makePrivate_and_repairOwnerControl_send_the_resource(): Unit = runBlocking {
        sharing.makePrivate(Fixtures.WEB_ID, Fixtures.RESOURCE)
        sdk.recorded("makePrivate").assertArgs("resourceUri" to Fixtures.RESOURCE)

        sharing.repairOwnerControl(Fixtures.WEB_ID, Fixtures.RESOURCE)
        sdk.recorded("repairOwnerControl").assertArgs("resourceUri" to Fixtures.RESOURCE)
    }

    // endregion

    @Test
    fun a_service_error_arrives_as_the_mapped_sharing_exception(): Unit = runBlocking {
        val thrown = runCatching { sharing.getStoredGivenShares(Fixtures.FAILING_WEB_ID) }
            .exceptionOrNull()

        assertTrue(
            "expected AccessDeniedException, got $thrown",
            thrown is SolidException.SolidSharingException.AccessDeniedException,
        )
    }
}
