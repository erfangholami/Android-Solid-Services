package com.erfangholami.androidsolidservices.shared.model.tickets

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketRDF
import kotlinx.parcelize.Parcelize

/**
 * The kind of pass a ticket represents. Stored as a literal (`solidshare:category`) and used to
 * pick the reservation/trip RDF types — see `documents/TICKET_VOCAB.md` §1.
 */
public enum class TicketCategory {
    EVENT,
    FLIGHT,
    TRAIN,
    BUS,
    BOAT,
    CINEMA,
    LODGING,
    LOYALTY,
    COUPON,
    GENERIC,
}

/**
 * The barcode symbology of a ticket's payload, stored on a [TicketBarcode] as
 * `solidshare:symbology` so the exact barcode the issuer produced can be re-rendered for gate
 * scanners. [NONE] marks a barcode-less pass.
 */
public enum class TicketBarcodeFormat {
    QR_CODE,
    AZTEC,
    PDF_417,
    CODE_128,
    CODE_39,
    CODE_93,
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    ITF,
    CODABAR,
    DATA_MATRIX,
    NONE,
}

/** How a ticket entered the pod, stored as a literal (`solidshare:source`). */
public enum class TicketSource {
    /** Entered by hand in a ticket form. */
    MANUAL,

    /** Captured by scanning an arbitrary barcode. */
    SCAN,

    /** Imported from an Apple Wallet `.pkpass` file. */
    PKPASS,

    /** Imported from a Google Wallet save-link. */
    GOOGLE_WALLET,

    /** Parsed from an IATA Bar Coded Boarding Pass payload. */
    BCBP,

    /** Parsed from a UIC 918-3 / FCB rail-ticket payload. */
    UIC,

    /** Extracted from a PDF (rendered barcode + OCR). */
    PDF,

    /** Extracted from an image or screenshot (barcode + OCR). */
    IMAGE,

    /** Added from a Solid Share ticket link / QR. */
    LINK,
}

/** Booking lifecycle state (`schema:reservationStatus`). */
public enum class TicketReservationStatus { CONFIRMED, PENDING, HOLD, CANCELLED }

/** Whether and how an event was rescheduled (`schema:eventStatus`). */
public enum class TicketEventStatus { SCHEDULED, RESCHEDULED, POSTPONED, CANCELLED, MOVED_ONLINE }

/** The mode of a journey — selects the `schema:*Trip` type and its per-mode predicates. */
public enum class TransportMode { FLIGHT, TRAIN, BUS, BOAT }

/** Where the issuer placed a [TicketDetail] on the original pass. */
public enum class DetailPlacement { HEADER, PRIMARY, SECONDARY, AUXILIARY, BACK, ADDITIONAL, FOOTER }

/**
 * One barcode on a ticket (`solidshare:Barcode`). A pass may carry several.
 *
 * Entirely modelled with minted terms: `schema:Barcode` is an image class with no payload or
 * symbology property, and schema.org has no symbology term at all.
 *
 * @property payload   The exact bytes the issuer encoded — re-rendered verbatim so a gate scanner
 *   reads an identical barcode.
 * @property symbology The symbology of [payload].
 * @property encoding  IANA charset of [payload] (e.g. `iso-8859-1`); round-tripping it wrong
 *   corrupts the barcode.
 * @property altText   Human-readable fallback shown beneath the barcode.
 * @property rotating  `true` when the payload is known to rotate server-side (SafeTix, AXS) and so
 *   will not scan later — the UI should say so rather than show a dead barcode.
 */
@Parcelize
public data class TicketBarcode(
    val payload: String,
    val symbology: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    val encoding: String? = null,
    val altText: String? = null,
    val rotating: Boolean = false,
) : Parcelable

/** A latitude/longitude pair (`schema:GeoCoordinates`). */
@Parcelize
public data class TicketGeo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
) : Parcelable

