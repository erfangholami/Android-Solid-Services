package com.erfangholami.androidsolidservices.shared.model.sharing

import com.erfangholami.androidsolidservices.shared.vocab.ACL

/**
 * Access mode granted to the receiver of a share.
 *
 * Maps 1:1 to a WAC `acl:mode` predicate.
 */
public enum class ShareMode {
    READ,
    APPEND,
    WRITE;

    public fun toAclPredicate(): String = when (this) {
        READ -> ACL.READ
        APPEND -> ACL.APPEND
        WRITE -> ACL.WRITE
    }

    /**
     * The full set of WAC `acl:mode` predicates a grant of this mode must
     * write. WAC has **no mode subsumption** — `acl:Write` does not grant
     * `acl:Read` — so a UI capability that's meant to include reading has to
     * assert `acl:Read` explicitly:
     *
     *  - [READ]   → `acl:Read`                ("View")
     *  - [APPEND] → `acl:Read`, `acl:Append`  ("Add": read + append)
     *  - [WRITE]  → `acl:Read`, `acl:Write`   ("Edit": read + modify)
     *
     * Without the explicit `acl:Read` a "Write" receiver could overwrite a
     * resource they cannot GET.
     */
    public fun impliedAclModes(): Set<String> = when (this) {
        READ -> setOf(ACL.READ)
        APPEND -> setOf(ACL.READ, ACL.APPEND)
        WRITE -> setOf(ACL.READ, ACL.WRITE)
    }

    public companion object {
        public fun fromAclPredicate(predicate: String): ShareMode? = when (predicate) {
            ACL.READ -> READ
            ACL.APPEND -> APPEND
            ACL.WRITE -> WRITE
            else -> null
        }

        /**
         * Picks the most permissive mode from a set of WAC predicates.
         * Write > Append > Read.
         */
        public fun strongest(predicates: Set<String>): ShareMode? = when {
            predicates.contains(ACL.WRITE) -> WRITE
            predicates.contains(ACL.APPEND) -> APPEND
            predicates.contains(ACL.READ) -> READ
            else -> null
        }

        /**
         * Picks the most permissive of a collection of [ShareMode]s
         * (Write > Append > Read), or `null` if empty. Used to fold the several
         * implied acl:modes a grant writes (e.g. Read+Append for "Add") back into
         * the single logical level shown per receiver.
         */
        public fun strongest(modes: Collection<ShareMode>): ShareMode? = when {
            modes.contains(WRITE) -> WRITE
            modes.contains(APPEND) -> APPEND
            modes.contains(READ) -> READ
            else -> null
        }
    }
}
