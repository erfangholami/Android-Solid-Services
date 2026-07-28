package com.erfangholami.androidsolidservices.shared.vocab

/**
 * The SolidShare vocabulary: terms the project mints because no published vocabulary has them.
 *
 * Two domains are covered — the sharing pipeline (share records and the access literals carried
 * on LDN inbox notifications) and the wallet (the parts of a ticket that schema.org cannot
 * express). See `documents/TICKET_VOCAB.md` for the full ticket dictionary and the justification
 * for each minted term; the short version is that schema.org has no barcode symbology, no
 * scheduled-vs-actual time pair, no fare class, no venue timings, and no presentation model.
 *
 * Terms are minted under [NAMESPACE] — `solidshare.app`, the domain the project owns and
 * verifies, so the namespace IRI can dereference to the vocabulary it names.
 */
public object SolidShare {

    /** The namespace every term below is minted under. */
    public const val NAMESPACE: String = "https://solidshare.app/ns#"

    /** Access mode literal on an Offer notification: "read" | "append" | "write". */
    public const val MODE: String = "${NAMESPACE}mode"

    /**
     * Activity type carried on a request-to-share notification:
     * `<#req> rdf:type solidshare:AccessRequest`.
     */
    public const val ACCESS_REQUEST: String = "${NAMESPACE}AccessRequest"

    /** Mode literal on an AccessRequest: "read" | "append" | "write". */
    public const val REQUESTED_MODE: String = "${NAMESPACE}requestedMode"

    /**
     * `rdf:type` of a reified share record in the given/received index — a resource, a
     * counterpart, one or more `acl:mode`s and a `dcterms:created` on one subject node.
     */
    public const val SHARE: String = "${NAMESPACE}Share"

    /** The shared resource IRI on a [SHARE] record. */
    public const val RESOURCE: String = "${NAMESPACE}resource"

    /** The receiver IRI on a given-share [SHARE] record: a WebID, a group, or `foaf:Agent`. */
    public const val RECEIVER: String = "${NAMESPACE}receiver"

    /** The owner WebID on a received-share [SHARE] record. */
    public const val OWNER: String = "${NAMESPACE}owner"

    /**
     * Links a `schema:Ticket` to its `schema:Reservation` envelope. schema.org only provides the
     * inverse (`schema:reservedTicket`), so this exists to spare readers a reverse scan.
     */
    public const val RESERVATION: String = "${NAMESPACE}reservation"

    /** Ticket category literal (a `TicketCategory` name, e.g. `"BUS"`). Derivable from the type. */
    public const val CATEGORY: String = "${NAMESPACE}category"

    /** Provenance literal on a ticket (a `TicketSource` name, e.g. `"PKPASS"`). */
    public const val SOURCE: String = "${NAMESPACE}source"

    /** Links a ticket to the original imported file it was built from, kept verbatim on the pod. */
    public const val ARTIFACT: String = "${NAMESPACE}artifact"

    /** Whether the artifact's issuer signature verified. */
    public const val ARTIFACT_VERIFIED: String = "${NAMESPACE}artifactVerified"

    /** Links a ticket to its stored logo image (pkpass `logo.png`, `primaryLogo.png`). */
    public const val LOGO_IMAGE: String = "${NAMESPACE}logoImage"

    /** Links a ticket to its stored icon image (pkpass `icon.png`). */
    public const val ICON_IMAGE: String = "${NAMESPACE}iconImage"

    /** Links a ticket to its stored strip image (pkpass `strip.png`). */
    public const val STRIP_IMAGE: String = "${NAMESPACE}stripImage"

    /** Links a ticket to its stored thumbnail image (pkpass `thumbnail.png`). */
    public const val THUMBNAIL_IMAGE: String = "${NAMESPACE}thumbnailImage"

    /** Links a ticket to its stored footer image (pkpass `footer.png`). */
    public const val FOOTER_IMAGE: String = "${NAMESPACE}footerImage"

    /** Links a ticket to its stored background image (pkpass `background.png`, `artwork.png`). */
    public const val BACKGROUND_IMAGE: String = "${NAMESPACE}backgroundImage"

    /**
     * `rdf:type` of a tickets index document — the wallet-list row cache a type index
     * `solid:instance` registration for `schema:Ticket` points at.
     */
    public const val TICKET_INDEX_CLASS: String = "${NAMESPACE}TicketIndex"

    /** The ticket has been redeemed or cancelled (pkpass `voided`). */
    public const val VOIDED: String = "${NAMESPACE}voided"

