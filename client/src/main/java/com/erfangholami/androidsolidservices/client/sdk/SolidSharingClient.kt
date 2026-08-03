package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_SHARING_SERVICE
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.IASSharingService
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import kotlinx.coroutines.flow.Flow

/**
 * Client SDK for the Solid resource-sharing feature: create, list and revoke
 * shares of pod resources, track shares received from others, browse an
 * owner's catalog, and accept or reject access requests.
 *
 * Sharing is enforced by the pod's access control (Web Access Control, or
 * Access Control Policy on servers that use it). Calls are delegated over IPC
 * to the Android Solid Services app.
 *
 * Obtain an instance via [Solid.getSharingClient]. Collect [connectionState]
 * and wait for `true` before issuing calls. All operations are `suspend`
 * functions and throw [SolidException] on failure.
 */
public class SolidSharingClient private constructor(context: Context) {

    public companion object {
        @Volatile
        private var instance: SolidSharingClient? = null

        public fun getInstance(context: Context): SolidSharingClient =
            instance ?: synchronized(this) {
                instance ?: SolidSharingClient(context).also { instance = it }
            }

        /**
         * Drops the singleton and releases its binding, so the next [getInstance] builds a fresh
         * client. Exists only for instrumented tests; nothing in production calls it.
         */
        internal fun resetForTests() {
            synchronized(this) {
                instance?.connector?.unbind()
                instance = null
            }
        }
    }

    private val connector = ServiceConnector(
        context,
        ANDROID_SOLID_SERVICES_SHARING_SERVICE,
        IASSharingService.Stub::asInterface,
    )

    /** Hot [Flow] of the IPC service connection state; emits `true` once connected. */
    public fun connectionState(): Flow<Boolean> = connector.connectionState

    /** Returns the shares this user has given, read from the on-pod index (fast). Re-validate with [refreshGivenShares]. */
    public suspend fun getStoredGivenShares(webId: String): List<GivenShare> =
        givenList { service, cb -> service.getStoredGivenShares(webId, cb) }

    /** Re-validates each given share against the resource's live ACL, drops entries no longer granted, and returns the verified list. */
    public suspend fun refreshGivenShares(webId: String): List<GivenShare> =
        givenList { service, cb -> service.refreshGivenShares(webId, cb) }

