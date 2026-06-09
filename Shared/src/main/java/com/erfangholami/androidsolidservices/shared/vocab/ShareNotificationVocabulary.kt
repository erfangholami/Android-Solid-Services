package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Legacy access-mode fallback literals a share-notification profile recognises
 * when an incoming notification carries its mode as a string literal rather than
 * the standard WAC `acl:mode` IRI. Lets the reader accept a namespace other than
 * SolidShare's for these older notifications; new notifications always use
 * `acl:mode`, so this only affects backward-compatible reads.
 *
 * The default is [SolidShareNotificationVocabulary].
 */
public interface ShareNotificationVocabulary {
    /** Fallback literal predicate for an offer/accept's access mode (e.g. `solidshare:mode`). */
    public val modeLiteral: String

    /** Fallback literal predicate for a request's requested mode (e.g. `solidshare:requestedMode`). */
    public val requestedModeLiteral: String
}

/** SolidShare's notification fallback literals, under `https://solidshare.com/ns#`. */
public object SolidShareNotificationVocabulary : ShareNotificationVocabulary {
    override val modeLiteral: String = SolidShare.MODE
    override val requestedModeLiteral: String = SolidShare.REQUESTED_MODE
}
