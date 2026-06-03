package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.IASSUnitCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry;
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare;
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare;
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest;
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSAccessGrantListCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSCatalogEntryListCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSGivenShareCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSReceivedShareCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSGivenShareListCallback;
import com.erfangholami.androidsolidservices.shared.model.sharing.IASSReceivedShareListCallback;

/**
 * AIDL IPC contract for Solid resource sharing. Manages the full sharing lifecycle across
 * processes: creating and revoking ACL-based shares, tracking given and received shares,
 * handling incoming share requests, and maintaining the owner's public catalog. Results are
 * delivered via one-way callbacks. Third-party apps normally use the higher-level client SDK
 * rather than binding here directly.
 */
interface IASSharingService {

    void getStoredGivenShares(String webId, IASSGivenShareListCallback callback);

    void refreshGivenShares(String webId, IASSGivenShareListCallback callback);

    void getGivenSharesForResource(
        String webId,
        String resourceUri,
        IASSGivenShareListCallback callback
    );

    /** mode: WAC access mode (e.g. Read=1). receiverKind: who receives access (e.g. specific WebID, public). receiverValue: the WebID or group URI when receiverKind targets a specific identity; null for public. */
    void createShare(
        String webId,
        String resourceUri,
        int mode,
        int receiverKind,
        @nullable String receiverValue,
        boolean notifyReceiver,
        IASSGivenShareCallback callback
    );

    void updateShare(
        String webId,
        String resourceUri,
        int mode,
        int receiverKind,
        @nullable String receiverValue,
        IASSGivenShareCallback callback
    );

    void revokeShare(
        String webId,
        String resourceUri,
        int receiverKind,
        @nullable String receiverValue,
        IASSUnitCallback callback
    );

    void getStoredReceivedShares(String webId, IASSReceivedShareListCallback callback);

    void refreshReceivedShares(String webId, IASSReceivedShareListCallback callback);

    void addReceivedShare(
        String webId,
        String resourceUri,
        IASSReceivedShareCallback callback
    );

    void removeReceivedShare(
        String webId,
        String resourceUri,
        String ownerWebId,
        IASSUnitCallback callback
    );

    void getAccessGrants(
        String webId,
        IASSAccessGrantListCallback callback
    );

    void acceptShareRequest(
        String webId,
        in ShareRequest request,
        IASSGivenShareCallback callback
    );

    void rejectShareRequest(
        String webId,
        in ShareRequest request,
        @nullable String reason,
        IASSUnitCallback callback
    );

    /** Rebuilds the given-shares index by scanning pod ACLs; use when the index may be out of sync. */
    void rebuildGivenIndex(String webId, IASSGivenShareListCallback callback);

    void publishCatalogEntry(
        String webId,
        in CatalogEntry entry,
        IASSUnitCallback callback
    );

    void removeCatalogEntry(
        String webId,
        String resourceUri,
        IASSUnitCallback callback
    );

    void getOwnerCatalog(
        String viewerWebId,
        String ownerWebId,
        IASSCatalogEntryListCallback callback
    );
}
