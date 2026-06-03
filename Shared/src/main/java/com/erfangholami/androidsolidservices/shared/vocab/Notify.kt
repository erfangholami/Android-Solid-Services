package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Solid Notifications Protocol vocabulary constants.
 * http://www.w3.org/ns/solid/notifications#
 * Spec: https://solidproject.org/TR/notifications-protocol
 */
public object Notify {
    public const val NAMESPACE: String = "http://www.w3.org/ns/solid/notifications#"

    //Channel types
    public const val WEB_SOCKET_CHANNEL_2023: String = "${NAMESPACE}WebSocketChannel2023"
    public const val EVENT_SOURCE_CHANNEL_2023: String = "${NAMESPACE}EventSourceChannel2023"
    public const val WEBHOOK_CHANNEL_2023: String = "${NAMESPACE}WebhookChannel2023"
    public const val STREAMING_HTTP_CHANNEL_2023: String = "${NAMESPACE}StreamingHTTPChannel2023"
    public const val LDN_CHANNEL_2023: String = "${NAMESPACE}LDNChannel2023"

    //Subscription predicates
    /** Storage advertises one or more subscription endpoints via this predicate. */
    public const val SUBSCRIPTION: String = "${NAMESPACE}subscription"

    /** The resource being subscribed to. */
    public const val TOPIC: String = "${NAMESPACE}topic"

    /** The channel type IRI. */
    public const val CHANNEL_TYPE: String = "${NAMESPACE}channelType"

    /** Send notifications to this endpoint (push/webhook pattern). */
    public const val SEND_TO: String = "${NAMESPACE}sendTo"

    /** Receive notifications from this endpoint (pull/SSE/WS pattern). */
    public const val RECEIVE_FROM: String = "${NAMESPACE}receiveFrom"

    //Channel feature predicates
    /** Start sending notifications after this datetime (xsd:dateTime). */
    public const val START_AT: String = "${NAMESPACE}startAt"

    /** Stop sending notifications after this datetime (xsd:dateTime). */
    public const val END_AT: String = "${NAMESPACE}endAt"

    /** Last known state of the resource (xsd:string). */
    public const val STATE: String = "${NAMESPACE}state"

    /** Minimum duration between notifications (xsd:duration). */
    public const val RATE: String = "${NAMESPACE}rate"

    /** Accepted media types for notifications. */
    public const val ACCEPT: String = "${NAMESPACE}accept"

    //Notification message predicates
    /** The last known ETag / state of the resource at time of notification. */
    public const val LAST_KNOWN_STATE: String = "${NAMESPACE}lastKnownState"
}
