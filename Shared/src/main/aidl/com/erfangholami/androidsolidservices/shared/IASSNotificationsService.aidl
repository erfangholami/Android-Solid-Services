package com.erfangholami.androidsolidservices.shared;

import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback;
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback;

/**
 * AIDL IPC contract for Solid Linked Data Notifications. Provides cross-process access to
 * inbox notifications (share offers, undos) and share requests, and allows sending LDN
 * messages (offer, undo, request, reject) to remote Solid inboxes. Results are delivered on the two generic callbacks, IASSParcelableCallback and
 * IASSParcelableListCallback, whose Bundle envelope is described by
 * `shared/ipc/IpcEnvelope.kt`. Third-party apps normally use the higher-level client SDK rather
 * than binding here directly.
 */
interface IASSNotificationsService {

    void listNotifications(
        String webId,
        IASSParcelableListCallback callback
    );

    void listRequests(
        String webId,
        IASSParcelableListCallback callback
    );

    /** resourceType/resourceName describe the object of a typed (entity) share — the entity's RDF class IRI and human title; null for plain resource shares. */
    void sendOffer(
        String ownerWebId,
        String receiverWebId,
        String resourceUri,
        int mode,
        @nullable String resourceType,
        @nullable String resourceName,
        IASSParcelableCallback callback
    );

    void sendUndo(
        String ownerWebId,
        String receiverWebId,
        String resourceUri,
        IASSParcelableCallback callback
    );

    void sendRequest(
        String requesterWebId,
        String ownerWebId,
        String resourceUri,
        int requestedMode,
        @nullable String summary,
        IASSParcelableCallback callback
    );

    void sendReject(
        String ownerWebId,
        String requesterWebId,
        String resourceUri,
        @nullable String reason,
        IASSParcelableCallback callback
    );

    /** Deletes inbox messages older than the given ISO-8601 timestamp; pass null to compact all read messages. */
    void compactInbox(
        String webId,
        @nullable String olderThanIso,
        IASSParcelableCallback callback
    );

    /** Ensures the user has an LDN inbox, creating and advertising it if absent. Returns its URI. */
    void ensureInbox(String webId, IASSParcelableCallback callback);

    /** Deletes a single message from the inbox. */
    void deleteNotification(String webId, String notificationUri, IASSParcelableCallback callback);

    /** Tells a receiver their access level changed. This is an as:Update, never a re-Offer. */
    void sendUpdate(
        String ownerWebId,
        String receiverWebId,
        String resourceUri,
        int mode,
        @nullable String resourceType,
        @nullable String resourceName,
        IASSParcelableCallback callback
    );

    /** Tells a requester that their access request was granted. */
    void sendAccept(
        String ownerWebId,
        String requesterWebId,
        String resourceUri,
        int mode,
        @nullable String requestUri,
        IASSParcelableCallback callback
    );

    /**
     * Leaves a read-only record in the owner's OWN inbox that they granted a request — the
     * "you approved sharing with X" row.
     */
    void recordDecisionGranted(
        String ownerWebId,
        String requesterWebId,
        String resourceUri,
        int mode,
        @nullable String requestUri,
        IASSParcelableCallback callback
    );

    /**
     * Leaves a read-only record in the owner's OWN inbox that they declined a request.
     * Pass mode = -1 when no mode applies.
     */
    void recordDecisionRejected(
        String ownerWebId,
        String requesterWebId,
        String resourceUri,
        int mode,
        @nullable String reason,
        IASSParcelableCallback callback
    );
}
