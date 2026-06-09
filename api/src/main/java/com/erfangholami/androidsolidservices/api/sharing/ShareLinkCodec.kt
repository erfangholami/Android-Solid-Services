package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink

/**
 * Encodes and parses the share links a profile puts in QR codes and shareable
 * URLs — the app-facing wrapping of a resource URI, distinct from the on-pod
 * access grant. Lets a profile use a scheme other than SolidShare's
 * `solidshare://`.
 *
 * The default is the SolidShare codec carried by [SolidShareProfile].
 */
public interface ShareLinkCodec {
    /**
     * Builds a deep link that opens [resourceUri] in the app, optionally
     * carrying [ownerWebId] so the receiver can identify the sender.
     */
    public fun deepLink(resourceUri: String, ownerWebId: String?): String

    /** Parses a deep link produced by [deepLink], or `null` if it isn't one. */
    public fun parse(deepLink: String): ParsedShareLink?

    /** The bare `https://…` URL form of [resourceUri] for non-app scanners. */
    public fun bareUrl(resourceUri: String): String
}
