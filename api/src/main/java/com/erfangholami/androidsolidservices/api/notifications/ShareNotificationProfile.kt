package com.erfangholami.androidsolidservices.api.notifications

import com.erfangholami.androidsolidservices.shared.vocab.ShareNotificationVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareNotificationVocabulary

/**
 * The SolidShare-specific conventions the share-notifications layer is
 * parameterized over — the legacy access-mode fallback literals it accepts when
 * reading, and the inbox `Slug` prefixes it writes — so the offer/accept/reject
 * flow can run under a different namespace and branding instead of being
 * hard-wired to SolidShare's choices.
 *
 * The wire shape itself is standards-based (Activity Streams 2.0 + WAC
 * `acl:mode` + SAI `interop:AccessRequest`); only these two app-specific
 * conventions are profiled. Pass a profile to [NotificationsManager.getInstance];
 * the default is [SolidShareNotificationProfile], which reproduces SolidShare's
 * behaviour exactly.
 */
public interface ShareNotificationProfile {
    /** Legacy access-mode fallback literals accepted when reading notifications. */
    public val vocabulary: ShareNotificationVocabulary

    /** `Slug` header prefixes used when writing notifications to an inbox. */
    public val slugs: NotificationSlugs
}

/** The default profile: SolidShare's fallback literals and `solidshare-*` slugs. */
public object SolidShareNotificationProfile : ShareNotificationProfile {
    override val vocabulary: ShareNotificationVocabulary = SolidShareNotificationVocabulary
    override val slugs: NotificationSlugs = SolidShareNotificationSlugs
}
