package com.erfangholami.androidsolidservices.api.sharing

import com.erfangholami.androidsolidservices.shared.model.sharing.CATALOG_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.GIVEN_SHARES_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.RECEIVED_SHARES_FILE_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.SHARES_CONTAINER_NAME
import com.erfangholami.androidsolidservices.shared.model.sharing.SOLIDSHARE_CONTAINER_NAME
import java.net.URI

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
    public fun rootContainer(podRoot: URI): URI

    /** The container holding the private given/received indexes. */
    public fun sharesContainer(podRoot: URI): URI

    /** The given-shares index file. */
    public fun givenIndex(podRoot: URI): URI

    /** The received-shares index file. */
    public fun receivedIndex(podRoot: URI): URI

    /** The public catalog file. */
    public fun catalog(podRoot: URI): URI

    /**
     * Path fragments a full pod-tree rebuild must skip so the scan never
     * recurses into this profile's own bookkeeping. Each is matched as a
     * substring of a resource URI.
     */
    public fun excludedScanPaths(): List<String>
}

/** SolidShare's layout: everything under `{podRoot}solidshare/`. */
public object SolidShareStorageLayout : ShareStorageLayout {
    override fun rootContainer(podRoot: URI): URI =
        URI.create("$podRoot$SOLIDSHARE_CONTAINER_NAME")

    override fun sharesContainer(podRoot: URI): URI =
        URI.create("$podRoot$SHARES_CONTAINER_NAME")

    override fun givenIndex(podRoot: URI): URI =
        URI.create("$podRoot$SHARES_CONTAINER_NAME$GIVEN_SHARES_FILE_NAME")

    override fun receivedIndex(podRoot: URI): URI =
        URI.create("$podRoot$SHARES_CONTAINER_NAME$RECEIVED_SHARES_FILE_NAME")

    override fun catalog(podRoot: URI): URI =
        URI.create("$podRoot$SOLIDSHARE_CONTAINER_NAME$CATALOG_FILE_NAME")

    override fun excludedScanPaths(): List<String> = listOf("/$SOLIDSHARE_CONTAINER_NAME")
}
