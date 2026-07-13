package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.shared.model.sharing.CATALOG_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.GIVEN_SHARES_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.RECEIVED_SHARES_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.SHARES_CONTAINER_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.SOLIDSHARE_CONTAINER_NAME

/**
 * Pod-relative placement of a sharing profile's bookkeeping: the private
 * given/received index files, the public catalog, and the container(s) that
 * hold them. Lets the sharing engine store its data somewhere other than
 * SolidShare's `solidshare/` tree.
 *
 * [podRoot] is always the pod storage root and ends with `/`.
 *
 * The default is [SolidShareStorageLayout].
 */
public interface ShareStorageLayout {
    /** The container under [podRoot] that holds this profile's data. */
    public fun rootContainer(podRoot: String): String

    /** The container holding the private given/received indexes. */
    public fun sharesContainer(podRoot: String): String

    /** The given-shares index file. */
    public fun givenIndex(podRoot: String): String

    /** The received-shares index file. */
    public fun receivedIndex(podRoot: String): String

    /** The public catalog file. */
    public fun catalog(podRoot: String): String

    /**
     * Path fragments a full pod-tree rebuild must skip, each matched as a
     * substring of a resource URI. Covers this profile's own bookkeeping (so the
     * scan never recurses into it) and any resource whose public access is
     * mandated by the protocol rather than chosen by the user — such as the LDN
     * inbox (`acl:Append`) and the public WebID profile document (`acl:Read`) —
     * so they are never listed as shares the user could revoke.
     */
    public fun excludedScanPaths(): List<String>
}

/** SolidShare's layout: everything under `{podRoot}solidshare/`. */
public object SolidShareStorageLayout : ShareStorageLayout {
    override fun rootContainer(podRoot: String): String =
        "$podRoot$SOLIDSHARE_CONTAINER_NAME"

    override fun sharesContainer(podRoot: String): String =
        "$podRoot$SHARES_CONTAINER_NAME"

    override fun givenIndex(podRoot: String): String =
        "$podRoot$SHARES_CONTAINER_NAME$GIVEN_SHARES_FILE_NAME"

    override fun receivedIndex(podRoot: String): String =
        "$podRoot$SHARES_CONTAINER_NAME$RECEIVED_SHARES_FILE_NAME"

    override fun catalog(podRoot: String): String =
        "$podRoot$SOLIDSHARE_CONTAINER_NAME$CATALOG_FILE_NAME"

    override fun excludedScanPaths(): List<String> =
        listOf("/$SOLIDSHARE_CONTAINER_NAME", "/inbox/", "/profile/card")
}
