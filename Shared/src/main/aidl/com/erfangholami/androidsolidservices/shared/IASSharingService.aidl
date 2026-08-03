package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback;
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry;
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest;
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification;

/**
 * AIDL IPC contract for Solid resource sharing. Manages the full sharing lifecycle across
 * processes: creating and revoking ACL-based shares, tracking given and received shares,
 * handling incoming share requests, and maintaining the owner's public catalog. Results are delivered on the two generic callbacks, IASSParcelableCallback and
 * IASSParcelableListCallback, whose Bundle envelope is described by
 * `shared/ipc/IpcEnvelope.kt`. Third-party apps normally use the higher-level client SDK
 * rather than binding here directly.
 */
interface IASSharingService {

    void getStoredGivenShares(String webId, IASSParcelableListCallback callback);

    void refreshGivenShares(String webId, IASSParcelableListCallback callback);

    void getGivenSharesForResource(
        String webId,
        String resourceUri,
        IASSParcelableListCallback callback
    );

    /** mode: the ShareMode ordinal (Read=0, Append=1, Write=2). receiverKind: ShareReceiver.KIND_* — WebID=0, group=1, public=2. receiverValue: the WebID or group URI when receiverKind targets a specific identity; null for public. Decode both with ShareMode.fromOrdinal / ShareReceiver.fromKindOrNull: these arrive from an exported service and may name values this build does not know. resourceType/resourceName mark a typed (entity) share — the entity's RDF class IRI and human title; null for plain resource shares. */
    void createShare(
        String webId,
        String resourceUri,
        int mode,
        int receiverKind,
        @nullable String receiverValue,
        boolean notifyReceiver,
        @nullable String resourceType,
        @nullable String resourceName,
        IASSParcelableCallback callback
    );

    void updateShare(
        String webId,
        String resourceUri,
        int mode,
        int receiverKind,
        @nullable String receiverValue,
        @nullable String resourceType,
        @nullable String resourceName,
        IASSParcelableCallback callback
    );

    void revokeShare(
        String webId,
        String resourceUri,
        int receiverKind,
        @nullable String receiverValue,
        IASSParcelableCallback callback
    );

    /** Drops the index rows of a deleted resource (and, with includeDescendants, everything under it) without touching access control. Returns the removed rows. */
    void purgeGivenShares(
        String webId,
        String resourceUri,
        boolean includeDescendants,
        boolean notifyReceivers,
        IASSParcelableListCallback callback
    );

    void getStoredReceivedShares(String webId, IASSParcelableListCallback callback);

    void refreshReceivedShares(String webId, IASSParcelableListCallback callback);

    void addReceivedShare(
        String webId,
        String resourceUri,
        @nullable String resourceType,
        @nullable String resourceName,
        IASSParcelableCallback callback
    );

    void removeReceivedShare(
        String webId,
        String resourceUri,
        String ownerWebId,
        IASSParcelableCallback callback
    );

    void getAccessGrants(
        String webId,
        IASSParcelableListCallback callback
    );

    void acceptShareRequest(
        String webId,
        in ShareRequest request,
        IASSParcelableCallback callback
    );

    void rejectShareRequest(
        String webId,
        in ShareRequest request,
        @nullable String reason,
        IASSParcelableCallback callback
    );

    /** Rebuilds the given-shares index by scanning pod ACLs; use when the index may be out of sync. */
    void rebuildGivenIndex(String webId, IASSParcelableListCallback callback);

    void publishCatalogEntry(
        String webId,
        in CatalogEntry entry,
        IASSParcelableCallback callback
    );

    void removeCatalogEntry(
        String webId,
        String resourceUri,
        IASSParcelableCallback callback
    );

    void getOwnerCatalog(
        String viewerWebId,
        String ownerWebId,
        IASSParcelableListCallback callback
    );

    /** Strips every share from the resource, leaving it owner-only. */
    void makePrivate(String webId, String resourceUri, IASSParcelableCallback callback);

    /** Re-asserts the owner's acl:Control on a resource whose ACL lost it. */
    void repairOwnerControl(String webId, String resourceUri, IASSParcelableCallback callback);

    /**
     * Reconciles the received-shares index against inbox notifications: an Offer/Accept adds
     * a row, an Undo removes one. Returns the reconciled index.
     */
    void syncReceivedShares(
        String webId,
        in List<ShareNotification> notifications,
        IASSParcelableListCallback callback
    );
}
