package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * One row in an owner-published catalog.
 *
 * Catalogs let an owner advertise resources they may be willing to share
 * so requesters can discover what to ask for. The catalog itself lives
 * at `{podRoot}/solidshare/catalog.ttl` and is publicly readable; the
 * resources it points at can still be private until a request is granted.
 *
 * These entries drive the request-to-share flow: a viewer fetches an
 * owner's catalog to see what can be requested, then sends an access
 * request for one of the listed resources.
 */
@Parcelize
public data class CatalogEntry(
    /** URI of the resource being advertised. */
    val resourceUri: String,

    /** Human-readable title (`dc:title`). */
    val title: String,

    /** Optional longer description (`dc:description`). */
    val description: String?,

    /** Optional preview / depiction URL (`foaf:depiction`). */
    val depictionUri: String?,
) : Parcelable
