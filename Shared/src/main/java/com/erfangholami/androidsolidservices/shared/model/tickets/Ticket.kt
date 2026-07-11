package com.erfangholami.androidsolidservices.shared.model.tickets

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.rdf.tickets.TicketRDF
import kotlinx.parcelize.Parcelize

/**
 * The kind of pass a ticket represents, mirroring the categories of mainstream
 * wallet apps. Stored as a literal (`solidshare:category`) on the ticket resource.
 */
public enum class TicketCategory {
    EVENT,
    FLIGHT,
    TRAIN,
    BUS,
    CINEMA,
    LOYALTY,
    COUPON,
    GENERIC,
}

/**
 * The barcode symbology of a ticket's token, stored as a literal
 * (`solidshare:barcodeFormat`) so the exact barcode the issuer produced can be
 * re-rendered for gate scanners. [NONE] marks a ticket without a barcode.
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

/**
 * How a ticket entered the pod, stored as a literal (`solidshare:source`).
 */
public enum class TicketSource {
    /** Entered by hand in a ticket form. */
    MANUAL,

    /** Captured by scanning an arbitrary barcode. */
    SCAN,

    /** Imported from an Apple Wallet `.pkpass` file. */
    PKPASS,

    /** Imported from a Google Wallet save-link. */
    GOOGLE_WALLET,
}

/**
 * The seat assignment of a ticket (`schema:ticketedSeat` → `schema:Seat`).
 *
 * @property seatNumber  The seat number (`schema:seatNumber`), or `null`.
 * @property seatRow     The seat row (`schema:seatRow`), or `null`.
 * @property seatSection The seat section/block (`schema:seatSection`), or `null`.
 */
@Parcelize
public data class TicketSeat(
    val seatNumber: String? = null,
    val seatRow: String? = null,
    val seatSection: String? = null,
) : Parcelable

/**
 * The venue of a ticket's event (`schema:location` → `schema:Place`).
 *
 * @property name    The venue name (`schema:name`), or `null`.
 * @property address The venue address (`schema:address`), or `null`.
 */
@Parcelize
public data class TicketPlace(
    val name: String? = null,
    val address: String? = null,
) : Parcelable

/**
 * The event a ticket admits to (`solidshare:event` → `schema:Event`).
 *
 * @property name      The event name (`schema:name`), or `null`.
 * @property startDate The event start as ISO-8601 (`schema:startDate`), or `null`.
 * @property endDate   The event end as ISO-8601 (`schema:endDate`), or `null`.
 * @property location  The venue, or `null`.
 */
@Parcelize
public data class TicketEvent(
    val name: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val location: TicketPlace? = null,
) : Parcelable

/**
 * A wallet ticket stored on the pod as a `schema:Ticket` resource.
 *
 * @property uri           The absolute pod URI of the ticket's primary subject (`…/{id}.ttl#this`).
 * @property title         The display title (`schema:name`).
 * @property description   Free-text notes (`schema:description`), or `null`.
 * @property ticketNumber  The human-readable ticket number (`schema:ticketNumber`), or `null`.
 * @property ticketToken   The exact barcode payload (`schema:ticketToken`), or `null` when barcode-less.
 * @property barcodeFormat The barcode symbology of [ticketToken].
 * @property category      The pass category.
 * @property issuerName    The issuing organization's display name (`schema:issuedBy`), or `null`.
 * @property underName     The ticket holder's display name (`schema:underName`), or `null`.
 * @property seat          The seat assignment, or `null`.
 * @property totalPrice    The price as its raw lexical value (`schema:totalPrice`), or `null`.
 * @property priceCurrency The ISO-4217 currency of [totalPrice] (`schema:priceCurrency`), or `null`.
 * @property dateIssued    When the ticket was issued, ISO-8601 (`schema:dateIssued`), or `null`.
 * @property event         The event the ticket admits to, or `null`.
 * @property validFrom     Validity start, ISO-8601 (`schema:validFrom`), or `null`.
 * @property validThrough  Validity end, ISO-8601 (`schema:validThrough`), or `null`.
 * @property source        How the ticket entered the pod.
 * @property artifactUri   The pod URI of the original imported artifact (e.g. `.pkpass`), or `null`.
 * @property createdAt     When the ticket resource was created, ISO-8601 (`dcterms:created`), or `null`.
 * @property modifiedAt    When the ticket resource was last modified, ISO-8601 (`dcterms:modified`), or `null`.
 * @property etag          The resource ETag as of the read this instance came from, or `null`.
 */
