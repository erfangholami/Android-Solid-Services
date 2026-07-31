package com.erfangholami.androidsolidservices.client.internal.fakes

import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBookList
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressType
import com.erfangholami.androidsolidservices.shared.model.contacts.Contact
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactMatch
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailType
import com.erfangholami.androidsolidservices.shared.model.contacts.FullGroup
import com.erfangholami.androidsolidservices.shared.model.contacts.Group
import com.erfangholami.androidsolidservices.shared.model.contacts.Name
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContactList
import com.erfangholami.androidsolidservices.shared.model.contacts.URLType
import com.erfangholami.androidsolidservices.shared.model.contacts.UrlEntry
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidSourceReference
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantDirection
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantSource
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantStatus
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketBarcode
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary

/**
 * The canned values the fakes answer with, and the identifiers the tests send.
 *
 * Every model is deliberately populated to its edges — collections non-empty, optionals set,
 * literals carrying datatypes and language tags — because these instances double as the payload of
 * the parcel-fidelity suite. A field left at its default proves nothing when it survives a round
 * trip.
 */
internal object Fixtures {

    const val WEB_ID: String = "https://alice.pod.example/profile/card#me"
    const val PEER_WEB_ID: String = "https://bob.pod.example/profile/card#me"

    /** Any call whose first WebID is this makes the fake answer on the error path instead. */
    const val FAILING_WEB_ID: String = "https://denied.pod.example/profile/card#me"

    const val STORAGE: String = "https://alice.pod.example/"
    const val CONTAINER: String = "https://alice.pod.example/notes/"
    const val RESOURCE: String = "https://alice.pod.example/notes/first"
    const val BINARY: String = "https://alice.pod.example/notes/photo.png"
    const val DESTINATION: String = "https://alice.pod.example/archive/first"
    const val ADDRESS_BOOK: String = "https://alice.pod.example/contacts/book/index.ttl#this"
    const val CONTACT: String = "https://alice.pod.example/contacts/book/Person/1/index.ttl#this"
    const val GROUP: String = "https://alice.pod.example/contacts/book/Group/1/index.ttl#this"
    const val TICKET: String = "https://alice.pod.example/tickets/1/index.ttl#this"
    const val ARTIFACT: String = "https://alice.pod.example/tickets/1/pass.pkpass"
    const val INBOX: String = "https://alice.pod.example/inbox/"
    const val NOTIFICATION: String = "https://alice.pod.example/inbox/n1"
    const val REQUEST_URI: String = "https://alice.pod.example/inbox/req1"

    const val ERROR_MESSAGE: String = "the fake refused this one"

    val HEADERS: SolidHeaders = SolidHeaders(
        mapOf(
            "ETag" to listOf("\"v1\""),
            "Content-Type" to listOf("text/turtle"),
            "Set-Cookie" to listOf("a=1", "b=2"),
        ),
    )

    val METADATA: SolidMetadata = SolidMetadata(
        aclUri = "https://alice.pod.example/notes/first.acl",
        storageDescriptionUri = "https://alice.pod.example/.well-known/solid",
        ownerUri = WEB_ID,
        wacAllow = WacAllow(userModes = setOf("read", "write"), publicModes = setOf("read")),
        allowedMethods = setOf("GET", "HEAD", "PUT", "PATCH", "DELETE"),
        linkTypes = setOf("http://www.w3.org/ns/ldp#Resource"),
        etag = "\"v1\"",
        lastModified = "Wed, 21 Oct 2026 07:28:00 GMT",
        location = RESOURCE,
        contentType = "text/turtle",
        contentLength = 512L,
        describeByUri = "https://alice.pod.example/notes/first.meta",
        acceptPatch = listOf("text/n3", "application/sparql-update"),
        acceptPost = listOf("text/turtle", "application/ld+json"),
        acceptPut = listOf("text/turtle"),
        wwwAuthenticate = "DPoP algs=\"ES256\"",
        oidcIssuerUri = "https://login.example/",
        isStorage = false,
        inboxUri = INBOX,
    )

