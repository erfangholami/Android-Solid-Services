package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Schema.org vocabulary constants.
 * https://schema.org/
 * Broadly used in Solid apps for typed data (media, events, products, etc.).
 */
public object Schema {
    public const val NAMESPACE: String = "https://schema.org/"

    // Types
    public const val THING: String = "${NAMESPACE}Thing"
    public const val CREATIVE_WORK: String = "${NAMESPACE}CreativeWork"
    public const val MEDIA_OBJECT: String = "${NAMESPACE}MediaObject"
    public const val MOVIE: String = "${NAMESPACE}Movie"
    public const val MUSIC_RECORDING: String = "${NAMESPACE}MusicRecording"
    public const val BOOK: String = "${NAMESPACE}Book"
    public const val ARTICLE: String = "${NAMESPACE}Article"
    public const val BLOG_POSTING: String = "${NAMESPACE}BlogPosting"
    public const val EVENT: String = "${NAMESPACE}Event"
    public const val PERSON: String = "${NAMESPACE}Person"
    public const val ORGANIZATION: String = "${NAMESPACE}Organization"
    public const val PLACE: String = "${NAMESPACE}Place"
    public const val PRODUCT: String = "${NAMESPACE}Product"
    public const val OFFER: String = "${NAMESPACE}Offer"

    // Descriptive predicates
    public const val NAME: String = "${NAMESPACE}name"
    public const val DESCRIPTION: String = "${NAMESPACE}description"
    public const val IMAGE: String = "${NAMESPACE}image"
    public const val URL: String = "${NAMESPACE}url"
    public const val IDENTIFIER: String = "${NAMESPACE}identifier"
    public const val SAME_AS: String = "${NAMESPACE}sameAs"
    public const val ADDITIONAL_TYPE: String = "${NAMESPACE}additionalType"

    // Creative work predicates
    public const val DATE_CREATED: String = "${NAMESPACE}dateCreated"
    public const val DATE_MODIFIED: String = "${NAMESPACE}dateModified"
    public const val DATE_PUBLISHED: String = "${NAMESPACE}datePublished"
    public const val AUTHOR: String = "${NAMESPACE}author"
    public const val CREATOR: String = "${NAMESPACE}creator"
    public const val PUBLISHER: String = "${NAMESPACE}publisher"
    public const val GENRE: String = "${NAMESPACE}genre"
    public const val KEYWORDS: String = "${NAMESPACE}keywords"
    public const val CONTENT_URL: String = "${NAMESPACE}contentUrl"
    public const val ENCODING_FORMAT: String = "${NAMESPACE}encodingFormat"
    public const val DURATION: String = "${NAMESPACE}duration"
    public const val IN_LANGUAGE: String = "${NAMESPACE}inLanguage"
    public const val THUMBNAIL_URL: String = "${NAMESPACE}thumbnailUrl"
    public const val LICENSE: String = "${NAMESPACE}license"

    // Person / Organization predicates
    public const val GIVEN_NAME: String = "${NAMESPACE}givenName"
    public const val FAMILY_NAME: String = "${NAMESPACE}familyName"
    public const val EMAIL: String = "${NAMESPACE}email"
    public const val TELEPHONE: String = "${NAMESPACE}telephone"
    public const val ADDRESS: String = "${NAMESPACE}address"
    public const val MEMBER_OF: String = "${NAMESPACE}memberOf"
    public const val KNOWS: String = "${NAMESPACE}knows"
    public const val BIRTH_DATE: String = "${NAMESPACE}birthDate"

    // Event predicates
    public const val START_DATE: String = "${NAMESPACE}startDate"
    public const val END_DATE: String = "${NAMESPACE}endDate"
    public const val LOCATION: String = "${NAMESPACE}location"
    public const val ORGANIZER: String = "${NAMESPACE}organizer"
    public const val ATTENDEE: String = "${NAMESPACE}attendee"

    // Ticket types
    public const val TICKET: String = "${NAMESPACE}Ticket"
    public const val SEAT: String = "${NAMESPACE}Seat"

    // Ticket predicates
    public const val TICKET_NUMBER: String = "${NAMESPACE}ticketNumber"

    /** The opaque token/payload encoded in the ticket's barcode. */
    public const val TICKET_TOKEN: String = "${NAMESPACE}ticketToken"
    public const val ISSUED_BY: String = "${NAMESPACE}issuedBy"
    public const val UNDER_NAME: String = "${NAMESPACE}underName"
    public const val TICKETED_SEAT: String = "${NAMESPACE}ticketedSeat"
    public const val SEAT_NUMBER: String = "${NAMESPACE}seatNumber"
    public const val SEAT_ROW: String = "${NAMESPACE}seatRow"
    public const val SEAT_SECTION: String = "${NAMESPACE}seatSection"
    public const val TOTAL_PRICE: String = "${NAMESPACE}totalPrice"
    public const val PRICE_CURRENCY: String = "${NAMESPACE}priceCurrency"
    public const val DATE_ISSUED: String = "${NAMESPACE}dateIssued"
    public const val VALID_FROM: String = "${NAMESPACE}validFrom"
    public const val VALID_THROUGH: String = "${NAMESPACE}validThrough"
}