    /** The issuer's own pass serial number (pkpass `serialNumber`). */
    public const val SERIAL_NUMBER: String = "${NAMESPACE}serialNumber"

    /** Groups related passes, e.g. the legs of one trip (pkpass `groupingIdentifier`). */
    public const val GROUPING_IDENTIFIER: String = "${NAMESPACE}groupingIdentifier"

    /** Apple pass type identifier of the imported pass (pkpass `passTypeIdentifier`). */
    public const val PASS_TYPE_IDENTIFIER: String = "${NAMESPACE}passTypeIdentifier"

    /** Apple developer team identifier of the pass signer (pkpass `teamIdentifier`). */
    public const val TEAM_IDENTIFIER: String = "${NAMESPACE}teamIdentifier"

    /** The issuer's pass-update web service base URL (pkpass `webServiceURL`). */
    public const val WEB_SERVICE_URL: String = "${NAMESPACE}webServiceUrl"

    /** The authentication token for the pass-update web service. */
    public const val AUTHENTICATION_TOKEN: String = "${NAMESPACE}authenticationToken"

    /** Whether the issuer prohibits sharing this pass (pkpass `sharingProhibited`). */
    public const val SHARING_PROHIBITED: String = "${NAMESPACE}sharingProhibited"

    /** The start of the pass relevancy interval (pkpass `relevantDates[].startDate`). */
    public const val RELEVANT_START_DATE: String = "${NAMESPACE}relevantStartDate"

    /** The end of the pass relevancy interval (pkpass `relevantDates[].endDate`). */
    public const val RELEVANT_END_DATE: String = "${NAMESPACE}relevantEndDate"

    /** The issuing organisation's display name when no richer issuer node exists. */
    public const val ORGANIZATION_NAME: String = "${NAMESPACE}organizationName"

    /** Cached issuer display name on a tickets-index row. */
    public const val ISSUER: String = "${NAMESPACE}issuer"

    /** Links a ticket to a [BARCODE_CLASS] node. Repeatable — a pass may carry several. */
    public const val BARCODE: String = "${NAMESPACE}barcode"

    /** `rdf:type` of a barcode node. */
    public const val BARCODE_CLASS: String = "${NAMESPACE}Barcode"

    /** The exact payload the issuer encoded. Re-rendered verbatim so gate scanners agree. */
    public const val PAYLOAD: String = "${NAMESPACE}payload"

    /** The symbology of [PAYLOAD] (a `TicketBarcodeFormat` name, e.g. `"AZTEC"`). */
    public const val SYMBOLOGY: String = "${NAMESPACE}symbology"

    /** IANA charset of [PAYLOAD], e.g. `iso-8859-1`. Round-tripping it wrong corrupts the code. */
    public const val ENCODING: String = "${NAMESPACE}encoding"

    /** Human-readable fallback rendered beneath the barcode. */
    public const val ALT_TEXT: String = "${NAMESPACE}altText"

    /**
     * The payload is known to rotate server-side (Ticketmaster SafeTix, AXS). Such a token is a
     * snapshot that will not scan later, so the UI must say so rather than show a dead barcode.
     */
    public const val ROTATING: String = "${NAMESPACE}rotating"

    /**
     * The originally scheduled departure, frozen at issue. schema.org has no scheduled-vs-actual
     * pair anywhere — `Trip.departureTime` is single-valued — so the convention is that the
     * schema.org term holds the *live* time and this one preserves what was first promised.
     */
    public const val ORIGINAL_DEPARTURE_TIME: String = "${NAMESPACE}originalDepartureTime"

    /** The originally scheduled arrival, frozen at issue. */
    public const val ORIGINAL_ARRIVAL_TIME: String = "${NAMESPACE}originalArrivalTime"

    /** When boarding begins. `Event.doorTime` is Event-only; `Flight.webCheckinTime` is check-in. */
    public const val BOARDING_TIME: String = "${NAMESPACE}boardingTime"

    /** The originally scheduled boarding time, frozen at issue. */
    public const val ORIGINAL_BOARDING_TIME: String = "${NAMESPACE}originalBoardingTime"

    /** Live status of the journey, e.g. "On Time", "Delayed". */
    public const val TRANSIT_STATUS: String = "${NAMESPACE}transitStatus"

    /** Why [TRANSIT_STATUS] is what it is, e.g. "Thunderstorms". */
    public const val TRANSIT_STATUS_REASON: String = "${NAMESPACE}transitStatusReason"