    /** Covers an IRI object, a datatyped literal, a language-tagged literal and a blank node. */
    val QUADS: List<RdfQuad> = listOf(
        RdfQuad(RESOURCE, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://schema.org/Note"),
        RdfQuad(
            subject = RESOURCE,
            predicate = "http://purl.org/dc/terms/created",
            `object` = "2026-07-31T10:00:00Z",
            datatype = "http://www.w3.org/2001/XMLSchema#dateTime",
        ),
        RdfQuad(
            subject = RESOURCE,
            predicate = "http://www.w3.org/2000/01/rdf-schema#label",
            `object` = "Première note",
            language = "fr",
        ),
        RdfQuad("_:b0", "http://schema.org/name", "a blank subject", graph = CONTAINER),
    )

    val PNG_BYTES: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03,
    )

    fun rdfResource(identifier: String = RESOURCE): SolidRDFResource =
        SolidRDFResource(identifier, QUADS, HEADERS)

    fun nonRdfResource(identifier: String = BINARY): SolidNonRDFResource =
        SolidNonRDFResource(identifier, "image/png", PNG_BYTES.inputStream(), HEADERS)

    fun container(identifier: String = CONTAINER): SolidContainer =
        SolidContainer(identifier, QUADS, HEADERS)

    val SOURCE_REFERENCES: List<SolidSourceReference> = listOf(
        SolidSourceReference(
            identifier = RESOURCE,
            types = listOf("http://www.w3.org/ns/ldp#Resource"),
            size = 512L,
            modified = "Wed, 21 Oct 2026 07:28:00 GMT",
            mtime = 1_761_031_680L,
            contentType = "text/turtle",
            headMetadata = METADATA,
        ),
        SolidSourceReference(
            identifier = BINARY,
            types = listOf("http://www.w3.org/ns/ldp#NonRDFSource"),
        ),
    )

    val ACCESS_PROBE: AccessProbe = AccessProbe.Accessible(
        modes = setOf(ShareMode.READ, ShareMode.WRITE),
        ownerWebId = WEB_ID,
    )

    val CONTACT_DATA: ContactData = ContactData(
        fullName = "Alice Example",
        name = Name(
            familyName = "Example",
            givenName = "Alice",
            additionalName = "Q",
            honorificPrefix = "Dr",
            honorificSuffix = "PhD",
        ),
        nickname = "Ali",
        phones = listOf(PhoneEntry(number = "+31 6 1234 5678", type = PhoneType.CELL)),
        emails = listOf(EmailEntry(address = "alice@example.org", type = EmailType.WORK)),
        addresses = listOf(
            AddressEntry(
                street = "Keizersgracht 1",
                locality = "Amsterdam",
                region = "Noord-Holland",
                postalCode = "1015 CJ",
                countryName = "Netherlands",
                poBox = "PO 42",
                type = AddressType.HOME,
            ),
        ),
        birthday = "1990-04-01",
        organizationName = "Example BV",
        organizationUnit = "Research",
        role = "Engineer",
        title = "Principal",
        note = "met at the Solid symposium",
        categories = listOf("solid", "colleagues"),
        urls = listOf(UrlEntry(value = WEB_ID, type = URLType.WebId)),
        uid = "urn:uuid:0f2b0a5c-1111-4444-8888-abcdefabcdef",
    )

    val SOLID_CONTACT: SolidContact = SolidContact(
        uri = CONTACT,
        etag = "\"c1\"",
        modified = 1_761_031_680_000L,
        photoUri = "https://alice.pod.example/contacts/book/Person/1/photo.png",
        data = CONTACT_DATA,
    )

    val CONTACT_LIST: SolidContactList = SolidContactList(listOf(SOLID_CONTACT))

    val ADDRESS_BOOK_MODEL: AddressBook = AddressBook(
        uri = ADDRESS_BOOK,
        title = "Work",
        contacts = listOf(Contact(uri = CONTACT, name = "Alice Example")),
        groups = listOf(Group(uri = GROUP, name = "Team")),
    )

    val ADDRESS_BOOK_LIST: AddressBookList = AddressBookList(
        publicAddressBookUris = listOf("https://alice.pod.example/public/book/index.ttl#this"),
        privateAddressBookUris = listOf(ADDRESS_BOOK),
    )

    val CONTACT_PHOTO: ContactPhoto =
        ContactPhoto(uri = "https://alice.pod.example/p.png", contentType = "image/png", bytes = PNG_BYTES)

