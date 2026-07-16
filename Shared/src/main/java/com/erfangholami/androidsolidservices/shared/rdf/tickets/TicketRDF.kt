package com.erfangholami.androidsolidservices.shared.rdf.tickets

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.tickets.DetailPlacement
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcode
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcodeFormat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBeacon
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketDetail
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEvent
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketEventStatus
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketGeo
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketJourney
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketMembership
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketOrganization
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPerson
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketPlace
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketRelevantLocation
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketReservation
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketReservationStatus
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSeat
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSource
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStop
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketStyle
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketWifi
import com.erfangholami.androidsolidservices.shared.model.tickets.TransportMode
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.SolidShare
import com.erfangholami.androidsolidservices.shared.vocab.XSD

/**
 * RDF codec for a single wallet ticket (`schema:Ticket`).
 *
 * The document's primary subject `#this` is always a `schema:Ticket`. Everything the ticket admits
 * to hangs off a reservation envelope, and the rest of the pass lives on sibling fragment nodes of
 * the same document — see `documents/TICKET_VOCAB.md` for the full shape and the justification for
 * every minted `solidshare:` term.
 *
 * ```
 * #this        schema:Ticket
 * ├─ #reservation  schema:*Reservation  →(reservationFor) #event | #trip
 * │  #trip         schema:Flight | TrainTrip | BusTrip | BoatTrip  →(dep/arr) #from / #to
 * │  #event        schema:Event | subtype  →(location) #venue
 * ├─ #seat{n}, #barcode{n}, #detail{n}, #rel{n}, #wifi{n}
 * └─ #issuer, #holder, #membership, #style, #carrier
 * ```
 *
 * Reads follow the RDF links rather than fixed node names, so the reservation, trip and event are
 * discovered through `solidshare:reservation` / `schema:reservationFor` regardless of how the nodes
 * were named.
 */
public class TicketRDF : SolidRDFResource {