    /** Departure platform for a bus or boat — `schema:BusTrip` has no platform or gate term. */
    public const val DEPARTURE_PLATFORM: String = "${NAMESPACE}departurePlatform"

    /** Arrival platform for a bus or boat. */
    public const val ARRIVAL_PLATFORM: String = "${NAMESPACE}arrivalPlatform"

    /** Name of the vehicle, e.g. a boat's name. */
    public const val VEHICLE_NAME: String = "${NAMESPACE}vehicleName"

    /** Vehicle identifier — aircraft registration, train-set number. */
    public const val VEHICLE_NUMBER: String = "${NAMESPACE}vehicleNumber"

    /** Vehicle type, e.g. the aircraft model. */
    public const val VEHICLE_TYPE: String = "${NAMESPACE}vehicleType"

    /** Rail carriage / car number (pkpass `carNumber`, UIC `coach`). */
    public const val COACH_NUMBER: String = "${NAMESPACE}coachNumber"

    /** Service brand, e.g. "ICE", "TGV" (UIC `serviceBrand`). */
    public const val SERVICE_BRAND: String = "${NAMESPACE}serviceBrand"

    /** Journey duration. `schema:Trip` has no duration term. */
    public const val DURATION: String = "${NAMESPACE}duration"

    /** Station/stop code where it is not an IATA airport code (UIC station code, carrier code). */
    public const val STOP_CODE: String = "${NAMESPACE}stopCode"

    /** Display city, distinct from the station name. */
    public const val CITY_NAME: String = "${NAMESPACE}cityName"

    /** tz database identifier, e.g. `Europe/Amsterdam` — needed to render a time correctly abroad. */
    public const val TIME_ZONE: String = "${NAMESPACE}timeZone"

    /** A security programme available at this stop, e.g. "TSA PreCheck". Repeatable. */
    public const val SECURITY_PROGRAM: String = "${NAMESPACE}securityProgram"

    /** Fare / booking class. `fare` returns zero hits across all of schema.org. */
    public const val FARE_CLASS: String = "${NAMESPACE}fareClass"

    /** BCBP compartment code (cabin class). */
    public const val COMPARTMENT_CODE: String = "${NAMESPACE}compartmentCode"

    /** UIC fare/tariff descriptor. */
    public const val TARIFF: String = "${NAMESPACE}tariff"

    /** UIC zone/line/region validity, for tickets that have no station pair. */
    public const val VALIDITY_REGION: String = "${NAMESPACE}validityRegion"

    /** Boarding zone (pkpass `boardingZone`). */
    public const val BOARDING_ZONE: String = "${NAMESPACE}boardingZone"

    /** BCBP passenger status code. */
    public const val PASSENGER_STATUS: String = "${NAMESPACE}passengerStatus"

    /** A BCBP baggage-tag licence-plate number. Repeatable. */
    public const val BAGGAGE_TAG: String = "${NAMESPACE}baggageTag"

    /** Free baggage allowance. */
    public const val BAGGAGE_ALLOWANCE: String = "${NAMESPACE}baggageAllowance"

    /** Priority-lane eligibility. */
    public const val FAST_TRACK: String = "${NAMESPACE}fastTrack"

    /** BCBP electronic-ticket indicator. */
    public const val ELECTRONIC_TICKET: String = "${NAMESPACE}electronicTicket"

    /** An IATA special-service-request code. Repeatable. */
    public const val SPECIAL_SERVICE_REQUEST: String = "${NAMESPACE}specialServiceRequest"

    /** A capability the passenger holds. Repeatable. */
    public const val PASSENGER_CAPABILITY: String = "${NAMESPACE}passengerCapability"

    /** The passenger's international documents have been verified. */
    public const val DOCUMENTS_VERIFIED: String = "${NAMESPACE}documentsVerified"

    /** Genre of the performance. `schema:Event` is not a `CreativeWork`, so it has no `genre`. */
    public const val GENRE: String = "${NAMESPACE}genre"

    /** Name of the sport. `SportsEvent.sport` is a pending schema.org term. */
    public const val SPORT_NAME: String = "${NAMESPACE}sportName"

    /** League name. No schema.org term exists. */
    public const val LEAGUE_NAME: String = "${NAMESPACE}leagueName"

    /** Abbreviated league name. */
    public const val LEAGUE_ABBREVIATION: String = "${NAMESPACE}leagueAbbreviation"

    /** Abbreviated team name, on a team node. */
    public const val TEAM_ABBREVIATION: String = "${NAMESPACE}teamAbbreviation"

    /** Admission level, e.g. "General Admission", "VIP". */
    public const val ADMISSION_LEVEL: String = "${NAMESPACE}admissionLevel"

