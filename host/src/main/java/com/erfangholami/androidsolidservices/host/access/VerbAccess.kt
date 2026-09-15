package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel

/**
 * The minimum level each group of verbs needs, in one place so the binders and the developer
 * docs read the same table.
 *
 * Resource verbs are checked on the resource they name. Data-module verbs are checked on the
 * module, or on the container a verb takes explicitly. Sharing verbs that name a resource are
 * checked on it; sharing and inbox verbs that touch the account as a whole are checked on the
 * whole pod.
 */
public object VerbAccess {

    /**
     * `head`, `read`, `readRdf`, `readContainer`, `listContainer`, `exists`, `probeAccess`,
     * `readStream`; a module's `get`, `list`, photo and artifact reads; the source of a `copy`.
     */
    public val READ: AccessLevel = AccessLevel.VIEW

    /** `createInContainer`, `createInContainerRdf`, `post`; a module's `create` verbs. */
    public val APPEND: AccessLevel = AccessLevel.ADD

    /**
     * `create`, `createRdf`, `update`, `updateRdf`, `patch`, `delete`, `deleteRdf`,
     * `deleteContainer`, `ensureContainer`, `putRaw`, `writeStream`, `rename`, the source of a
     * `move` and the destination of a `copy` or `move`; a module's `update`, `delete`, `rename`,
     * photo and artifact writes.
     */
    public val WRITE: AccessLevel = AccessLevel.EDIT

    /** Every sharing verb, on the resource it names or on the whole pod. */
    public val SHARE: AccessLevel = AccessLevel.FULL

    /** `listNotifications`, `listRequests`, `ensureInbox`, on the whole pod. */
    public val INBOX_READ: AccessLevel = AccessLevel.VIEW

    /** `deleteNotification`, `compactInbox`, on the whole pod. */
    public val INBOX_WRITE: AccessLevel = AccessLevel.EDIT

    /** Every `send*` and `recordDecision*` verb, on the whole pod. */
    public val INBOX_SEND: AccessLevel = AccessLevel.FULL
}
