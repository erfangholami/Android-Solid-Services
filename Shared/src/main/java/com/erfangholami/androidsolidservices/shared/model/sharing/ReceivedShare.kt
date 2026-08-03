package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A share the user has received — [ownerWebId] has granted the current user [mode]
 * on [resourceUri]. Stored in `{podRoot}/solidshare/shares/received_shares.ttl`.
 *
 * [addedAt] is the ISO-8601 instant recorded for the share (`dcterms:created` on
 * the index record). When the share is auto-synced from an inbox offer this is
 * the owner's original share time (the offer's `as:published`); when added by
 * scanning or pasting a link it is the moment it was added. `null` for legacy
 * rows written before timestamps existed.
 *
 * [resourceType] is the RDF class IRI of the entity the share carries (e.g.
 * `https://schema.org/Ticket`) and [resourceName] its human title, as announced
 * by the owner's notification or supplied when the share was added
 * (`solidshare:resourceType` / `dcterms:title` on the index record). Both are
 * `null` for plain file/folder shares and legacy rows — readers must render
 * unknown or absent types generically.
 */
@Parcelize
public data class ReceivedShare(
    val ownerWebId: String,
    val mode: ShareMode,
    val resourceUri: String,
    val addedAt: String? = null,
    val resourceType: String? = null,
    val resourceName: String? = null,
) : Parcelable
