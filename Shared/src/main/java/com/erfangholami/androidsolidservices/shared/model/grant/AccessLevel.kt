package com.erfangholami.androidsolidservices.shared.model.grant

import kotlinx.serialization.Serializable

/**
 * How much an app may do on the target of a grant entry.
 *
 * The levels form a ladder: each one includes everything below it. The first three carry the
 * meaning the user already knows from shares as View, Add and Edit. [FULL] adds what no ordinary
 * app should hold: changing who else may reach the user's data, and speaking to other people's
 * inboxes as the user.
 *
 * Declaration order is the ladder, which [includes] relies on, and the names are what a host
 * persists; append new levels at the end and never rename one.
 */
@Serializable
public enum class AccessLevel {

    /** Read, head, list, probe and stream a resource; read a data module. */
    VIEW,

    /** [VIEW], plus creating new members in a container by POST; creating in a data module. */
    ADD,

    /**
     * [ADD], plus creating by PUT, updating, patching, deleting, renaming and streaming writes;
     * updating and deleting in a data module.
     */
    EDIT,

    /**
     * [EDIT], plus every sharing verb, access-control repair, the catalog, and sending
     * notifications as the user.
     */
    FULL;

    /** `true` when this level grants at least what [required] grants. */
    public fun includes(required: AccessLevel): Boolean = ordinal >= required.ordinal

    public companion object {

        /** The most permissive of [levels], or `null` when empty. */
        public fun strongest(levels: Collection<AccessLevel>): AccessLevel? = levels.maxOrNull()
    }
}