/**
 * One end of a journey — an airport, station, stop, or terminal.
 *
 * `gate`, `terminal` and `platform` describe the *journey's* use of the place, so in RDF they live
 * on the trip node (`schema:departureGate` etc.), not on the place; the codec flattens them here
 * for convenience.
 */
@Parcelize
public data class TicketStop(
    val name: String? = null,
    val code: String? = null,
    val iataCode: String? = null,
    val cityName: String? = null,
    val timeZone: String? = null,
    val address: String? = null,
    val geo: TicketGeo? = null,
    val terminal: String? = null,
    val gate: String? = null,
    val platform: String? = null,
    val securityPrograms: List<String> = emptyList(),
) : Parcelable

/**
 * The two-ended journey a travel ticket admits to (`schema:Flight` / `TrainTrip` / `BusTrip` /
 * `BoatTrip`).
 *
 * The scheduled-vs-actual pairs ([departureTime] vs [originalDepartureTime], …) exist because
 * schema.org has no such distinction — its trip times are single-valued. The convention: the
 * schema.org term holds the *live* value, the `original*` term is frozen at issue.
 *
 * Boats have no schema.org service-number or service-name term, so for [TransportMode.BOAT] the
 * codec stores [serviceNumber] in `solidshare:vehicleNumber` and [serviceName] in
 * `solidshare:vehicleName`; [vehicleNumber] / [vehicleName] are then not used for boats.
 */
@Parcelize
public data class TicketJourney(
    val mode: TransportMode,
    val carrierName: String? = null,
    val serviceNumber: String? = null,
    val serviceName: String? = null,
    val departure: TicketStop? = null,
    val arrival: TicketStop? = null,
    val departureTime: String? = null,
    val arrivalTime: String? = null,
    val originalDepartureTime: String? = null,
    val originalArrivalTime: String? = null,
    val boardingTime: String? = null,
    val originalBoardingTime: String? = null,
    val transitStatus: String? = null,
    val transitStatusReason: String? = null,
    val vehicleName: String? = null,
    val vehicleNumber: String? = null,
    val vehicleType: String? = null,
    val coachNumber: String? = null,
    val serviceBrand: String? = null,
    val duration: String? = null,
    val aircraft: String? = null,
    val boardingPolicy: String? = null,
) : Parcelable

/** A seat assignment (`schema:Seat`). Repeatable — a pass may cover several seats. */
@Parcelize
public data class TicketSeat(
    val seatNumber: String? = null,
    val seatRow: String? = null,
    val seatSection: String? = null,
    val seatingType: String? = null,
    val seatIdentifier: String? = null,
    val seatLevel: String? = null,
    val seatAisle: String? = null,
    val seatDescription: String? = null,
    val seatSectionColor: String? = null,
    val coach: String? = null,
) : Parcelable

/** A venue (`schema:Place`) — the location of an event ticket. */
@Parcelize
public data class TicketPlace(
    val name: String? = null,
    val address: String? = null,
    val geo: TicketGeo? = null,
    val telephone: String? = null,
    val room: String? = null,
    val entrance: String? = null,
    val entranceGate: String? = null,
    val entranceDoor: String? = null,
    val entrancePortal: String? = null,
    val regionName: String? = null,
) : Parcelable

/** The event an event ticket admits to (`schema:Event` or a subtype). */
@Parcelize
public data class TicketEvent(
    val name: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val doorTime: String? = null,
    val status: TicketEventStatus? = null,
    val previousStartDate: String? = null,
    val duration: String? = null,
    val location: TicketPlace? = null,
    val performers: List<String> = emptyList(),
    val organizer: String? = null,
    val genre: String? = null,
    val sportName: String? = null,
    val leagueName: String? = null,
    val leagueAbbreviation: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val admissionLevel: String? = null,
    val admissionLevelAbbreviation: String? = null,
    val gatesOpenTime: String? = null,
    val boxOfficeOpenTime: String? = null,
    val parkingOpenTime: String? = null,
    val fanZoneOpenTime: String? = null,
    val venueOpenTime: String? = null,
    val venueCloseTime: String? = null,
    val tailgatingAllowed: Boolean? = null,
    val dateUnannounced: Boolean? = null,
    val dateUndetermined: Boolean? = null,
) : Parcelable

