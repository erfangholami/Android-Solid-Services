package com.erfangholami.androidsolidservices.shared.model.sharing

/**
 * Pod-relative paths used by the sharing feature.
 *
 * - [SHARES_CONTAINER_NAME] holds private bookkeeping (given/received indexes).
 *   Owner-only ACL. Kept under [SOLIDSHARE_CONTAINER_NAME] rather than a
 *   dot-prefixed root path because Community Solid Server reserves the
 *   `/.*` namespace for server-internal resources and rejects any access
 *   to it with 403 `ForbiddenHttpError`.
 */
public const val SOLIDSHARE_CONTAINER_NAME: String = "solidshare/"
public const val SHARES_CONTAINER_NAME: String = "solidshare/shares/"

public const val GIVEN_SHARES_FILE_NAME: String = "given_shares.ttl"
public const val RECEIVED_SHARES_FILE_NAME: String = "received_shares.ttl"

public const val CATALOG_FILE_NAME: String = "catalog.ttl"

/** Custom URI scheme that opens scanned shares directly in the SolidShare app. */
public const val SOLID_SHARE_URI_SCHEME: String = "solidshare"
