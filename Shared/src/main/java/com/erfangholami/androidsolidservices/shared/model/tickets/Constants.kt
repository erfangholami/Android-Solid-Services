package com.erfangholami.androidsolidservices.shared.model.tickets

/** Path suffix appended to a storage root when allocating the tickets container. */
public const val TICKETS_DIRECTORY_SUFFIX: String = "tickets/"

/** File name of the tickets index document within a tickets container. */
public const val TICKETS_INDEX_FILE_NAME: String = "index.ttl"

/** File-name suffix of a ticket RDF document. */
public const val TICKET_FILE_SUFFIX: String = ".ttl"

/** Fragment identifying a ticket's primary subject within its document. */
public const val TICKET_FRAGMENT: String = "#this"