/** The booking envelope (`schema:Reservation` or a subtype) — everything about the booking itself. */
@Parcelize
public data class TicketReservation(
    val status: TicketReservationStatus? = null,
    val bookingReference: String? = null,
    val bookingTime: String? = null,
    val modifiedTime: String? = null,
    val fareClass: String? = null,
    val boardingGroup: String? = null,
    val boardingZone: String? = null,
    val passengerSequenceNumber: String? = null,
    val passengerPriorityStatus: String? = null,
    val securityScreening: String? = null,
    val compartmentCode: String? = null,
    val passengerStatus: String? = null,
    val baggageTags: List<String> = emptyList(),
    val baggageAllowance: String? = null,
    val fastTrack: Boolean? = null,
    val electronicTicket: Boolean? = null,
    val specialServiceRequests: List<String> = emptyList(),
    val passengerCapabilities: List<String> = emptyList(),
    val documentsVerified: Boolean? = null,
    val tariff: String? = null,
    val validityRegion: String? = null,
    val checkinTime: String? = null,
    val checkoutTime: String? = null,
    val numAdults: Int? = null,
    val numChildren: Int? = null,
) : Parcelable

/** The issuing organisation (`schema:Organization`, or `schema:Airline` when [iataCode] is set). */
@Parcelize
public data class TicketOrganization(
    val name: String? = null,
    val logoUri: String? = null,
    val url: String? = null,
    val telephone: String? = null,
    val email: String? = null,
    val iataCode: String? = null,
) : Parcelable

/** The ticket holder (`schema:Person`). */
@Parcelize
public data class TicketPerson(
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val additionalName: String? = null,
    val honorificPrefix: String? = null,
    val honorificSuffix: String? = null,
    val phoneticName: String? = null,
    val nickname: String? = null,
) : Parcelable

/**
 * A loyalty / frequent-flyer membership (`schema:ProgramMembership`).
 *
 * [pointsBalance] and [balance] are minted: schema.org's `membershipPointsEarned` is a pending
 * term that means points *earned*, not a running balance.
 */
@Parcelize
public data class TicketMembership(
    val programName: String? = null,
    val membershipNumber: String? = null,
    val membershipStatus: String? = null,
    val pointsBalance: String? = null,
    val balance: String? = null,
    val balanceCurrency: String? = null,
) : Parcelable

/**
 * The pass's presentation (`solidshare:PassStyle`) — colours and image URIs. Entirely minted, so
 * the wallet can re-render a pass faithfully and export it back to `.pkpass`.
 */
@Parcelize
public data class TicketStyle(
    val foregroundColor: String? = null,
    val backgroundColor: String? = null,
    val labelColor: String? = null,
    val stripColor: String? = null,
    val footerBackgroundColor: String? = null,
    val logoText: String? = null,
    val logoSymbolName: String? = null,
    val logoImage: String? = null,
    val iconImage: String? = null,
    val stripImage: String? = null,
    val thumbnailImage: String? = null,
    val backgroundImage: String? = null,
    val footerImage: String? = null,
) : Parcelable

/**
 * A label/value field the importer could not map to a typed term (`solidshare:Detail`).
 *
 * This is the "no data lost" guarantee: a pkpass back-field, an OCR'd PDF line, or BCBP's
 * airline-individual-use blob lands here verbatim rather than being discarded.
 */
@Parcelize
public data class TicketDetail(
    val label: String? = null,
    val value: String? = null,
    val placement: DetailPlacement? = null,
    val order: Int? = null,
    val changeMessage: String? = null,
    val textAlignment: String? = null,
    val linkUrl: String? = null,
) : Parcelable

