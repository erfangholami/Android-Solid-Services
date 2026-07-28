package com.erfangholami.androidsolidservices.shared.model.tickets

/** Path suffix appended to a storage root when allocating the tickets container. */
public const val TICKETS_DIRECTORY_SUFFIX: String = "tickets/"

/**
 * Document name minted for the tickets index inside a tickets container. Extension-less on
 * purpose: the index is linked data reached by URL — the type index links to this document
 * and its rows link to each ticket — so the URI never encodes a representation (the module
 * reads and writes JSON-LD; how the server persists it is its own business). Only used when
 * bootstrapping; discovery always follows the registered URL, whatever it is named.
 */
public const val TICKETS_INDEX_NAME: String = "index"

/**
 * Document name of a tickets index bootstrapped by the legacy flat layout, used to locate the
 * index of a container that is still registered as a `solid:instanceContainer`.
 */
public const val LEGACY_TICKETS_INDEX_FILE_NAME: String = "index.ttl"

/**
 * Document name minted for a ticket inside its per-ticket container
 * (`{tickets}/{uuid}/ticket`). The container also holds the ticket's binary attachments
 * (artifact, pass images). Extension-less like [TICKETS_INDEX_NAME].
 */
public const val TICKET_DOCUMENT_NAME: String = "ticket"

/** Fragment identifying a ticket's primary subject within its document. */
public const val TICKET_FRAGMENT: String = "#this"
