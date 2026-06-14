package com.erfangholami.androidsolidservices.shared.model.sharing

/**
 * Collapses a flat list of [GivenShare] — which may carry several rows for one
 * `(receiver, resourceUri)` when a grant writes multiple implied acl:modes (e.g.
 * Read+Append for "Add", Read+Write for "Edit") — into a single row per
 * pair carrying the strongest [ShareMode]. Each receiver therefore appears once
 * per resource at its effective access level. The earliest known
 * [GivenShare.createdAt] is preserved.
 */
public fun List<GivenShare>.collapseByReceiver(): List<GivenShare> =
    groupBy { it.receiver.toRdfSubject() to it.resourceUri }
        .map { (_, rows) ->
            GivenShare(
                receiver = rows.first().receiver,
                mode = ShareMode.strongest(rows.map { it.mode }) ?: rows.first().mode,
                resourceUri = rows.first().resourceUri,
                createdAt = rows.firstNotNullOfOrNull { it.createdAt },
            )
        }

/**
 * The received-share counterpart of [collapseByReceiver]: one [ReceivedShare]
 * per `(ownerWebId, resourceUri)` at the strongest [ShareMode], preserving the
 * earliest known [ReceivedShare.addedAt].
 */
public fun List<ReceivedShare>.collapseByOwner(): List<ReceivedShare> =
    groupBy { it.ownerWebId to it.resourceUri }
        .map { (_, rows) ->
            ReceivedShare(
                ownerWebId = rows.first().ownerWebId,
                mode = ShareMode.strongest(rows.map { it.mode }) ?: rows.first().mode,
                resourceUri = rows.first().resourceUri,
                addedAt = rows.firstNotNullOfOrNull { it.addedAt },
            )
        }