    /** Abbreviated admission level, e.g. "GA", "VIP". */
    public const val ADMISSION_LEVEL_ABBREVIATION: String = "${NAMESPACE}admissionLevelAbbreviation"

    /** When the gates open. */
    public const val GATES_OPEN_TIME: String = "${NAMESPACE}gatesOpenTime"

    /** When the box office opens. */
    public const val BOX_OFFICE_OPEN_TIME: String = "${NAMESPACE}boxOfficeOpenTime"

    /** When the parking lots open. */
    public const val PARKING_OPEN_TIME: String = "${NAMESPACE}parkingOpenTime"

    /** When the fan zone opens. */
    public const val FAN_ZONE_OPEN_TIME: String = "${NAMESPACE}fanZoneOpenTime"

    /** When the venue opens. */
    public const val VENUE_OPEN_TIME: String = "${NAMESPACE}venueOpenTime"

    /** When the venue closes. */
    public const val VENUE_CLOSE_TIME: String = "${NAMESPACE}venueCloseTime"

    /** Whether tailgating is allowed. */
    public const val TAILGATING_ALLOWED: String = "${NAMESPACE}tailgatingAllowed"

    /** The event date is announced as TBA. */
    public const val DATE_UNANNOUNCED: String = "${NAMESPACE}dateUnannounced"

    /** The event date is TBD. */
    public const val DATE_UNDETERMINED: String = "${NAMESPACE}dateUndetermined"

    /** The room hosting the event. `schema:Place` has no such term. */
    public const val ROOM: String = "${NAMESPACE}room"

    /** The venue entrance, e.g. "Gate A". */
    public const val ENTRANCE: String = "${NAMESPACE}entrance"

    /** The venue entrance gate. */
    public const val ENTRANCE_GATE: String = "${NAMESPACE}entranceGate"

    /** The venue entrance door. */
    public const val ENTRANCE_DOOR: String = "${NAMESPACE}entranceDoor"

    /** The venue entrance portal. */
    public const val ENTRANCE_PORTAL: String = "${NAMESPACE}entrancePortal"

    /** City or hosting region of the venue. */
    public const val REGION_NAME: String = "${NAMESPACE}regionName"

    /** The seat's identifier code. */
    public const val SEAT_IDENTIFIER: String = "${NAMESPACE}seatIdentifier"

    /** The level containing the seat. */
    public const val SEAT_LEVEL: String = "${NAMESPACE}seatLevel"

    /** The aisle containing the seat. */
    public const val SEAT_AISLE: String = "${NAMESPACE}seatAisle"

    /** Description of the seat, e.g. "A flat bed seat". */
    public const val SEAT_DESCRIPTION: String = "${NAMESPACE}seatDescription"

    /** CSS rgb triple identifying the seat's section. */
    public const val SEAT_SECTION_COLOR: String = "${NAMESPACE}seatSectionColor"

    /** The coach containing this specific place (UIC). */
    public const val COACH: String = "${NAMESPACE}coach"

    /** Membership tier, e.g. "Gold". */
    public const val MEMBERSHIP_STATUS: String = "${NAMESPACE}membershipStatus"

    /**
     * A running points balance. schema.org's `membershipPointsEarned` is a pending term *and*
     * means points *earned*, not a balance, so it cannot carry this.
     */
    public const val POINTS_BALANCE: String = "${NAMESPACE}pointsBalance"

    /** A store card's redeemable monetary value. */
    public const val BALANCE: String = "${NAMESPACE}balance"

    /** ISO-4217 currency of [BALANCE]. */
    public const val BALANCE_CURRENCY: String = "${NAMESPACE}balanceCurrency"

    /** Links a ticket to its [PASS_STYLE_CLASS] node. */
    public const val STYLE: String = "${NAMESPACE}style"

    /** `rdf:type` of a pass-style node. */
    public const val PASS_STYLE_CLASS: String = "${NAMESPACE}PassStyle"

    /** Foreground colour, as a CSS rgb triple. */
    public const val FOREGROUND_COLOR: String = "${NAMESPACE}foregroundColor"

    /** Background colour, as a CSS rgb triple. */
    public const val BACKGROUND_COLOR: String = "${NAMESPACE}backgroundColor"

    /** Label colour, as a CSS rgb triple. */
    public const val LABEL_COLOR: String = "${NAMESPACE}labelColor"

    /** Text rendered next to the logo. */
    public const val LOGO_TEXT: String = "${NAMESPACE}logoText"

