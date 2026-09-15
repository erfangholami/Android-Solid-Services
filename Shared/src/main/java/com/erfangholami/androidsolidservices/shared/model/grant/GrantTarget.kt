package com.erfangholami.androidsolidservices.shared.model.grant

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a grant entry applies to, as the host stores it after the user approved the grant.
 *
 * An app names what it wants before sign-in with a [RequestedTarget]; the host resolves that
 * against the chosen account into these, which carry absolute IRIs.
 *
 * The serial names are persisted by hosts; never rename one.
 */
@Serializable
public sealed class GrantTarget : Parcelable {

    /**
     * Everything the WebID reaches: every resource, every data module, sharing and
     * notifications.
     */
    @Parcelize
    @Serializable
    @SerialName("pod")
    public data object Pod : GrantTarget()

    /**
     * One resource by IRI. A container (an IRI ending in `/`) covers its whole subtree; any
     * other resource covers itself only.
     */
    @Parcelize
    @Serializable
    @SerialName("resource")
    public data class Resource(val uri: String) : GrantTarget() {

        /** `true` when [resourceUri] is this resource or, for a container, lies anywhere below it. */
        public fun covers(resourceUri: String): Boolean =
            resourceUri == uri || (uri.endsWith('/') && resourceUri.startsWith(uri))
    }

    /**
     * A data module by its id, one of the constants in
     * [com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId]. Covers the
     * module's own verbs and the containers the module occupies on the account, which only the
     * host can resolve.
     */
    @Parcelize
    @Serializable
    @SerialName("module")
    public data class Module(val id: String) : GrantTarget()
}