    public constructor(
        identifier: String,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null,
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    init {
        ensureType(getIdentifier(), Schema.TICKET)
    }

    private val self: String get() = getIdentifier()
    private val doc: String get() = self.substringBefore('#')

    private val reservationNode get() = "$doc#reservation"
    private val tripNode get() = "$doc#trip"
    private val fromNode get() = "$doc#from"
    private val toNode get() = "$doc#to"
    private val eventNode get() = "$doc#event"
    private val venueNode get() = "$doc#venue"
    private val issuerNode get() = "$doc#issuer"
    private val holderNode get() = "$doc#holder"
    private val membershipNode get() = "$doc#membership"
    private val styleNode get() = "$doc#style"
    private val carrierNode get() = "$doc#carrier"
    private fun seatNode(i: Int) = "$doc#seat$i"
    private fun barcodeNode(i: Int) = "$doc#barcode$i"
    private fun detailNode(i: Int) = "$doc#detail$i"
    private fun relevanceNode(i: Int) = "$doc#rel$i"
    private fun wifiNode(i: Int) = "$doc#wifi$i"
    private fun beaconNode(i: Int) = "$doc#beacon$i"

    // ---- Public convenience accessors (used by the index and the delete shell) ----------------

    /** The display title (`schema:name`); `""` when absent, so a throwaway shell never crashes. */
    public fun getTitle(): String = str(self, Schema.NAME) ?: ""

    /** Sets the display title. */
    public fun setTitle(title: String) {
        putStr(self, Schema.NAME, title)
    }

    /** The pass category, tolerating unknown stored values as [TicketCategory.GENERIC]. */
    public fun getCategory(): TicketCategory =
        enumOrDefault(str(self, SolidShare.CATEGORY), TicketCategory.GENERIC)

    /** The issuing organisation's display name, from the issuer node or [organizationName]. */
    public fun getIssuerName(): String? =
        follow(self, Schema.ISSUED_BY)?.let { str(it, Schema.NAME) }
            ?: str(self, SolidShare.ORGANIZATION_NAME)

    /** The validity end (`schema:validThrough`, ISO-8601), or `null`. */
    public fun getValidThrough(): String? = str(self, Schema.VALID_THROUGH)

    /** The pass's presentation (colours, logo), or `null` when none was stored. */
    public fun getStyle(): TicketStyle? = readStyle()

    /** The date a wallet list should sort by: the event start, or a journey's departure. */
    public fun getIndexStartDate(): String? =
        reservationForTargets().firstNotNullOfOrNull { node ->
            str(node, Schema.START_DATE) ?: str(node, Schema.DEPARTURE_TIME)
        }

    /** The pod URI of the original imported artifact (`solidshare:artifact`), or `null`. */
    public fun getArtifactUri(): String? = str(self, SolidShare.ARTIFACT)

    /** Sets (or clears, when `null`) the artifact link. */
    public fun setArtifactUri(artifactUri: String?) {
        clearProperties(SolidShare.ARTIFACT, self)
        if (!artifactUri.isNullOrBlank()) addQuad(self, SolidShare.ARTIFACT, artifactUri)
    }

    /** Whether the artifact's issuer signature verified (`solidshare:artifactVerified`), or `null`. */
    public fun getArtifactVerified(): Boolean? = bool(self, SolidShare.ARTIFACT_VERIFIED)

    /** Sets (or clears, when `null`) the artifact-verified flag. */
    public fun setArtifactVerified(verified: Boolean?) {
        clearProperties(SolidShare.ARTIFACT_VERIFIED, self)
        putBool(self, SolidShare.ARTIFACT_VERIFIED, verified)
    }

    /** The creation timestamp (`dcterms:created`, ISO-8601), or `null`. */
    public fun getCreated(): String? = str(self, DC.CREATED)

    /** Sets the creation timestamp (ISO-8601). */
    public fun setCreated(isoDateTime: String) {
        addQuadLiteral(self, DC.CREATED, isoDateTime, XSD.DATE_TIME)
    }

    /** The last-modification timestamp (`dcterms:modified`, ISO-8601), or `null`. */
    public fun getModified(): String? = str(self, DC.MODIFIED)

    /** Sets the last-modification timestamp (ISO-8601). */
    public fun setModified(isoDateTime: String) {
        addQuadLiteral(self, DC.MODIFIED, isoDateTime, XSD.DATE_TIME)
    }

    // ---- Write --------------------------------------------------------------------------------

    /**
     * Rewrites this ticket's writable state from [data] with replace semantics: every fragment
     * node and every optional property is replaced by the snapshot's content. Only the type
     * triple, `dcterms:created`, `solidshare:artifact` and `solidshare:artifactVerified` survive —
     * those belong to the resource lifecycle, not the ticket's editable content.
     */
    public fun setTicketData(data: NewTicket) {
        require(data.title.isNotBlank()) { "A ticket needs a non-blank title" }
        val preserved = setOf(DC.CREATED, SolidShare.ARTIFACT, SolidShare.ARTIFACT_VERIFIED)
        quads.retainAll { q ->
            q.subject == self &&
                ((q.predicate == RDF.TYPE && q.`object` == Schema.TICKET) || q.predicate in preserved)
        }

        putStr(self, Schema.NAME, data.title)
        putStr(self, Schema.DESCRIPTION, data.description)
        putStr(self, Schema.TICKET_NUMBER, data.ticketNumber)
        putEnum(self, SolidShare.CATEGORY, data.category)
        putEnum(self, SolidShare.SOURCE, data.source)
        putStr(self, Schema.TOTAL_PRICE, data.totalPrice)
        putStr(self, Schema.PRICE_CURRENCY, data.priceCurrency)
        putDate(self, Schema.DATE_ISSUED, data.dateIssued)
        putDate(self, Schema.VALID_FROM, data.validFrom)
        putDate(self, Schema.VALID_THROUGH, data.validThrough)
        putDate(self, SolidShare.RELEVANT_DATE, data.relevantDate)
        putBool(self, SolidShare.SILENCE_REQUESTED, data.silenceRequested)
        putBool(self, SolidShare.VOIDED, data.voided)
        putStr(self, SolidShare.SERIAL_NUMBER, data.serialNumber)
        putStr(self, SolidShare.GROUPING_IDENTIFIER, data.groupingIdentifier)
        putStr(self, SolidShare.ORGANIZATION_NAME, data.organizationName)
        putStr(self, SolidShare.PASS_TYPE_IDENTIFIER, data.passTypeIdentifier)
        putStr(self, SolidShare.TEAM_IDENTIFIER, data.teamIdentifier)
        putStr(self, SolidShare.WEB_SERVICE_URL, data.webServiceUrl)
        putStr(self, SolidShare.AUTHENTICATION_TOKEN, data.authenticationToken)
        putBool(self, SolidShare.SHARING_PROHIBITED, data.sharingProhibited)
        putDate(self, SolidShare.RELEVANT_START_DATE, data.relevantStartDate)
        putDate(self, SolidShare.RELEVANT_END_DATE, data.relevantEndDate)

        writeBarcodes(data.barcodes)
        writeSeats(data.seats)
        writeIssuer(data.issuer)
        writeHolder(data.holder)
        writeMembership(data.membership)
        writeStyle(data.style)
        writeDetails(data.details)
        writeRelevantLocations(data.relevantLocations)
        writeWifi(data.wifiNetworks)
        writeBeacons(data.beacons)

        if (data.event != null) writeEvent(data.event)
        if (data.journey != null) writeJourney(data.journey)
        writeReservation(data)
    }

    private fun writeBarcodes(barcodes: List<TicketBarcode>) {
        val present = barcodes.filter { it.payload.isNotBlank() }
        present.forEachIndexed { i, bc ->
            val node = barcodeNode(i)
            addQuad(self, SolidShare.BARCODE, node, maxNumber = Int.MAX_VALUE)
            addQuad(node, RDF.TYPE, SolidShare.BARCODE_CLASS)
            putStr(node, SolidShare.PAYLOAD, bc.payload)
            putEnum(node, SolidShare.SYMBOLOGY, bc.symbology)
            putStr(node, SolidShare.ENCODING, bc.encoding)
            putStr(node, SolidShare.ALT_TEXT, bc.altText)
            if (bc.rotating) putBool(node, SolidShare.ROTATING, true)
        }
        // Mirror the first barcode's payload into schema:ticketToken for schema.org consumers.
        present.firstOrNull()?.let { putStr(self, Schema.TICKET_TOKEN, it.payload) }
    }

    private fun writeSeats(seats: List<TicketSeat>) {
        seats.filterNot { it.isEmpty() }.forEachIndexed { i, seat ->
            val node = seatNode(i)
            addQuad(self, Schema.TICKETED_SEAT, node, maxNumber = Int.MAX_VALUE)
            addQuad(node, RDF.TYPE, Schema.SEAT)
            putStr(node, Schema.SEAT_NUMBER, seat.seatNumber)
            putStr(node, Schema.SEAT_ROW, seat.seatRow)
            putStr(node, Schema.SEAT_SECTION, seat.seatSection)
            putStr(node, Schema.SEATING_TYPE, seat.seatingType)
            putStr(node, SolidShare.SEAT_IDENTIFIER, seat.seatIdentifier)
            putStr(node, SolidShare.SEAT_LEVEL, seat.seatLevel)
            putStr(node, SolidShare.SEAT_AISLE, seat.seatAisle)
            putStr(node, SolidShare.SEAT_DESCRIPTION, seat.seatDescription)
            putStr(node, SolidShare.SEAT_SECTION_COLOR, seat.seatSectionColor)
            putStr(node, SolidShare.COACH, seat.coach)
        }
    }

    private fun writeIssuer(issuer: TicketOrganization?) {
        if (issuer == null || issuer.isEmpty()) return
        addQuad(self, Schema.ISSUED_BY, issuerNode)
        addQuad(issuerNode, RDF.TYPE, if (issuer.iataCode != null) Schema.AIRLINE else Schema.ORGANIZATION)
        putStr(issuerNode, Schema.NAME, issuer.name)
        putIri(issuerNode, Schema.LOGO, issuer.logoUri)
        putIri(issuerNode, Schema.URL, issuer.url)
        putStr(issuerNode, Schema.TELEPHONE, issuer.telephone)
        putStr(issuerNode, Schema.EMAIL, issuer.email)
        putStr(issuerNode, Schema.IATA_CODE, issuer.iataCode)
    }

    private fun writeHolder(holder: TicketPerson?) {
        if (holder == null || holder.isEmpty()) return
        addQuad(self, Schema.UNDER_NAME, holderNode)
        addQuad(holderNode, RDF.TYPE, Schema.PERSON)
        putStr(holderNode, Schema.NAME, holder.name)
        putStr(holderNode, Schema.GIVEN_NAME, holder.givenName)
        putStr(holderNode, Schema.FAMILY_NAME, holder.familyName)
        putStr(holderNode, Schema.ADDITIONAL_NAME, holder.additionalName)
        putStr(holderNode, Schema.HONORIFIC_PREFIX, holder.honorificPrefix)
        putStr(holderNode, Schema.HONORIFIC_SUFFIX, holder.honorificSuffix)
        putStr(holderNode, SolidShare.PHONETIC_NAME, holder.phoneticName)
        putStr(holderNode, SolidShare.NICKNAME, holder.nickname)
    }

    private fun writeMembership(m: TicketMembership?) {
        if (m == null || m.isEmpty()) return
        addQuad(self, Schema.PROGRAM_MEMBERSHIP_USED, membershipNode)
        addQuad(membershipNode, RDF.TYPE, Schema.PROGRAM_MEMBERSHIP)
        putStr(membershipNode, Schema.PROGRAM_NAME, m.programName)
        putStr(membershipNode, Schema.MEMBERSHIP_NUMBER, m.membershipNumber)
        putStr(membershipNode, SolidShare.MEMBERSHIP_STATUS, m.membershipStatus)
        putStr(membershipNode, SolidShare.POINTS_BALANCE, m.pointsBalance)
        putStr(membershipNode, SolidShare.BALANCE, m.balance)
        putStr(membershipNode, SolidShare.BALANCE_CURRENCY, m.balanceCurrency)
    }

    private fun writeStyle(s: TicketStyle?) {
        if (s == null || s.isEmpty()) return
        addQuad(self, SolidShare.STYLE, styleNode)
        addQuad(styleNode, RDF.TYPE, SolidShare.PASS_STYLE_CLASS)
        putStr(styleNode, SolidShare.FOREGROUND_COLOR, s.foregroundColor)
        putStr(styleNode, SolidShare.BACKGROUND_COLOR, s.backgroundColor)
        putStr(styleNode, SolidShare.LABEL_COLOR, s.labelColor)
        putStr(styleNode, SolidShare.LOGO_TEXT, s.logoText)
        putIri(styleNode, SolidShare.LOGO_IMAGE, s.logoImage)
        putIri(styleNode, SolidShare.ICON_IMAGE, s.iconImage)
        putIri(styleNode, SolidShare.STRIP_IMAGE, s.stripImage)
        putIri(styleNode, SolidShare.THUMBNAIL_IMAGE, s.thumbnailImage)
        putIri(styleNode, SolidShare.BACKGROUND_IMAGE, s.backgroundImage)
        putIri(styleNode, SolidShare.FOOTER_IMAGE, s.footerImage)
        putStr(styleNode, SolidShare.STRIP_COLOR, s.stripColor)
        putStr(styleNode, SolidShare.FOOTER_BACKGROUND_COLOR, s.footerBackgroundColor)
        putStr(styleNode, SolidShare.LOGO_SYMBOL_NAME, s.logoSymbolName)
    }

    private fun writeDetails(details: List<TicketDetail>) {
        details.filterNot { it.label == null && it.value == null }.forEachIndexed { i, d ->
            val node = detailNode(i)
            addQuad(self, SolidShare.DETAIL, node, maxNumber = Int.MAX_VALUE)
            addQuad(node, RDF.TYPE, SolidShare.DETAIL_CLASS)
            putStr(node, SolidShare.LABEL, d.label)
            putStr(node, SolidShare.VALUE, d.value)
            putEnum(node, SolidShare.PLACEMENT, d.placement)
            putInt(node, SolidShare.ORDER, d.order)
            putStr(node, SolidShare.CHANGE_MESSAGE, d.changeMessage)
            putStr(node, SolidShare.TEXT_ALIGNMENT, d.textAlignment)
            putStr(node, SolidShare.LINK_URL, d.linkUrl)
        }
    }

    private fun writeRelevantLocations(locations: List<TicketRelevantLocation>) {
        locations.filterNot { it.isEmpty() }.forEachIndexed { i, loc ->
            val node = relevanceNode(i)
            addQuad(self, SolidShare.RELEVANT_LOCATION, node, maxNumber = Int.MAX_VALUE)
            addQuad(node, RDF.TYPE, SolidShare.RELEVANT_LOCATION_CLASS)
            writeGeo(node, "${node}Geo", loc.geo)
            putInt(node, SolidShare.MAX_DISTANCE, loc.maxDistance)
            putStr(node, SolidShare.RELEVANT_TEXT, loc.relevantText)
        }
    }

    private fun writeBeacons(beacons: List<TicketBeacon>) {
        beacons.filterNot { it.proximityUuid == null && it.relevantText == null }.forEachIndexed { i, b ->
            val node = beaconNode(i)
            addQuad(self, SolidShare.BEACON, node, maxNumber = Int.MAX_VALUE)
            addQuad(node, RDF.TYPE, SolidShare.BEACON_CLASS)
            putStr(node, SolidShare.PROXIMITY_UUID, b.proximityUuid)
            putInt(node, SolidShare.BEACON_MAJOR, b.major)
            putInt(node, SolidShare.BEACON_MINOR, b.minor)
            putStr(node, SolidShare.RELEVANT_TEXT, b.relevantText)
        }
    }

    private fun writeWifi(networks: List<TicketWifi>) {
        networks.filterNot { it.ssid == null && it.password == null }.forEachIndexed { i, w ->
            val node = wifiNode(i)
            addQuad(self, SolidShare.WIFI_NETWORK, node, maxNumber = Int.MAX_VALUE)
            addQuad(node, RDF.TYPE, SolidShare.WIFI_NETWORK_CLASS)
            putStr(node, SolidShare.SSID, w.ssid)
            putStr(node, SolidShare.PASSWORD, w.password)
        }
    }

    private fun writeEvent(e: TicketEvent) {
        addQuad(eventNode, RDF.TYPE, eventTypeFor(e))
        putStr(eventNode, Schema.NAME, e.name)
        putDate(eventNode, Schema.START_DATE, e.startDate)
        putDate(eventNode, Schema.END_DATE, e.endDate)
        putDate(eventNode, Schema.DOOR_TIME, e.doorTime)
        e.status?.let { addQuad(eventNode, Schema.EVENT_STATUS, eventStatusIri(it)) }
        putDate(eventNode, Schema.PREVIOUS_START_DATE, e.previousStartDate)
        putDuration(eventNode, SolidShare.DURATION, e.duration)
        e.performers.forEach { addQuadLiteral(eventNode, Schema.PERFORMER, it, XSD.STRING, maxNumber = Int.MAX_VALUE) }
        putStr(eventNode, Schema.ORGANIZER, e.organizer)
        putStr(eventNode, SolidShare.GENRE, e.genre)
        putStr(eventNode, SolidShare.SPORT_NAME, e.sportName)
        putStr(eventNode, SolidShare.LEAGUE_NAME, e.leagueName)
        putStr(eventNode, SolidShare.LEAGUE_ABBREVIATION, e.leagueAbbreviation)
        putStr(eventNode, Schema.HOME_TEAM, e.homeTeam)
        putStr(eventNode, Schema.AWAY_TEAM, e.awayTeam)
        putStr(eventNode, SolidShare.ADMISSION_LEVEL, e.admissionLevel)
        putStr(eventNode, SolidShare.ADMISSION_LEVEL_ABBREVIATION, e.admissionLevelAbbreviation)
        putDate(eventNode, SolidShare.GATES_OPEN_TIME, e.gatesOpenTime)
        putDate(eventNode, SolidShare.BOX_OFFICE_OPEN_TIME, e.boxOfficeOpenTime)
        putDate(eventNode, SolidShare.PARKING_OPEN_TIME, e.parkingOpenTime)
        putDate(eventNode, SolidShare.FAN_ZONE_OPEN_TIME, e.fanZoneOpenTime)
        putDate(eventNode, SolidShare.VENUE_OPEN_TIME, e.venueOpenTime)
        putDate(eventNode, SolidShare.VENUE_CLOSE_TIME, e.venueCloseTime)
        putBool(eventNode, SolidShare.TAILGATING_ALLOWED, e.tailgatingAllowed)
        putBool(eventNode, SolidShare.DATE_UNANNOUNCED, e.dateUnannounced)
        putBool(eventNode, SolidShare.DATE_UNDETERMINED, e.dateUndetermined)
        writeVenue(e.location)
    }

    private fun writeVenue(v: TicketPlace?) {
        if (v == null || v.isEmpty()) return
        addQuad(eventNode, Schema.LOCATION, venueNode)
        addQuad(venueNode, RDF.TYPE, Schema.PLACE)
        putStr(venueNode, Schema.NAME, v.name)
        putStr(venueNode, Schema.ADDRESS, v.address)
        writeGeo(venueNode, "${venueNode}Geo", v.geo)
        putStr(venueNode, Schema.TELEPHONE, v.telephone)
        putStr(venueNode, SolidShare.ROOM, v.room)
        putStr(venueNode, SolidShare.ENTRANCE, v.entrance)
        putStr(venueNode, SolidShare.ENTRANCE_GATE, v.entranceGate)
        putStr(venueNode, SolidShare.ENTRANCE_DOOR, v.entranceDoor)
        putStr(venueNode, SolidShare.ENTRANCE_PORTAL, v.entrancePortal)
        putStr(venueNode, SolidShare.REGION_NAME, v.regionName)
    }

    /**
     * Writes the journey. Per-mode predicates are the fiddly part: the service number/name and the
     * stop links differ by mode, and boats have no schema.org service term at all — so a boat's
     * [TicketJourney.serviceNumber] / [TicketJourney.serviceName] are stored in
     * `solidshare:vehicleNumber` / `solidshare:vehicleName` (a ferry's "vehicle" is its service).
     * Gate and terminal are always `schema:` (Flight-domain, but non-constraining); platform is
     * `schema:` for rail and `solidshare:` otherwise, since schema.org's platform is TrainTrip-only.
     */
    private fun writeJourney(j: TicketJourney) {
        addQuad(tripNode, RDF.TYPE, tripTypeFor(j.mode))
        when (j.mode) {
            TransportMode.FLIGHT -> {
                putStr(tripNode, Schema.FLIGHT_NUMBER, j.serviceNumber)
                putStr(tripNode, SolidShare.VEHICLE_NAME, j.vehicleName)
                putStr(tripNode, SolidShare.VEHICLE_NUMBER, j.vehicleNumber)
            }
            TransportMode.TRAIN -> {
                putStr(tripNode, Schema.TRAIN_NUMBER, j.serviceNumber)
                putStr(tripNode, Schema.TRAIN_NAME, j.serviceName)
                putStr(tripNode, SolidShare.VEHICLE_NAME, j.vehicleName)
                putStr(tripNode, SolidShare.VEHICLE_NUMBER, j.vehicleNumber)
            }
            TransportMode.BUS -> {
                putStr(tripNode, Schema.BUS_NUMBER, j.serviceNumber)
                putStr(tripNode, Schema.BUS_NAME, j.serviceName)
                putStr(tripNode, SolidShare.VEHICLE_NAME, j.vehicleName)
                putStr(tripNode, SolidShare.VEHICLE_NUMBER, j.vehicleNumber)
            }
            TransportMode.BOAT -> {
                putStr(tripNode, SolidShare.VEHICLE_NUMBER, j.serviceNumber)
                putStr(tripNode, SolidShare.VEHICLE_NAME, j.serviceName)
            }
        }
        j.carrierName?.takeIf { it.isNotBlank() }?.let {
            addQuad(tripNode, Schema.PROVIDER, carrierNode)
            addQuad(carrierNode, RDF.TYPE, Schema.ORGANIZATION)
            putStr(carrierNode, Schema.NAME, it)
        }
        putDate(tripNode, Schema.DEPARTURE_TIME, j.departureTime)
        putDate(tripNode, Schema.ARRIVAL_TIME, j.arrivalTime)
        putDate(tripNode, SolidShare.ORIGINAL_DEPARTURE_TIME, j.originalDepartureTime)
        putDate(tripNode, SolidShare.ORIGINAL_ARRIVAL_TIME, j.originalArrivalTime)
        putDate(tripNode, SolidShare.BOARDING_TIME, j.boardingTime)
        putDate(tripNode, SolidShare.ORIGINAL_BOARDING_TIME, j.originalBoardingTime)
        putStr(tripNode, SolidShare.TRANSIT_STATUS, j.transitStatus)
        putStr(tripNode, SolidShare.TRANSIT_STATUS_REASON, j.transitStatusReason)
        putStr(tripNode, SolidShare.VEHICLE_TYPE, j.vehicleType)
        putStr(tripNode, SolidShare.COACH_NUMBER, j.coachNumber)
        putStr(tripNode, SolidShare.SERVICE_BRAND, j.serviceBrand)
        putDuration(tripNode, SolidShare.DURATION, j.duration)
        putStr(tripNode, Schema.AIRCRAFT, j.aircraft)
        putStr(tripNode, Schema.BOARDING_POLICY, j.boardingPolicy)
        writeStop(j.mode, j.departure, isDeparture = true)
        writeStop(j.mode, j.arrival, isDeparture = false)
    }

    private fun writeStop(mode: TransportMode, stop: TicketStop?, isDeparture: Boolean) {
        // Gate/terminal/platform are the journey's, so they go on the trip node even if the place is null.
        putStr(tripNode, if (isDeparture) Schema.DEPARTURE_GATE else Schema.ARRIVAL_GATE, stop?.gate)
        putStr(tripNode, if (isDeparture) Schema.DEPARTURE_TERMINAL else Schema.ARRIVAL_TERMINAL, stop?.terminal)
        val platformPredicate = when {
            mode == TransportMode.TRAIN && isDeparture -> Schema.DEPARTURE_PLATFORM
            mode == TransportMode.TRAIN -> Schema.ARRIVAL_PLATFORM
            isDeparture -> SolidShare.DEPARTURE_PLATFORM
            else -> SolidShare.ARRIVAL_PLATFORM
        }
        putStr(tripNode, platformPredicate, stop?.platform)
        if (stop == null || stop.isPlaceEmpty()) return
        val node = if (isDeparture) fromNode else toNode
        addQuad(tripNode, stopLink(mode, isDeparture), node)
        addQuad(node, RDF.TYPE, stopType(mode))
        putStr(node, Schema.NAME, stop.name)
        putStr(node, Schema.IATA_CODE, stop.iataCode)
        putStr(node, SolidShare.STOP_CODE, stop.code)
        putStr(node, SolidShare.CITY_NAME, stop.cityName)
        putStr(node, SolidShare.TIME_ZONE, stop.timeZone)
        putStr(node, Schema.ADDRESS, stop.address)
        writeGeo(node, "${node}Geo", stop.geo)
        stop.securityPrograms.forEach {
            addQuadLiteral(node, SolidShare.SECURITY_PROGRAM, it, XSD.STRING, maxNumber = Int.MAX_VALUE)
        }
    }

    private fun writeReservation(data: NewTicket) {
        val type = reservationTypeFor(data) ?: return
        addQuad(self, SolidShare.RESERVATION, reservationNode)
        addQuad(reservationNode, RDF.TYPE, type)
        addQuad(reservationNode, Schema.RESERVED_TICKET, self)
        val forNode = when {
            data.event != null -> eventNode
            data.journey != null -> tripNode
            else -> null
        }
        forNode?.let { addQuad(reservationNode, Schema.RESERVATION_FOR, it) }

        val r = data.reservation ?: return
        r.status?.let { addQuad(reservationNode, Schema.RESERVATION_STATUS, reservationStatusIri(it)) }
        putStr(reservationNode, Schema.RESERVATION_ID, r.bookingReference)
        putDate(reservationNode, Schema.BOOKING_TIME, r.bookingTime)
        putDate(reservationNode, Schema.MODIFIED_TIME, r.modifiedTime)
        putStr(reservationNode, SolidShare.FARE_CLASS, r.fareClass)
        putStr(reservationNode, Schema.BOARDING_GROUP, r.boardingGroup)
        putStr(reservationNode, SolidShare.BOARDING_ZONE, r.boardingZone)
        putStr(reservationNode, Schema.PASSENGER_SEQUENCE_NUMBER, r.passengerSequenceNumber)
        putStr(reservationNode, Schema.PASSENGER_PRIORITY_STATUS, r.passengerPriorityStatus)
        putStr(reservationNode, Schema.SECURITY_SCREENING, r.securityScreening)
        putStr(reservationNode, SolidShare.COMPARTMENT_CODE, r.compartmentCode)
        putStr(reservationNode, SolidShare.PASSENGER_STATUS, r.passengerStatus)
        r.baggageTags.forEach {
            addQuadLiteral(reservationNode, SolidShare.BAGGAGE_TAG, it, XSD.STRING, maxNumber = Int.MAX_VALUE)
        }
        putStr(reservationNode, SolidShare.BAGGAGE_ALLOWANCE, r.baggageAllowance)
        putBool(reservationNode, SolidShare.FAST_TRACK, r.fastTrack)
        putBool(reservationNode, SolidShare.ELECTRONIC_TICKET, r.electronicTicket)
        r.specialServiceRequests.forEach {
            addQuadLiteral(reservationNode, SolidShare.SPECIAL_SERVICE_REQUEST, it, XSD.STRING, maxNumber = Int.MAX_VALUE)
        }
        r.passengerCapabilities.forEach {
            addQuadLiteral(reservationNode, SolidShare.PASSENGER_CAPABILITY, it, XSD.STRING, maxNumber = Int.MAX_VALUE)
        }
        putBool(reservationNode, SolidShare.DOCUMENTS_VERIFIED, r.documentsVerified)
        putStr(reservationNode, SolidShare.TARIFF, r.tariff)
        putStr(reservationNode, SolidShare.VALIDITY_REGION, r.validityRegion)
        putDate(reservationNode, Schema.CHECKIN_TIME, r.checkinTime)
        putDate(reservationNode, Schema.CHECKOUT_TIME, r.checkoutTime)
        putInt(reservationNode, Schema.NUM_ADULTS, r.numAdults)
        putInt(reservationNode, Schema.NUM_CHILDREN, r.numChildren)
    }

    // ---- Read ---------------------------------------------------------------------------------

    /** Reads this ticket's complete writable state into a [NewTicket] snapshot. */
    public fun toNewTicket(): NewTicket {
        val category = getCategory()
        return NewTicket(
            title = getTitle(),
            description = str(self, Schema.DESCRIPTION),
            ticketNumber = str(self, Schema.TICKET_NUMBER),
            category = category,
            source = enumOrDefault(str(self, SolidShare.SOURCE), TicketSource.MANUAL),
            issuer = readIssuer(),
            holder = readHolder(),
            seats = readSeats(),
            barcodes = readBarcodes(),
            totalPrice = str(self, Schema.TOTAL_PRICE),
            priceCurrency = str(self, Schema.PRICE_CURRENCY),
            dateIssued = str(self, Schema.DATE_ISSUED),
            validFrom = str(self, Schema.VALID_FROM),
            validThrough = str(self, Schema.VALID_THROUGH),
            reservation = readReservation(),
            journey = readJourney(category),
            event = readEvent(),
            membership = readMembership(),
            style = readStyle(),
            details = readDetails(),
            relevantLocations = readRelevantLocations(),
            relevantDate = str(self, SolidShare.RELEVANT_DATE),
            wifiNetworks = readWifi(),
            silenceRequested = bool(self, SolidShare.SILENCE_REQUESTED),
            voided = bool(self, SolidShare.VOIDED),
            serialNumber = str(self, SolidShare.SERIAL_NUMBER),
            groupingIdentifier = str(self, SolidShare.GROUPING_IDENTIFIER),
            organizationName = str(self, SolidShare.ORGANIZATION_NAME),
            passTypeIdentifier = str(self, SolidShare.PASS_TYPE_IDENTIFIER),
            teamIdentifier = str(self, SolidShare.TEAM_IDENTIFIER),
            webServiceUrl = str(self, SolidShare.WEB_SERVICE_URL),
            authenticationToken = str(self, SolidShare.AUTHENTICATION_TOKEN),
            sharingProhibited = bool(self, SolidShare.SHARING_PROHIBITED),
            relevantStartDate = str(self, SolidShare.RELEVANT_START_DATE),
            relevantEndDate = str(self, SolidShare.RELEVANT_END_DATE),
            beacons = readBeacons(),
        )
    }

    private fun readBarcodes(): List<TicketBarcode> =
        followAll(self, SolidShare.BARCODE).mapNotNull { node ->
            val payload = str(node, SolidShare.PAYLOAD) ?: return@mapNotNull null
            TicketBarcode(
                payload = payload,
                symbology = enumOrDefault(str(node, SolidShare.SYMBOLOGY), TicketBarcodeFormat.QR_CODE),
                encoding = str(node, SolidShare.ENCODING),
                altText = str(node, SolidShare.ALT_TEXT),
                rotating = bool(node, SolidShare.ROTATING) ?: false,
            )
        }

    private fun readSeats(): List<TicketSeat> =
        followAll(self, Schema.TICKETED_SEAT).mapNotNull { node ->
            TicketSeat(
                seatNumber = str(node, Schema.SEAT_NUMBER),
                seatRow = str(node, Schema.SEAT_ROW),
                seatSection = str(node, Schema.SEAT_SECTION),
                seatingType = str(node, Schema.SEATING_TYPE),
                seatIdentifier = str(node, SolidShare.SEAT_IDENTIFIER),
                seatLevel = str(node, SolidShare.SEAT_LEVEL),
                seatAisle = str(node, SolidShare.SEAT_AISLE),
                seatDescription = str(node, SolidShare.SEAT_DESCRIPTION),
                seatSectionColor = str(node, SolidShare.SEAT_SECTION_COLOR),
                coach = str(node, SolidShare.COACH),
            ).takeUnless { it.isEmpty() }
        }

    private fun readIssuer(): TicketOrganization? {
        val node = follow(self, Schema.ISSUED_BY) ?: return null
        return TicketOrganization(
            name = str(node, Schema.NAME),
            logoUri = str(node, Schema.LOGO),
            url = str(node, Schema.URL),
            telephone = str(node, Schema.TELEPHONE),
            email = str(node, Schema.EMAIL),
            iataCode = str(node, Schema.IATA_CODE),
        ).takeUnless { it.isEmpty() }
    }

    private fun readHolder(): TicketPerson? {
        val node = follow(self, Schema.UNDER_NAME) ?: return null
        return TicketPerson(
            name = str(node, Schema.NAME),
            givenName = str(node, Schema.GIVEN_NAME),
            familyName = str(node, Schema.FAMILY_NAME),
            additionalName = str(node, Schema.ADDITIONAL_NAME),
            honorificPrefix = str(node, Schema.HONORIFIC_PREFIX),
            honorificSuffix = str(node, Schema.HONORIFIC_SUFFIX),
            phoneticName = str(node, SolidShare.PHONETIC_NAME),
            nickname = str(node, SolidShare.NICKNAME),
        ).takeUnless { it.isEmpty() }
    }

    private fun readMembership(): TicketMembership? {
        val node = follow(self, Schema.PROGRAM_MEMBERSHIP_USED) ?: return null
        return TicketMembership(
            programName = str(node, Schema.PROGRAM_NAME),
            membershipNumber = str(node, Schema.MEMBERSHIP_NUMBER),
            membershipStatus = str(node, SolidShare.MEMBERSHIP_STATUS),
            pointsBalance = str(node, SolidShare.POINTS_BALANCE),
            balance = str(node, SolidShare.BALANCE),
            balanceCurrency = str(node, SolidShare.BALANCE_CURRENCY),
        ).takeUnless { it.isEmpty() }
    }

    private fun readStyle(): TicketStyle? {
        val node = follow(self, SolidShare.STYLE) ?: return null
        return TicketStyle(
            foregroundColor = str(node, SolidShare.FOREGROUND_COLOR),
            backgroundColor = str(node, SolidShare.BACKGROUND_COLOR),
            labelColor = str(node, SolidShare.LABEL_COLOR),
            stripColor = str(node, SolidShare.STRIP_COLOR),
            footerBackgroundColor = str(node, SolidShare.FOOTER_BACKGROUND_COLOR),
            logoSymbolName = str(node, SolidShare.LOGO_SYMBOL_NAME),
            logoText = str(node, SolidShare.LOGO_TEXT),
            logoImage = str(node, SolidShare.LOGO_IMAGE),
            iconImage = str(node, SolidShare.ICON_IMAGE),
            stripImage = str(node, SolidShare.STRIP_IMAGE),
            thumbnailImage = str(node, SolidShare.THUMBNAIL_IMAGE),
            backgroundImage = str(node, SolidShare.BACKGROUND_IMAGE),
            footerImage = str(node, SolidShare.FOOTER_IMAGE),
        ).takeUnless { it.isEmpty() }
    }

    private fun readDetails(): List<TicketDetail> =
        followAll(self, SolidShare.DETAIL).mapNotNull { node ->
            TicketDetail(
                label = str(node, SolidShare.LABEL),
                value = str(node, SolidShare.VALUE),
                placement = enumOrNull<DetailPlacement>(str(node, SolidShare.PLACEMENT)),
                order = int(node, SolidShare.ORDER),
                changeMessage = str(node, SolidShare.CHANGE_MESSAGE),
                textAlignment = str(node, SolidShare.TEXT_ALIGNMENT),
                linkUrl = str(node, SolidShare.LINK_URL),
            ).takeUnless { it.label == null && it.value == null }
        }

    private fun readRelevantLocations(): List<TicketRelevantLocation> =
        followAll(self, SolidShare.RELEVANT_LOCATION).mapNotNull { node ->
            TicketRelevantLocation(
                geo = readGeo(node),
                maxDistance = int(node, SolidShare.MAX_DISTANCE),
                relevantText = str(node, SolidShare.RELEVANT_TEXT),
            ).takeUnless { it.geo == null && it.maxDistance == null && it.relevantText == null }
        }

    private fun readBeacons(): List<TicketBeacon> =
        followAll(self, SolidShare.BEACON).mapNotNull { node ->
            TicketBeacon(
                proximityUuid = str(node, SolidShare.PROXIMITY_UUID),
                major = int(node, SolidShare.BEACON_MAJOR),
                minor = int(node, SolidShare.BEACON_MINOR),
                relevantText = str(node, SolidShare.RELEVANT_TEXT),
            ).takeUnless { it.proximityUuid == null && it.relevantText == null }
        }

    private fun readWifi(): List<TicketWifi> =
        followAll(self, SolidShare.WIFI_NETWORK).mapNotNull { node ->
            TicketWifi(ssid = str(node, SolidShare.SSID), password = str(node, SolidShare.PASSWORD))
                .takeUnless { it.ssid == null && it.password == null }
        }

    private fun readReservation(): TicketReservation? {
        val node = follow(self, SolidShare.RESERVATION) ?: return null
        return TicketReservation(
            status = reservationStatusOf(str(node, Schema.RESERVATION_STATUS)),
            bookingReference = str(node, Schema.RESERVATION_ID),
            bookingTime = str(node, Schema.BOOKING_TIME),
            modifiedTime = str(node, Schema.MODIFIED_TIME),
            fareClass = str(node, SolidShare.FARE_CLASS),
            boardingGroup = str(node, Schema.BOARDING_GROUP),
            boardingZone = str(node, SolidShare.BOARDING_ZONE),
            passengerSequenceNumber = str(node, Schema.PASSENGER_SEQUENCE_NUMBER),
            passengerPriorityStatus = str(node, Schema.PASSENGER_PRIORITY_STATUS),
            securityScreening = str(node, Schema.SECURITY_SCREENING),
            compartmentCode = str(node, SolidShare.COMPARTMENT_CODE),
            passengerStatus = str(node, SolidShare.PASSENGER_STATUS),
            baggageTags = strList(node, SolidShare.BAGGAGE_TAG),
            baggageAllowance = str(node, SolidShare.BAGGAGE_ALLOWANCE),
            fastTrack = bool(node, SolidShare.FAST_TRACK),
            electronicTicket = bool(node, SolidShare.ELECTRONIC_TICKET),
            specialServiceRequests = strList(node, SolidShare.SPECIAL_SERVICE_REQUEST),
            passengerCapabilities = strList(node, SolidShare.PASSENGER_CAPABILITY),
            documentsVerified = bool(node, SolidShare.DOCUMENTS_VERIFIED),
            tariff = str(node, SolidShare.TARIFF),
            validityRegion = str(node, SolidShare.VALIDITY_REGION),
            checkinTime = str(node, Schema.CHECKIN_TIME),
            checkoutTime = str(node, Schema.CHECKOUT_TIME),
            numAdults = int(node, Schema.NUM_ADULTS),
            numChildren = int(node, Schema.NUM_CHILDREN),
        ).takeUnless { it == TicketReservation() }
    }

    private fun readJourney(category: TicketCategory): TicketJourney? {
        val node = reservationForTargets().firstOrNull { isTripType(typesOf(it)) } ?: return null
        val mode = modeOf(typesOf(node), category)
        val departure = readStop(node, mode, isDeparture = true)
        val arrival = readStop(node, mode, isDeparture = false)
        val serviceNumber = when (mode) {
            TransportMode.FLIGHT -> str(node, Schema.FLIGHT_NUMBER)
            TransportMode.TRAIN -> str(node, Schema.TRAIN_NUMBER)
            TransportMode.BUS -> str(node, Schema.BUS_NUMBER)
            TransportMode.BOAT -> str(node, SolidShare.VEHICLE_NUMBER)
        }
        val serviceName = when (mode) {
            TransportMode.TRAIN -> str(node, Schema.TRAIN_NAME)
            TransportMode.BUS -> str(node, Schema.BUS_NAME)
            TransportMode.BOAT -> str(node, SolidShare.VEHICLE_NAME)
            TransportMode.FLIGHT -> null
        }
        return TicketJourney(
            mode = mode,
            carrierName = follow(node, Schema.PROVIDER)?.let { str(it, Schema.NAME) },
            serviceNumber = serviceNumber,
            serviceName = serviceName,
            departure = departure,
            arrival = arrival,
            departureTime = str(node, Schema.DEPARTURE_TIME),
            arrivalTime = str(node, Schema.ARRIVAL_TIME),
            originalDepartureTime = str(node, SolidShare.ORIGINAL_DEPARTURE_TIME),
            originalArrivalTime = str(node, SolidShare.ORIGINAL_ARRIVAL_TIME),
            boardingTime = str(node, SolidShare.BOARDING_TIME),
            originalBoardingTime = str(node, SolidShare.ORIGINAL_BOARDING_TIME),
            transitStatus = str(node, SolidShare.TRANSIT_STATUS),
            transitStatusReason = str(node, SolidShare.TRANSIT_STATUS_REASON),
            vehicleName = if (mode == TransportMode.BOAT) null else str(node, SolidShare.VEHICLE_NAME),
            vehicleNumber = if (mode == TransportMode.BOAT) null else str(node, SolidShare.VEHICLE_NUMBER),
            vehicleType = str(node, SolidShare.VEHICLE_TYPE),
            coachNumber = str(node, SolidShare.COACH_NUMBER),
            serviceBrand = str(node, SolidShare.SERVICE_BRAND),
            duration = str(node, SolidShare.DURATION),
            aircraft = str(node, Schema.AIRCRAFT),
            boardingPolicy = str(node, Schema.BOARDING_POLICY),
        )
    }

    private fun readStop(trip: String, mode: TransportMode, isDeparture: Boolean): TicketStop? {
        val gate = str(trip, if (isDeparture) Schema.DEPARTURE_GATE else Schema.ARRIVAL_GATE)
        val terminal = str(trip, if (isDeparture) Schema.DEPARTURE_TERMINAL else Schema.ARRIVAL_TERMINAL)
        val platform = str(trip, if (isDeparture) Schema.DEPARTURE_PLATFORM else Schema.ARRIVAL_PLATFORM)
            ?: str(trip, if (isDeparture) SolidShare.DEPARTURE_PLATFORM else SolidShare.ARRIVAL_PLATFORM)
        val node = stopLinks(isDeparture).firstNotNullOfOrNull { follow(trip, it) }
        val place = node?.let {
            TicketStop(
                name = str(it, Schema.NAME),
                code = str(it, SolidShare.STOP_CODE),
                iataCode = str(it, Schema.IATA_CODE),
                cityName = str(it, SolidShare.CITY_NAME),
                timeZone = str(it, SolidShare.TIME_ZONE),
                address = str(it, Schema.ADDRESS),
                geo = readGeo(it),
                securityPrograms = strList(it, SolidShare.SECURITY_PROGRAM),
            )
        }
        val stop = (place ?: TicketStop()).copy(gate = gate, terminal = terminal, platform = platform)
        return stop.takeUnless { it == TicketStop() }
    }

    private fun readEvent(): TicketEvent? {
        val node = reservationForTargets().firstOrNull { isEventType(typesOf(it)) } ?: return null
        val venue = follow(node, Schema.LOCATION)?.let { v ->
            TicketPlace(
                name = str(v, Schema.NAME),
                address = str(v, Schema.ADDRESS),
                geo = readGeo(v),
                telephone = str(v, Schema.TELEPHONE),
                room = str(v, SolidShare.ROOM),
                entrance = str(v, SolidShare.ENTRANCE),
                entranceGate = str(v, SolidShare.ENTRANCE_GATE),
                entranceDoor = str(v, SolidShare.ENTRANCE_DOOR),
                entrancePortal = str(v, SolidShare.ENTRANCE_PORTAL),
                regionName = str(v, SolidShare.REGION_NAME),
            ).takeUnless { it.isEmpty() }
        }
        return TicketEvent(
            name = str(node, Schema.NAME),
            startDate = str(node, Schema.START_DATE),
            endDate = str(node, Schema.END_DATE),
            doorTime = str(node, Schema.DOOR_TIME),
            status = eventStatusOf(str(node, Schema.EVENT_STATUS)),
            previousStartDate = str(node, Schema.PREVIOUS_START_DATE),
            duration = str(node, SolidShare.DURATION),
            location = venue,
            performers = strList(node, Schema.PERFORMER),
            organizer = str(node, Schema.ORGANIZER),
            genre = str(node, SolidShare.GENRE),
            sportName = str(node, SolidShare.SPORT_NAME),
            leagueName = str(node, SolidShare.LEAGUE_NAME),
            leagueAbbreviation = str(node, SolidShare.LEAGUE_ABBREVIATION),
            homeTeam = str(node, Schema.HOME_TEAM),
            awayTeam = str(node, Schema.AWAY_TEAM),
            admissionLevel = str(node, SolidShare.ADMISSION_LEVEL),
            admissionLevelAbbreviation = str(node, SolidShare.ADMISSION_LEVEL_ABBREVIATION),
            gatesOpenTime = str(node, SolidShare.GATES_OPEN_TIME),
            boxOfficeOpenTime = str(node, SolidShare.BOX_OFFICE_OPEN_TIME),
            parkingOpenTime = str(node, SolidShare.PARKING_OPEN_TIME),
            fanZoneOpenTime = str(node, SolidShare.FAN_ZONE_OPEN_TIME),
            venueOpenTime = str(node, SolidShare.VENUE_OPEN_TIME),
            venueCloseTime = str(node, SolidShare.VENUE_CLOSE_TIME),
            tailgatingAllowed = bool(node, SolidShare.TAILGATING_ALLOWED),
            dateUnannounced = bool(node, SolidShare.DATE_UNANNOUNCED),
            dateUndetermined = bool(node, SolidShare.DATE_UNDETERMINED),
        ).takeUnless { it == TicketEvent() }
    }

    // ---- Geo ----------------------------------------------------------------------------------

    private fun writeGeo(parent: String, geoNode: String, geo: TicketGeo?) {
        if (geo == null || (geo.latitude == null && geo.longitude == null)) return
        addQuad(parent, Schema.GEO, geoNode)
        addQuad(geoNode, RDF.TYPE, Schema.GEO_COORDINATES)
        putDouble(geoNode, Schema.LATITUDE, geo.latitude)
        putDouble(geoNode, Schema.LONGITUDE, geo.longitude)
        putDouble(geoNode, Schema.ELEVATION, geo.elevation)
    }

    private fun readGeo(parent: String): TicketGeo? {
        val node = follow(parent, Schema.GEO) ?: return null
        val geo = TicketGeo(
            dbl(node, Schema.LATITUDE),
            dbl(node, Schema.LONGITUDE),
            dbl(node, Schema.ELEVATION),
        )
        return geo.takeUnless { it.latitude == null && it.longitude == null }
    }

    // ---- Type / enum mapping ------------------------------------------------------------------

    private fun reservationTypeFor(data: NewTicket): String? {
        if (data.reservation == null && data.journey == null && data.event == null) return null
        return when {
            data.journey != null -> when (data.journey.mode) {
                TransportMode.FLIGHT -> Schema.FLIGHT_RESERVATION
                TransportMode.TRAIN -> Schema.TRAIN_RESERVATION
                TransportMode.BUS -> Schema.BUS_RESERVATION
                TransportMode.BOAT -> Schema.BOAT_RESERVATION
            }
            data.event != null -> Schema.EVENT_RESERVATION
            data.category == TicketCategory.LODGING -> Schema.LODGING_RESERVATION
            else -> Schema.RESERVATION
        }
    }

    private fun tripTypeFor(mode: TransportMode): String = when (mode) {
        TransportMode.FLIGHT -> Schema.FLIGHT
        TransportMode.TRAIN -> Schema.TRAIN_TRIP
        TransportMode.BUS -> Schema.BUS_TRIP
        TransportMode.BOAT -> Schema.BOAT_TRIP
    }

    private fun stopType(mode: TransportMode): String = when (mode) {
        TransportMode.FLIGHT -> Schema.AIRPORT
        TransportMode.TRAIN -> Schema.TRAIN_STATION
        TransportMode.BUS -> Schema.BUS_STOP
        TransportMode.BOAT -> Schema.BOAT_TERMINAL
    }

    private fun stopLink(mode: TransportMode, isDeparture: Boolean): String = when (mode) {
        TransportMode.FLIGHT -> if (isDeparture) Schema.DEPARTURE_AIRPORT else Schema.ARRIVAL_AIRPORT
        TransportMode.TRAIN -> if (isDeparture) Schema.DEPARTURE_STATION else Schema.ARRIVAL_STATION
        TransportMode.BUS -> if (isDeparture) Schema.DEPARTURE_BUS_STOP else Schema.ARRIVAL_BUS_STOP
        TransportMode.BOAT -> if (isDeparture) Schema.DEPARTURE_BOAT_TERMINAL else Schema.ARRIVAL_BOAT_TERMINAL
    }

    private fun stopLinks(isDeparture: Boolean): List<String> =
        if (isDeparture) {
            listOf(Schema.DEPARTURE_AIRPORT, Schema.DEPARTURE_STATION, Schema.DEPARTURE_BUS_STOP, Schema.DEPARTURE_BOAT_TERMINAL)
        } else {
            listOf(Schema.ARRIVAL_AIRPORT, Schema.ARRIVAL_STATION, Schema.ARRIVAL_BUS_STOP, Schema.ARRIVAL_BOAT_TERMINAL)
        }

    private fun modeOf(types: List<String>, fallback: TicketCategory): TransportMode = when {
        Schema.FLIGHT in types -> TransportMode.FLIGHT
        Schema.TRAIN_TRIP in types -> TransportMode.TRAIN
        Schema.BUS_TRIP in types -> TransportMode.BUS
        Schema.BOAT_TRIP in types -> TransportMode.BOAT
        fallback == TicketCategory.TRAIN -> TransportMode.TRAIN
        fallback == TicketCategory.BUS -> TransportMode.BUS
        fallback == TicketCategory.BOAT -> TransportMode.BOAT
        else -> TransportMode.FLIGHT
    }

    private fun eventTypeFor(e: TicketEvent): String = when {
        e.sportName != null || e.homeTeam != null || e.awayTeam != null -> Schema.SPORTS_EVENT
        getCategory() == TicketCategory.CINEMA -> Schema.SCREENING_EVENT
        else -> Schema.EVENT
    }

    private fun reservationStatusIri(s: TicketReservationStatus): String = when (s) {
        TicketReservationStatus.CONFIRMED -> Schema.RESERVATION_CONFIRMED
        TicketReservationStatus.PENDING -> Schema.RESERVATION_PENDING
        TicketReservationStatus.HOLD -> Schema.RESERVATION_HOLD
        TicketReservationStatus.CANCELLED -> Schema.RESERVATION_CANCELLED
    }

    private fun reservationStatusOf(iri: String?): TicketReservationStatus? = when (iri) {
        Schema.RESERVATION_CONFIRMED -> TicketReservationStatus.CONFIRMED
        Schema.RESERVATION_PENDING -> TicketReservationStatus.PENDING
        Schema.RESERVATION_HOLD -> TicketReservationStatus.HOLD
        Schema.RESERVATION_CANCELLED -> TicketReservationStatus.CANCELLED
        else -> null
    }

    private fun eventStatusIri(s: TicketEventStatus): String = when (s) {
        TicketEventStatus.SCHEDULED -> Schema.EVENT_SCHEDULED
        TicketEventStatus.RESCHEDULED -> Schema.EVENT_RESCHEDULED
        TicketEventStatus.POSTPONED -> Schema.EVENT_POSTPONED
        TicketEventStatus.CANCELLED -> Schema.EVENT_CANCELLED
        TicketEventStatus.MOVED_ONLINE -> Schema.EVENT_MOVED_ONLINE
    }

    private fun eventStatusOf(iri: String?): TicketEventStatus? = when (iri) {
        Schema.EVENT_SCHEDULED -> TicketEventStatus.SCHEDULED
        Schema.EVENT_RESCHEDULED -> TicketEventStatus.RESCHEDULED
        Schema.EVENT_POSTPONED -> TicketEventStatus.POSTPONED
        Schema.EVENT_CANCELLED -> TicketEventStatus.CANCELLED
        Schema.EVENT_MOVED_ONLINE -> TicketEventStatus.MOVED_ONLINE
        else -> null
    }

    private fun isEventType(types: List<String>): Boolean =
        types.any { it in EVENT_TYPES }

    private fun isTripType(types: List<String>): Boolean =
        types.any { it in TRIP_TYPES }

    private fun reservationForTargets(): List<String> =
        follow(self, SolidShare.RESERVATION)?.let { followAll(it, Schema.RESERVATION_FOR) } ?: emptyList()

    // ---- Low-level quad helpers ---------------------------------------------------------------

    private fun str(subject: String, predicate: String): String? =
        findPropertyForSubject(subject, predicate)

    private fun strList(subject: String, predicate: String): List<String> =
        findAllPropertiesForSubject(subject, predicate)

    private fun bool(subject: String, predicate: String): Boolean? =
        str(subject, predicate)?.toBooleanStrictOrNull()

    private fun int(subject: String, predicate: String): Int? =
        str(subject, predicate)?.toIntOrNull()

    private fun dbl(subject: String, predicate: String): Double? =
        str(subject, predicate)?.toDoubleOrNull()

    private fun follow(subject: String, predicate: String): String? =
        findPropertyForSubject(subject, predicate)

    private fun followAll(subject: String, predicate: String): List<String> =
        findAllPropertiesForSubject(subject, predicate)

    private fun typesOf(subject: String): List<String> =
        findAllPropertiesForSubject(subject, RDF.TYPE)

    private fun putStr(subject: String, predicate: String, value: String?) {
        if (!value.isNullOrBlank()) addQuadLiteral(subject, predicate, value, XSD.STRING)
    }

    private fun putIri(subject: String, predicate: String, value: String?) {
        if (!value.isNullOrBlank()) addQuad(subject, predicate, value)
    }

    private fun putDate(subject: String, predicate: String, value: String?) {
        if (!value.isNullOrBlank()) addQuadLiteral(subject, predicate, value, XSD.dateTypeFor(value))
    }

    private fun putDuration(subject: String, predicate: String, value: String?) {
        if (!value.isNullOrBlank()) addQuadLiteral(subject, predicate, value, XSD.DURATION)
    }

    private fun putBool(subject: String, predicate: String, value: Boolean?) {
        if (value != null) addQuadLiteral(subject, predicate, value.toString(), XSD.BOOLEAN)
    }

    private fun putInt(subject: String, predicate: String, value: Int?) {
        if (value != null) addQuadLiteral(subject, predicate, value.toString(), XSD.INTEGER)
    }

    private fun putDouble(subject: String, predicate: String, value: Double?) {
        if (value != null) addQuadLiteral(subject, predicate, value.toString(), XSD.DECIMAL)
    }

    private fun putEnum(subject: String, predicate: String, value: Enum<*>?) {
        if (value != null) addQuadLiteral(subject, predicate, value.name, XSD.STRING)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    // A sub-entity counts as empty — and so is neither written nor read back as a node — when every
    // one of its fields is null or blank. Blank-aware so a `TicketSeat(seatNumber = "  ")` collapses
    // to nothing rather than a bare, part-less node.
    private fun TicketOrganization.isEmpty(): Boolean = allBlank(name, logoUri, url, telephone, email, iataCode)

    private fun TicketPerson.isEmpty(): Boolean = allBlank(
        name, givenName, familyName, additionalName, honorificPrefix, honorificSuffix, phoneticName, nickname,
    )

    private fun TicketMembership.isEmpty(): Boolean =
        allBlank(programName, membershipNumber, membershipStatus, pointsBalance, balance, balanceCurrency)

    private fun TicketStyle.isEmpty(): Boolean = allBlank(
        foregroundColor, backgroundColor, labelColor, stripColor, footerBackgroundColor,
        logoText, logoSymbolName,
        logoImage, iconImage, stripImage, thumbnailImage, backgroundImage, footerImage,
    )

    private fun TicketSeat.isEmpty(): Boolean = allBlank(
        seatNumber, seatRow, seatSection, seatingType, seatIdentifier,
        seatLevel, seatAisle, seatDescription, seatSectionColor, coach,
    )

    private fun TicketPlace.isEmpty(): Boolean =
        geo == null && allBlank(
            name, address, telephone, room, entrance, entranceGate, entranceDoor, entrancePortal, regionName,
        )

    private fun TicketRelevantLocation.isEmpty(): Boolean =
        geo == null && maxDistance == null && relevantText.isNullOrBlank()

    private fun TicketStop.isPlaceEmpty(): Boolean =
        geo == null && securityPrograms.isEmpty() &&
            allBlank(name, code, iataCode, cityName, timeZone, address)

    private fun allBlank(vararg values: String?): Boolean = values.all { it.isNullOrBlank() }

    private companion object {
        private val EVENT_TYPES = setOf(
            Schema.EVENT, Schema.MUSIC_EVENT, Schema.SPORTS_EVENT,
            Schema.SCREENING_EVENT, Schema.THEATER_EVENT, Schema.FESTIVAL,
        )
        private val TRIP_TYPES = setOf(
            Schema.TRIP, Schema.FLIGHT, Schema.TRAIN_TRIP, Schema.BUS_TRIP, Schema.BOAT_TRIP,
        )
    }
}
