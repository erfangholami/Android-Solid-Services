package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.notifications.implementation.NotificationTransportImplementation
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.flow.Flow

/**
 * Pure Linked Data Notifications transport — the protocol layer for delivering
 * and reading notifications through a Solid LDN inbox, with no knowledge of what
 * any notification *means*.
 *
 * This is the generic foundation other notification features build on: a sharing
 * "offer", a chat message, a comment, or a follow request are all just Activity
 * Streams 2.0 activities posted to and read from an inbox. Use this interface
 * directly to put notifications in your own domain; use the higher-level
 * [NotificationsManager] when you want SolidShare's sharing semantics layered on
 * top of it.
 *
 * Pull-based: [list] enumerates an inbox on demand. Push delivery via the Solid
 * Notifications Protocol channels can later layer on top of this same transport.
 *
 * Obtain an instance via [NotificationTransport.getInstance].
 *
 * Spec anchors:
 *  - LDN: https://www.w3.org/TR/ldn/
 *  - Solid Notifications: https://solidproject.org/TR/notifications-protocol
 */
public interface NotificationTransport {

    public companion object {
        /** Returns a transport backed by [authenticator]'s resource manager. */
        public fun getInstance(authenticator: Authenticator): NotificationTransport =
            NotificationTransportImplementation.getInstance(authenticator)

        /** Returns a transport backed by an existing [resourceManager]. */
        public fun getInstance(resourceManager: SolidResourceManager): NotificationTransport =
            NotificationTransportImplementation.getInstance(resourceManager)
    }

    /**
     * Resolves the LDN inbox advertised by [webId] — via `ldp:inbox` in the
     * WebID profile, a `Link: rel="…ldp#inbox"` HEAD header, or an extended
     * profile document — returning its URI string, or `null` if none is
     * discoverable.
     */
    public suspend fun discoverInbox(webId: String): SolidResult<String?>

    /**
     * POSTs an opaque notification [body] to [inbox] as [webId], optionally
     * tagging it with a [slug] hint for the server-allocated name. Returns the
     * created notification's `Location` URI when the server provides one.
     */
    public suspend fun post(
        webId: String,
        inbox: String,
        contentType: String,
        body: ByteArray,
        slug: String? = null,
    ): SolidResult<String?>

    /**
     * Reads every item currently in [inbox] and decodes each into a generic
     * [RawNotification]. Items that cannot be read or parsed are skipped, so a
     * single malformed notification never fails the whole listing.
     */
    public suspend fun list(
        webId: String,
        inbox: String,
    ): SolidResult<List<RawNotification>>

    /** Reads and decodes a single notification by [notificationUri]. */
    public suspend fun read(
        webId: String,
        notificationUri: String,
    ): SolidResult<RawNotification>

    /** Deletes a single notification by [notificationUri]; `true` when confirmed. */
    public suspend fun delete(
        webId: String,
        notificationUri: String,
    ): SolidResult<Boolean>

    /**
     * Subscribes to real-time change notifications for [resourceUri] over a Solid
     * `WebSocketChannel2023` channel, returning a cold [Flow] of [RawNotification]s.
     *
     * Negotiation is done up front (this suspends until the channel is created): the resource's
     * storage description is read for a WebSocket subscription service, a channel is requested for
     * [resourceUri] as the topic, and the server's `notify:receiveFrom` WebSocket URL is opened.
     * Each frame the pod pushes is decoded like an inbox notification.
     *
     * The WebSocket lives only while the returned Flow is collected — cancelling the collection
     * closes the socket. **On Android, collect it only while a screen is active** (e.g. under
     * `repeatOnLifecycle(STARTED)`): a persistent socket doesn't survive Doze and drains battery,
     * so this complements — it does not replace — background inbox polling.
     *
     * Requires a transport built from an [Authenticator]; returns [SolidResult.Failure] when the
     * pod advertises no WebSocket subscription service or when built without an authenticated session.
     */
    public suspend fun subscribe(
        webId: String,
        resourceUri: String,
    ): SolidResult<Flow<RawNotification>>
}