    val CONTACT_MATCH: ContactMatch = ContactMatch(contact = SOLID_CONTACT, addressBookUri = ADDRESS_BOOK)

    val FULL_GROUP: FullGroup = FullGroup(
        uri = GROUP,
        name = "Team",
        contacts = listOf(Contact(uri = CONTACT, name = "Alice Example")),
    )

    val NEW_TICKET: NewTicket = NewTicket(
        title = "Symposium 2026",
        description = "Day pass",
        ticketNumber = "SYM-2026-0042",
        category = TicketCategory.EVENT,
        barcodes = listOf(TicketBarcode(payload = "SYM-2026-0042", altText = "SYM 2026 0042")),
        totalPrice = "49.00",
        priceCurrency = "EUR",
        validFrom = "2026-09-01T09:00:00Z",
        validThrough = "2026-09-01T18:00:00Z",
        serialNumber = "0042",
    )

    val TICKET_MODEL: Ticket = Ticket(
        uri = TICKET,
        title = NEW_TICKET.title,
        description = NEW_TICKET.description,
        ticketNumber = NEW_TICKET.ticketNumber,
        category = NEW_TICKET.category,
        barcodes = NEW_TICKET.barcodes,
        totalPrice = NEW_TICKET.totalPrice,
        priceCurrency = NEW_TICKET.priceCurrency,
        validFrom = NEW_TICKET.validFrom,
        validThrough = NEW_TICKET.validThrough,
        serialNumber = NEW_TICKET.serialNumber,
        artifactUri = ARTIFACT,
        artifactVerified = true,
        createdAt = "2026-07-31T10:00:00Z",
        etag = "\"t1\"",
    )

    val TICKET_LIST: TicketList = TicketList(
        listOf(
            TicketSummary(
                uri = TICKET,
                title = NEW_TICKET.title,
                category = TicketCategory.EVENT,
                issuer = "Example BV",
                validThrough = NEW_TICKET.validThrough,
            ),
        ),
    )

    val TICKET_IMAGES: NewTicketImages = NewTicketImages(logo = PNG_BYTES, icon = PNG_BYTES)

    val TICKET_ARTIFACT: TicketArtifact =
        TicketArtifact(uri = ARTIFACT, contentType = "application/vnd.apple.pkpass", bytes = PNG_BYTES)

    val GIVEN_SHARE: GivenShare = GivenShare(
        receiver = ShareReceiver.WebIdReceiver(PEER_WEB_ID),
        mode = ShareMode.APPEND,
        resourceUri = RESOURCE,
        createdAt = "2026-07-31T10:00:00Z",
    )

    val RECEIVED_SHARE: ReceivedShare = ReceivedShare(
        ownerWebId = PEER_WEB_ID,
        mode = ShareMode.READ,
        resourceUri = "https://bob.pod.example/shared/report",
        addedAt = "2026-07-31T11:00:00Z",
    )

    val CATALOG_ENTRY: CatalogEntry = CatalogEntry(
        resourceUri = RESOURCE,
        title = "First note",
        description = "Ask me for access",
        depictionUri = BINARY,
    )

    val ACCESS_GRANT: AccessGrant = AccessGrant(
        direction = AccessGrantDirection.GIVEN,
        counterpartWebId = PEER_WEB_ID,
        resourceUri = RESOURCE,
        mode = ShareMode.WRITE,
        status = AccessGrantStatus.ACTIVE,
        source = AccessGrantSource.APP_INDEX,
        grantedAt = "2026-07-31T10:00:00Z",
        requestUri = REQUEST_URI,
    )

    val SHARE_REQUEST: ShareRequest = ShareRequest(
        requestUri = REQUEST_URI,
        requesterWebId = PEER_WEB_ID,
        resourceUri = RESOURCE,
        requestedMode = ShareMode.READ,
        summary = "may I read your note?",
        publishedAt = "2026-07-31T09:00:00Z",
    )

    val SHARE_NOTIFICATION: ShareNotification = ShareNotification(
        notificationUri = NOTIFICATION,
        type = ShareNotificationType.OFFER,
        ownerWebId = PEER_WEB_ID,
        resourceUri = "https://bob.pod.example/shared/report",
        mode = ShareMode.READ,
        summary = "shared a report with you",
        publishedAt = "2026-07-31T09:30:00Z",
        targetWebId = WEB_ID,
    )
}
