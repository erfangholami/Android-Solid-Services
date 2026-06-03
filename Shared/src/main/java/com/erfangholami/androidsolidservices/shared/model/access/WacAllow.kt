package com.erfangholami.androidsolidservices.shared.model.access

import android.os.Parcel
import android.os.Parcelable

/**
 * Parsed representation of the `WAC-Allow` HTTP response header.
 *
 * Format: `WAC-Allow: user="read write", public="read"`
 *
 * Spec: https://solidproject.org/TR/wac — WAC-Allow header
 */
public data class WacAllow(
    /** Modes the currently authenticated user is granted, lower-cased (e.g. `read`, `write`). */
    val userModes: Set<String>,
    /** Modes granted to unauthenticated/public agents, lower-cased. */
    val publicModes: Set<String>
) : Parcelable {
    public fun canRead(): Boolean = userModes.contains("read")
    public fun canWrite(): Boolean = userModes.contains("write")

    /**
     * `true` if the user may append. Folds in `write`, since a user who may
     * write may also append even when the server does not list `append`
     * separately.
     */
    public fun canAppend(): Boolean = userModes.contains("append") || canWrite()
    public fun canControl(): Boolean = userModes.contains("control")

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStringList(userModes.toList())
        dest.writeStringList(publicModes.toList())
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<WacAllow> = object : Parcelable.Creator<WacAllow> {
            override fun createFromParcel(parcel: Parcel): WacAllow = WacAllow(
                userModes = parcel.createStringArrayList()!!.toSet(),
                publicModes = parcel.createStringArrayList()!!.toSet(),
            )

            override fun newArray(size: Int): Array<WacAllow?> = arrayOfNulls(size)
        }

        /**
         * Parses a raw `WAC-Allow` header value into a [WacAllow].
         *
         * Returns `null` when [headerValue] is `null` (header absent).
         * Unrecognised groups are ignored; the `user` and `public` groups
         * default to empty sets when not present in the header.
         */
        public fun parse(headerValue: String?): WacAllow? {
            headerValue ?: return null
            val groups = mutableMapOf<String, Set<String>>()
            val regex = Regex("""(\w+)\s*=\s*"([^"]*)"""")
            regex.findAll(headerValue).forEach { match ->
                val group = match.groupValues[1]
                val modes = match.groupValues[2]
                    .split(" ")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                groups[group] = modes
            }
            return WacAllow(
                userModes = groups["user"] ?: emptySet(),
                publicModes = groups["public"] ?: emptySet()
            )
        }
    }
}