/** A location that should surface the pass on the lock screen (`solidshare:RelevantLocation`). */
@Parcelize
public data class TicketRelevantLocation(
    val geo: TicketGeo? = null,
    val maxDistance: Int? = null,
    val relevantText: String? = null,
) : Parcelable

/** A Bluetooth beacon that should surface the pass nearby (`solidshare:Beacon`). */
@Parcelize
public data class TicketBeacon(
    val proximityUuid: String? = null,
    val major: Int? = null,
    val minor: Int? = null,
    val relevantText: String? = null,
) : Parcelable

/** A Wi-Fi network associated with the pass (`solidshare:WifiNetwork`). */
@Parcelize
public data class TicketWifi(
    val ssid: String? = null,
    val password: String? = null,
) : Parcelable

/**
 * Input model for creating or updating a ticket — the complete writable state, minus the
 * server-managed fields ([Ticket.uri], [Ticket.artifactUri], timestamps, [Ticket.etag]).
 *
 * Updates use replace semantics: anything absent here is removed from the pod resource; only the
 * artifact link, `solidshare:artifactVerified` and `dcterms:created` survive a write.
 *
 * Every field is optional except [title].
 */
@Parcelize
public data class NewTicket(
    val title: String,
    val description: String? = null,
    val ticketNumber: String? = null,
    val category: TicketCategory = TicketCategory.GENERIC,
    val source: TicketSource = TicketSource.MANUAL,
    val issuer: TicketOrganization? = null,
    val holder: TicketPerson? = null,
    val seats: List<TicketSeat> = emptyList(),
    val barcodes: List<TicketBarcode> = emptyList(),
    val totalPrice: String? = null,
    val priceCurrency: String? = null,
    val dateIssued: String? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val reservation: TicketReservation? = null,
    val journey: TicketJourney? = null,
    val event: TicketEvent? = null,
    val membership: TicketMembership? = null,
    val style: TicketStyle? = null,
    val details: List<TicketDetail> = emptyList(),
    val relevantLocations: List<TicketRelevantLocation> = emptyList(),
    val relevantDate: String? = null,
    val wifiNetworks: List<TicketWifi> = emptyList(),
    val silenceRequested: Boolean? = null,
    val voided: Boolean? = null,
    val serialNumber: String? = null,
    val groupingIdentifier: String? = null,
    val organizationName: String? = null,
    val passTypeIdentifier: String? = null,
    val teamIdentifier: String? = null,
    val webServiceUrl: String? = null,
    val authenticationToken: String? = null,
    val sharingProhibited: Boolean? = null,
    val relevantStartDate: String? = null,
    val relevantEndDate: String? = null,
    val beacons: List<TicketBeacon> = emptyList(),
) : Parcelable

/**
 * A wallet ticket stored on the pod as a `schema:Ticket` resource — the complete read model.
 *
 * Mirrors [NewTicket] and adds the server-managed fields. See `documents/TICKET_VOCAB.md` for the
 * full term dictionary.
 */