    /** Returns the shares affecting [resourceUri], read directly from that resource's ACL (authoritative). */
    public suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): List<GivenShare> =
        givenList { service, cb -> service.getGivenSharesForResource(webId, resourceUri, cb) }

    /**
     * Grants [receiver] access of [mode] on [resourceUri] and records it in the
     * given-shares index. For a container, members inherit the access. When
     * [notifyReceiver] is true and [receiver] is a WebID, a best-effort
     * notification is delivered to their inbox. Returns the created share.
     *
     * [resourceType] / [resourceName] mark a typed (entity) share — the RDF class
     * IRI of the data-module entity the share carries and its human title; both
     * `null` for plain resource shares.
     */
    public suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean = true,
        resourceType: String? = null,
        resourceName: String? = null,
    ): GivenShare? = given { service, cb ->
        service.createShare(
            webId, resourceUri, mode.ordinal,
            receiver.kind(), receiver.value(),
            notifyReceiver, resourceType, resourceName, cb,
        )
    }

    /**
     * Changes the access mode of an existing share for [receiver] on [resourceUri].
     * [resourceType] / [resourceName] refresh the typed-share marks as on [createShare].
     */
    public suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        resourceType: String? = null,
        resourceName: String? = null,
    ): GivenShare? = given { service, cb ->
        service.updateShare(
            webId, resourceUri, mode.ordinal, receiver.kind(), receiver.value(),
            resourceType, resourceName, cb,
        )
    }

    /**
     * Removes [receiver]'s access to [resourceUri] and the matching index entry.
     * If [receiver] is a WebID, a best-effort withdrawal notification is sent.
     */
    public suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ): Unit = unit { service, cb ->
        service.revokeShare(webId, resourceUri, receiver.kind(), receiver.value(), cb)
    }

    /**
     * Drops the given-shares index rows of a deleted [resourceUri] — and, with
     * [includeDescendants], of everything beneath it — without touching access control.
     * Call it after deleting a resource so its bookkeeping does not outlive it. Returns the
     * removed rows; [notifyReceivers] additionally sends each WebID receiver an `as:Undo`.
     */
    public suspend fun purgeGivenShares(
        webId: String,
        resourceUri: String,
        includeDescendants: Boolean = true,
        notifyReceivers: Boolean = true,
    ): List<GivenShare> = givenList { service, cb ->
        service.purgeGivenShares(webId, resourceUri, includeDescendants, notifyReceivers, cb)
    }

    /** Returns the shares this user has received, from the local index (fast). Re-validate with [refreshReceivedShares]. */
    public suspend fun getStoredReceivedShares(webId: String): List<ReceivedShare> =
        receivedList { service, cb -> service.getStoredReceivedShares(webId, cb) }

    /** Re-validates each received share and drops entries that are no longer accessible. */
    public suspend fun refreshReceivedShares(webId: String): List<ReceivedShare> =
        receivedList { service, cb -> service.refreshReceivedShares(webId, cb) }

    /**
     * Starts tracking access to [resourceUri] that was shared with this user
     * (e.g. after scanning a QR code or opening a share link), verifying access
     * first. Returns the received share, or `null` if access can't be verified.
     *
     * [resourceType] / [resourceName] mark the stored row as a typed (entity)
     * share; both `null` records an untyped row.
     */
    public suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        resourceType: String? = null,
        resourceName: String? = null,
    ): ReceivedShare? = received { service, cb ->
        service.addReceivedShare(webId, resourceUri, resourceType, resourceName, cb)
    }

    /** Stops tracking a received share. Does not affect the resource itself. */
    public suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ): Unit = unit { service, cb ->
        service.removeReceivedShare(webId, resourceUri, ownerWebId, cb)
    }

    /**
     * Returns every observable access relationship for [webId] — shares given,
     * shares received, incoming requests, and any grants discovered through
     * Solid Application Interoperability (SAI) registries — unified into one list.
     */
    public suspend fun getAccessGrants(webId: String): List<AccessGrant> =
        accessGrantList { service, cb -> service.getAccessGrants(webId, cb) }

    /**
     * Approves an incoming share request. Equivalent to creating the
     * requested share — the requester gets an `as:Offer` informing them
     * of the grant.
     */
    public suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): GivenShare? = given { service, cb ->
        service.acceptShareRequest(webId, request, cb)
    }

    /**
     * Declines an incoming share request by posting an `as:Reject` to the
     * requester's inbox. Does not create any share.
     */
    public suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String? = null,
    ): Unit = unit { service, cb ->
        service.rejectShareRequest(webId, request, reason, cb)
    }

    /**
     * Rebuilds the given-shares index by walking the pod and reading each
     * resource's ACL. Expensive — expose as an explicit user action, not a
     * routine refresh. Returns the rebuilt list.
     */
    public suspend fun rebuildGivenIndex(webId: String): List<GivenShare> =
        givenList { service, cb -> service.rebuildGivenIndex(webId, cb) }

    /**
     * Adds or replaces [entry] in this user's public catalog of resources that
     * others may request access to. The catalog is public; the listed resources
     * stay private until access is granted.
     */
    public suspend fun publishCatalogEntry(
        webId: String,
        entry: CatalogEntry,
    ): Unit = unit { service, cb -> service.publishCatalogEntry(webId, entry, cb) }

    /** Removes the catalog entry for [resourceUri], if present. */
    public suspend fun removeCatalogEntry(
        webId: String,
        resourceUri: String,
    ): Unit = unit { service, cb -> service.removeCatalogEntry(webId, resourceUri, cb) }

    /**
     * Reads [ownerWebId]'s public catalog from [viewerWebId]'s perspective —
     * used to browse what an owner accepts access requests for.
     */
    public suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): List<CatalogEntry> =
        catalogList { service, cb -> service.getOwnerCatalog(viewerWebId, ownerWebId, cb) }

    /** Strips every share from [resourceUri], leaving it owner-only. */
    public suspend fun makePrivate(webId: String, resourceUri: String): Unit =
        unit { service, cb -> service.makePrivate(webId, resourceUri, cb) }

    /**
     * Re-asserts the owner's `acl:Control` on a resource whose ACL lost it — the repair for
     * a pod that dropped the owner rule and left the resource un-manageable.
     */
    public suspend fun repairOwnerControl(webId: String, resourceUri: String): Unit =
        unit { service, cb -> service.repairOwnerControl(webId, resourceUri, cb) }

    /**
     * Reconciles the received-shares index against inbox notifications: an Offer/Accept adds
     * a row, an Undo removes one. Returns the reconciled index.
     */
    public suspend fun syncReceivedShares(
        webId: String,
        notifications: List<ShareNotification>,
    ): List<ReceivedShare> = receivedList { service, cb ->
        service.syncReceivedShares(webId, notifications, cb)
    }

    private suspend fun givenList(
        call: (IASSharingService, IASSParcelableListCallback) -> Unit,
    ): List<GivenShare> = connector.suspendParcelableList(GivenShare::class.java, call)

    private suspend fun receivedList(
        call: (IASSharingService, IASSParcelableListCallback) -> Unit,
    ): List<ReceivedShare> = connector.suspendParcelableList(ReceivedShare::class.java, call)

    private suspend fun given(
        call: (IASSharingService, IASSParcelableCallback) -> Unit,
    ): GivenShare? = connector.suspendParcelable(GivenShare::class.java, call)

    private suspend fun received(
        call: (IASSharingService, IASSParcelableCallback) -> Unit,
    ): ReceivedShare? = connector.suspendParcelable(ReceivedShare::class.java, call)

    private suspend fun catalogList(
        call: (IASSharingService, IASSParcelableListCallback) -> Unit,
    ): List<CatalogEntry> = connector.suspendParcelableList(CatalogEntry::class.java, call)

    private suspend fun accessGrantList(
        call: (IASSharingService, IASSParcelableListCallback) -> Unit,
    ): List<AccessGrant> = connector.suspendParcelableList(AccessGrant::class.java, call)

    private suspend fun unit(
        call: (IASSharingService, IASSParcelableCallback) -> Unit,
    ): Unit = connector.suspendUnit(call)
}