    /** The pass's strip background colour (pkpass `stripColor`). */
    public const val STRIP_COLOR: String = "${NAMESPACE}stripColor"

    /** The pass's footer background colour (pkpass `footerBackgroundColor`). */
    public const val FOOTER_BACKGROUND_COLOR: String = "${NAMESPACE}footerBackgroundColor"

    /** An SF Symbol name the issuer uses as a logo (pkpass `logoSymbolName`). */
    public const val LOGO_SYMBOL_NAME: String = "${NAMESPACE}logoSymbolName"

    /**
     * Links a ticket to a [DETAIL_CLASS] node. Repeatable.
     *
     * This is the guarantee that no extracted field is ever dropped: anything an importer reads
     * but cannot map to a term above — a pkpass back-field, an OCR'd PDF line, BCBP's
     * airline-individual-use blob — lands here verbatim rather than being discarded.
     */
    public const val DETAIL: String = "${NAMESPACE}detail"

    /** `rdf:type` of a detail node. */
    public const val DETAIL_CLASS: String = "${NAMESPACE}Detail"

    /** The alert format string shown when a detail's value changes (pkpass `changeMessage`). */
    public const val CHANGE_MESSAGE: String = "${NAMESPACE}changeMessage"

    /** The issuer's text alignment hint for a detail (pkpass `textAlignment`). */
    public const val TEXT_ALIGNMENT: String = "${NAMESPACE}textAlignment"

    /** A hyperlink carried by a detail's attributed value (pkpass `attributedValue`). */
    public const val LINK_URL: String = "${NAMESPACE}linkUrl"

    /** The detail's label. */
    public const val LABEL: String = "${NAMESPACE}label"

    /** The detail's value. */
    public const val VALUE: String = "${NAMESPACE}value"

    /** Where the issuer placed it: `HEADER PRIMARY SECONDARY AUXILIARY BACK ADDITIONAL`. */
    public const val PLACEMENT: String = "${NAMESPACE}placement"

    /** Render order within its placement. */
    public const val ORDER: String = "${NAMESPACE}order"

    /** Links a ticket to a [RELEVANT_LOCATION_CLASS] node. Repeatable. */
    public const val RELEVANT_LOCATION: String = "${NAMESPACE}relevantLocation"

    /** `rdf:type` of a relevance node. */
    public const val RELEVANT_LOCATION_CLASS: String = "${NAMESPACE}RelevantLocation"

    /** Radius in metres within which the pass is relevant. */
    public const val MAX_DISTANCE: String = "${NAMESPACE}maxDistance"

    /** Text shown when the pass surfaces. */
    public const val RELEVANT_TEXT: String = "${NAMESPACE}relevantText"

    /** When the pass should surface. */
    public const val RELEVANT_DATE: String = "${NAMESPACE}relevantDate"

    /** Links a ticket to a [WIFI_NETWORK_CLASS] node. Repeatable. */
    public const val WIFI_NETWORK: String = "${NAMESPACE}wifiNetwork"

    /** `rdf:type` of a Wi-Fi node. */
    public const val WIFI_NETWORK_CLASS: String = "${NAMESPACE}WifiNetwork"

    /** Wi-Fi network name. */
    public const val SSID: String = "${NAMESPACE}ssid"

    /** Wi-Fi password. */
    public const val PASSWORD: String = "${NAMESPACE}password"

    /** The device should stay silent for the duration of the event or journey. */
    public const val SILENCE_REQUESTED: String = "${NAMESPACE}silenceRequested"

    /** Links a ticket to a [BEACON_CLASS] node. Repeatable. */
    public const val BEACON: String = "${NAMESPACE}beacon"

    /** The class of a Bluetooth relevance beacon node (pkpass `beacons[]`). */
    public const val BEACON_CLASS: String = "${NAMESPACE}Beacon"

    /** The proximity UUID of a relevance beacon. */
    public const val PROXIMITY_UUID: String = "${NAMESPACE}proximityUuid"

    /** The major identifier of a relevance beacon. */
    public const val BEACON_MAJOR: String = "${NAMESPACE}beaconMajor"

    /** The minor identifier of a relevance beacon. */
    public const val BEACON_MINOR: String = "${NAMESPACE}beaconMinor"

    /** Phonetic representation of the holder's name (pkpass `phoneticRepresentation`). */
    public const val PHONETIC_NAME: String = "${NAMESPACE}phoneticName"

    /** The holder's nickname. */
    public const val NICKNAME: String = "${NAMESPACE}nickname"
}