@Parcelize
public data class Ticket(
    val uri: String,
    val title: String,
    val description: String? = null,
    val ticketNumber: String? = null,
    val category: TicketCategory = TicketCategory.GENERIC,
    val source: TicketSource = TicketSource.MANUAL,
    val issuer: TicketOrganization? = null,
    val holder: TicketPerson? = null,
    val seats: List<TicketSeat> = emptyList(),
    val barcodes: List<TicketBarcode> = emptyList(),
    val totalPrice: String? = null,
    val priceCurrency: String? = null,
    val dateIssued: String? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val reservation: TicketReservation? = null,
    val journey: TicketJourney? = null,
    val event: TicketEvent? = null,
    val membership: TicketMembership? = null,
    val style: TicketStyle? = null,
    val details: List<TicketDetail> = emptyList(),
    val relevantLocations: List<TicketRelevantLocation> = emptyList(),
    val relevantDate: String? = null,
    val wifiNetworks: List<TicketWifi> = emptyList(),
    val silenceRequested: Boolean? = null,
    val voided: Boolean? = null,
    val serialNumber: String? = null,
    val groupingIdentifier: String? = null,
    val organizationName: String? = null,
    val passTypeIdentifier: String? = null,
    val teamIdentifier: String? = null,
    val webServiceUrl: String? = null,
    val authenticationToken: String? = null,
    val sharingProhibited: Boolean? = null,
    val relevantStartDate: String? = null,
    val relevantEndDate: String? = null,
    val beacons: List<TicketBeacon> = emptyList(),
    val artifactUri: String? = null,
    val artifactVerified: Boolean? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val etag: String? = null,
) : Parcelable {

    /** The first barcode, if any — the one mirrored into `schema:ticketToken` for schema.org readers. */
    public val primaryBarcode: TicketBarcode?
        get() = barcodes.firstOrNull()

    public companion object {
        /** Constructs a [Ticket] from a parsed ticket RDF resource. */
        public fun createFromRdf(ticketRdf: TicketRDF): Ticket {
            val core = ticketRdf.toNewTicket()
            return Ticket(
                uri = ticketRdf.getIdentifier(),
                title = core.title,
                description = core.description,
                ticketNumber = core.ticketNumber,
                category = core.category,
                source = core.source,
                issuer = core.issuer,
                holder = core.holder,
                seats = core.seats,
                barcodes = core.barcodes,
                totalPrice = core.totalPrice,
                priceCurrency = core.priceCurrency,
                dateIssued = core.dateIssued,
                validFrom = core.validFrom,
                validThrough = core.validThrough,
                reservation = core.reservation,
                journey = core.journey,
                event = core.event,
                membership = core.membership,
                style = core.style,
                details = core.details,
                relevantLocations = core.relevantLocations,
                relevantDate = core.relevantDate,
                wifiNetworks = core.wifiNetworks,
                silenceRequested = core.silenceRequested,
                voided = core.voided,
                serialNumber = core.serialNumber,
                groupingIdentifier = core.groupingIdentifier,
                organizationName = core.organizationName,
                passTypeIdentifier = core.passTypeIdentifier,
                teamIdentifier = core.teamIdentifier,
                webServiceUrl = core.webServiceUrl,
                authenticationToken = core.authenticationToken,
                sharingProhibited = core.sharingProhibited,
                relevantStartDate = core.relevantStartDate,
                relevantEndDate = core.relevantEndDate,
                beacons = core.beacons,
                artifactUri = ticketRdf.getArtifactUri(),
                artifactVerified = ticketRdf.getArtifactVerified(),
                createdAt = ticketRdf.getCreated(),
                modifiedAt = ticketRdf.getModified(),
                etag = ticketRdf.getMetadata().etag,
            )
        }
    }
}

/**
 * One cached row of a tickets index — enough to render a wallet list without fetching each ticket
 * document.
 */
@Parcelize
public data class TicketSummary(
    val uri: String,
    val title: String,
    val category: TicketCategory = TicketCategory.GENERIC,
    val eventStart: String? = null,
    val issuer: String? = null,
    val validThrough: String? = null,
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
) : Parcelable

/** A list of [TicketSummary] rows, wrapped as one [Parcelable] so it can cross the AIDL boundary. */
@Parcelize
public data class TicketList(
    val tickets: List<TicketSummary>,
) : Parcelable

/**
 * The binary content of a ticket's original imported artifact read from the pod (e.g. the
 * `.pkpass` file the ticket was created from).
 */
@Parcelize
public class TicketArtifact(
    public val uri: String,
    public val contentType: String,
    public val bytes: ByteArray,
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TicketArtifact) return false
        return uri == other.uri &&
                contentType == other.contentType &&
                bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
