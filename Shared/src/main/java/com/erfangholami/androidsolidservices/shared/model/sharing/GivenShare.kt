package com.erfangholami.androidsolidservices.shared.model.sharing

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A share the user has given (created) — the user grants [receiver] access of [mode]
 * on [resourceUri]. Stored in `{podRoot}/solidshare/shares/given_shares.ttl`.
 *
 * [createdAt] is the ISO-8601 instant the share was created (`dcterms:created`
 * on the index record), or `null` for legacy rows written before timestamps
 * existed or rows reconstructed from a pod ACL scan (where the moment is
 * unknown). Several modes for the same `(receiver, resourceUri)` pair share the
 * one record, hence the one timestamp.
 *
 * [resourceType] is the RDF class IRI of the entity the share carries (e.g.
 * `https://schema.org/Ticket`) when the share was created for a data-module
 * entity, and [resourceName] its human title at share time
 * (`solidshare:resourceType` / `dcterms:title` on the index record). Both are
 * `null` for plain file/folder shares, legacy rows, and ACL-scan rows — readers
 * must render unknown or absent types generically.
 */
@Parcelize
public data class GivenShare(
    val receiver: ShareReceiver,
    val mode: ShareMode,
    val resourceUri: String,
    val createdAt: String? = null,
    val resourceType: String? = null,
    val resourceName: String? = null,
) : Parcelable
