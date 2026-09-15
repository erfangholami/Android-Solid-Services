package com.erfangholami.androidsolidservices.shared.model.grant

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What an app asks for before it knows which account the user will pick.
 *
 * At authorize time the app knows neither the WebID nor the pod root, so a resource is named as
 * a path relative to the pod's storage. The host resolves every [Path] against each
 * `pim:storage` of the chosen account into [GrantTarget.Resource] entries, one per storage.
 */
@Serializable
public sealed class RequestedTarget : Parcelable {

    /** The whole pod; becomes [GrantTarget.Pod]. */
    @Parcelize
    @Serializable
    @SerialName("pod")
    public data object Pod : RequestedTarget()

    /**
     * A path below the storage root, such as `notes/` or `photos/2026/trip.jpg`. A trailing `/`
     * names a container, which covers its subtree once granted. Leading slashes are ignored.
     */
    @Parcelize
    @Serializable
    @SerialName("path")
    public data class Path(val relativePath: String) : RequestedTarget() {

        /**
         * The absolute IRI of this path inside [storageRoot], or `null` when the path cannot be
         * placed there: it is blank, it carries a scheme, it has an empty segment, or it steps
         * with `.` or `..`.
         */
        public fun resolveAgainst(storageRoot: String): String? {
            val trimmed = relativePath.trimStart('/')
            if (trimmed.isBlank() || trimmed.contains("://")) return null
            val segments = trimmed.split('/')
            if (segments.any { it == "." || it == ".." }) return null
            if (segments.dropLast(1).any { it.isEmpty() }) return null
            return storageRoot.trimEnd('/') + "/" + trimmed
        }
    }

    /**
     * A data module by its id, one of the constants in
     * [com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId]; becomes
     * [GrantTarget.Module].
     */
    @Parcelize
    @Serializable
    @SerialName("module")
    public data class Module(val id: String) : RequestedTarget()
}