@Parcelize
public data class Ticket(
    val uri: String,
    val title: String,
    val description: String? = null,
    val ticketNumber: String? = null,
    val ticketToken: String? = null,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.NONE,
    val category: TicketCategory = TicketCategory.GENERIC,
    val issuerName: String? = null,
    val underName: String? = null,
    val seat: TicketSeat? = null,
    val totalPrice: String? = null,
    val priceCurrency: String? = null,
    val dateIssued: String? = null,
    val event: TicketEvent? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val source: TicketSource = TicketSource.MANUAL,
    val artifactUri: String? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val etag: String? = null,
) : Parcelable {
    public companion object {
        /**
         * Constructs a [Ticket] from a parsed ticket RDF resource.
         */
        public fun createFromRdf(ticketRdf: TicketRDF): Ticket {
            return Ticket(
                uri = ticketRdf.getIdentifier().toString(),
                title = ticketRdf.getTitle(),
                description = ticketRdf.getDescription(),
                ticketNumber = ticketRdf.getTicketNumber(),
                ticketToken = ticketRdf.getTicketToken(),
                barcodeFormat = ticketRdf.getBarcodeFormat(),
                category = ticketRdf.getCategory(),
                issuerName = ticketRdf.getIssuerName(),
                underName = ticketRdf.getUnderName(),
                seat = ticketRdf.getSeat(),
                totalPrice = ticketRdf.getTotalPrice(),
                priceCurrency = ticketRdf.getPriceCurrency(),
                dateIssued = ticketRdf.getDateIssued(),
                event = ticketRdf.getEvent(),
                validFrom = ticketRdf.getValidFrom(),
                validThrough = ticketRdf.getValidThrough(),
                source = ticketRdf.getSource(),
                artifactUri = ticketRdf.getArtifactUri(),
                createdAt = ticketRdf.getCreated(),
                modifiedAt = ticketRdf.getModified(),
                etag = ticketRdf.getMetadata().etag,
            )
        }
    }
}

/**
 * Input model for creating or updating a ticket, mirroring [Ticket] minus the
 * server-managed fields (`uri`, `artifactUri`, timestamps, `etag`).
 *
 * Updates use replace semantics: properties absent from this model are removed
 * from the pod resource (the artifact link and `dcterms:created` are preserved).
 */
@Parcelize
public data class NewTicket(
    val title: String,
    val description: String? = null,
    val ticketNumber: String? = null,
    val ticketToken: String? = null,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.NONE,
    val category: TicketCategory = TicketCategory.GENERIC,
    val issuerName: String? = null,
    val underName: String? = null,
    val seat: TicketSeat? = null,
    val totalPrice: String? = null,
    val priceCurrency: String? = null,
    val dateIssued: String? = null,
    val event: TicketEvent? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val source: TicketSource = TicketSource.MANUAL,
) : Parcelable

/**
 * One cached row of a tickets index — enough to render a wallet list without
 * fetching each ticket document.
 *
 * @property uri          The absolute pod URI of the ticket's primary subject.
 * @property title        The ticket's display title.
 * @property category     The pass category.
 * @property eventStart   The cached event start (ISO-8601), or `null`.
 * @property issuer       The cached issuer display name, or `null`.
 * @property validThrough The cached validity end (ISO-8601), or `null`.
 */
@Parcelize
public data class TicketSummary(
    val uri: String,
    val title: String,
    val category: TicketCategory = TicketCategory.GENERIC,
    val eventStart: String? = null,
    val issuer: String? = null,
    val validThrough: String? = null,
) : Parcelable

/**
 * A list of [TicketSummary] rows, wrapped so it can be carried by a
 * `DataModuleResult` (which requires a [Parcelable] payload).
 */
@Parcelize
public data class TicketList(
    val tickets: List<TicketSummary>,
) : Parcelable

/**
 * The binary content of a ticket's original imported artifact read from the pod
 * (e.g. the `.pkpass` file the ticket was created from).
 *
 * @property uri         The absolute pod URI of the artifact resource.
 * @property contentType The artifact's media type (e.g. `application/vnd.apple.pkpass`).
 * @property bytes       The artifact bytes.
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
