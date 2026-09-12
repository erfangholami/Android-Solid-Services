# Tickets

A wallet that lives on the user's pod: event tickets, boarding passes, memberships and reservations, each keeping the original file it came from alongside the structured data.

Modelled on schema.org, so a ticket written here is readable by anything that speaks the same vocabulary rather than only by your app.

## What you can build

- A pass wallet whose contents survive the user changing phones or apps.
- Import from `.pkpass`, a PDF or an email attachment, keeping the original intact.
- Boarding passes and event tickets that can be shared with the person travelling with you.
- A trip view that spans providers, because every ticket is on the same pod.

## Setup

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:client:0.7.1")
}
```

```kotlin
import com.erfangholami.androidsolidservices.client.sdk.Solid

val tickets = Solid.getTicketsDataModule(context)
```

Calls return `T?` and throw `SolidException` on failure.

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:api:0.7.1")
}
```

```kotlin
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule

val tickets = SolidTicketsDataModule.getInstance(authenticator)
```

Calls return `SolidResult<T>`.

## Recipes

### Add a ticket

A ticket is described by `NewTicket`. Only `title` is required; fill in what the source gives you.

```kotlin
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketCategory

val pass = NewTicket(
    title = "Amsterdam → Berlin",
    category = TicketCategory.TRAIN,   // EVENT, FLIGHT, TRAIN, BUS, BOAT, CINEMA, LODGING, LOYALTY, COUPON, GENERIC
    ticketNumber = "NS-88213",
    validFrom = "2026-09-14T08:12:00Z",
    validThrough = "2026-09-14T14:40:00Z",
    totalPrice = "89.50",
    priceCurrency = "EUR",
)
```

```kotlin
val ticket = tickets.createTicket(
    webId = webId,
    newTicket = pass,
    artifact = pkpassBytes,                       // (1)!
    artifactContentType = "application/vnd.apple.pkpass",
    isPrivate = true,
)
```

1. The file the ticket came from, stored beside it. Keeping it means the pass still opens in whatever produced it, even if your app never learns to render every field.

```kotlin
val ticket = tickets.createTicket(
    webId = webId,
    newTicket = pass,
    artifact = pkpassBytes,
    artifactContentType = "application/vnd.apple.pkpass",
    isPrivate = true,
).getOrThrow()
```

Artifacts cross Binder inline

On the `client` path the bytes are subject to the ~1 MB transaction limit. A larger pass fails the call rather than truncating. The `api` path is unbounded.

### List the wallet

```kotlin
val wallet = tickets.listTickets(webId)?.tickets.orEmpty()
wallet.forEach { println("${it.title} — ${it.uri}") }
```

```kotlin
val wallet = tickets.listTickets(webId).getOrThrow().tickets
```

Listing reads the index document only — one request, no matter how many tickets there are. The summaries carry enough to render a list; fetch the full ticket when someone opens one.

### Read one ticket, and its original file

```kotlin
val ticket = tickets.getTicket(webId, ticketUri)

val artifact = ticket?.artifactUri?.let { tickets.getTicketArtifact(webId, it) }
// artifact.bytes is the original .pkpass / PDF, byte for byte
```

### Update a ticket

```kotlin
tickets.updateTicket(webId, ticketUri, pass.copy(validThrough = "2026-09-14T15:10:00Z"))
```

The index is rewritten under compare-and-set, so two devices editing different tickets in the same wallet do not lose each other's changes.

### Replace the artifact

```kotlin
tickets.putTicketArtifact(
    webId = webId,
    ticketUri = ticketUri,
    artifact = updatedPkpass,
    artifactContentType = "application/vnd.apple.pkpass",
)
```

The binary is stored before the document links it, so an interrupted call never leaves a ticket pointing at a file that is not there.

### Delete a ticket

```kotlin
tickets.deleteTicket(webId, ticketUri)
```

Each ticket owns a container holding its artifact and images, so this removes all of it, then drops the index row.

### Share a ticket with someone

A ticket is a shareable entity, not just a file — sharing it grants its whole container, so the receiver gets the pass and its images together:

```kotlin
sharing.createShare(
    webId = webId,
    resourceUri = ticketUri,
    mode = ShareMode.READ,
    receiver = ShareReceiver.WebIdReceiver(theirWebId),
)
```

Unlike contacts, tickets **can** be shared publicly — the artifact is self-contained, so a link-holder gets something that makes sense without pod access. See [Sharing](https://androidsolidservices.erfangholami.com/0.7/build/sharing/index.md).

## How it flows

```
sequenceDiagram
    autonumber
    participant App as Your app
    participant SDK as SDK
    participant Pod as Solid pod

    App->>SDK: createTicket(…, artifact)
    SDK->>Pod: PUT {ticket}/artifact.pkpass
    Pod-->>SDK: 201
    Note over SDK: artifact first — the document<br/>never links a missing file
    SDK->>Pod: PUT {ticket}/index (schema.org RDF)
    Pod-->>SDK: 201
    SDK->>Pod: PATCH wallet index (compare-and-set)
    Pod-->>SDK: 205
    SDK-->>App: Ticket
```

## Errors you'll hit

| What you see                            | Why                                              | What to do                                         |
| --------------------------------------- | ------------------------------------------------ | -------------------------------------------------- |
| Call fails on a large pass              | the ~1 MB Binder limit on `client`               | use the `api` path for oversized artifacts         |
| Empty wallet on a fresh pod             | no index registered yet                          | expected — creating the first ticket bootstraps it |
| `403` on create                         | no write access to the pod's `datamodule/` root  | the account must own the storage                   |
| A ticket with no artifact               | it was created without one, or the upload failed | `artifactUri` is null; the ticket is still valid   |
| Index row missing after a failed delete | the container delete failed first, deliberately  | the ticket stays discoverable; retry the delete    |

## Under the hood

The tickets module stores wallet passes — event tickets, boarding passes, loyalty cards, coupons — as `schema:Ticket` resources on the user's own pod, one per ticket, each in its own container beside the original imported file and the pass images. It is a data module on the shared collection toolkit described in [Data modules](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/index.md): container bootstrap, type-index registration, UUID allocation, index caching and attachment naming are inherited from `api/datamodule/core/` and documented there, not here. This page covers only what is ticket-specific: the RDF a ticket is made of, the `TicketStore` verbs, the artifact and image handling, and how a ticket behaves as a shareable data identity.

Pod shape

```text
{storage}datamodule/tickets/
├── index                       ← the tickets index (solidshare:TicketIndex); registered in the
│                                 type index as a solid:instance for schema:Ticket
└── {uuid}/                     ← one container per ticket
    ├── ticket                  ← the schema:Ticket document, subject #this
    ├── artifact.pkpass         ← optional original imported file (solidshare:artifact)
    └── logo.png, icon.png, …   ← optional stored pass images, one file per pkpass image role
```

The name constants live in `Shared/.../shared/model/tickets/Constants.kt`; `index` and `ticket` are extension-less for the reason given in [Data modules](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/#pod-shape) — linked data reached by followed URL, the URI never encoding a representation. Everything belonging to one ticket lives inside its `{uuid}/` container, so a single grant on that container shares the complete pass — document, artifact and images. That layout decision is what the sharing contract below builds on.

#### The ticket document

The primary subject `#this` is always a `schema:Ticket`. What the ticket admits to hangs off a reservation envelope — schema.org's only ticket↔event link is `Reservation.reservedTicket` pointing *inward*, so the module mints `solidshare:reservation` as the forward link — and the rest of the pass lives on sibling fragment nodes of the same document (`Shared/.../shared/rdf/tickets/TicketRDF.kt:45`):

```text
#this        schema:Ticket
├─ #reservation  schema:*Reservation  →(reservationFor) #event | #trip
│  #trip         schema:Flight | TrainTrip | BusTrip | BoatTrip  →(dep/arr) #from / #to
│  #event        schema:Event | subtype  →(location) #venue
├─ #seat{n}, #barcode{n}, #detail{n}, #rel{n}, #wifi{n}, #beacon{n}
└─ #issuer, #holder, #membership, #style, #carrier
```

This is the document `TicketRDF.setTicketData` (`TicketRDF.kt:184`) writes for a pkpass-imported bus ticket — the worked example from the vocabulary reference, with node names exactly as the codec mints them (zero-based `#seat0` / `#barcode0`, the operating carrier on its own `#carrier` node, `TicketRDF.kt:85`, `TicketRDF.kt:438`):

```turtle
@prefix schema:     <https://schema.org/> .
@prefix solidshare: <https://solidshare.app/ns#> .
@prefix dcterms:    <http://purl.org/dc/terms/> .
@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .

<#this> a schema:Ticket ;
    schema:name            "Amsterdam → Berlin" ;
    schema:ticketNumber    "TKT-0042" ;
    schema:ticketToken     "MSBYRUZBTiBHSE9MQU1J…" ;
    schema:ticketedSeat    <#seat0> ;
    schema:issuedBy        <#issuer> ;
    schema:underName       <#holder> ;
    schema:totalPrice      "24.99" ;
    schema:priceCurrency   "EUR" ;
    solidshare:category    "BUS" ;
    solidshare:source      "PKPASS" ;
    solidshare:reservation <#reservation> ;
    solidshare:barcode     <#barcode0> ;
    solidshare:style       <#style> ;
    solidshare:artifact    <artifact.pkpass> ;
    solidshare:logoImage   <logo.png> ;
    dcterms:created        "2026-07-14T09:15:00Z"^^xsd:dateTime ;
    dcterms:modified       "2026-07-14T09:15:00Z"^^xsd:dateTime .

<#reservation> a schema:BusReservation ;
    schema:reservedTicket    <#this> ;
    schema:reservationFor    <#trip> ;
    schema:reservationId     "PNR7X2Q" ;
    schema:reservationStatus schema:ReservationConfirmed ;
    solidshare:fareClass     "Standard" .

<#trip> a schema:BusTrip ;
    schema:busNumber             "042" ;
    schema:busName               "FlixBus 042" ;
    schema:provider              <#carrier> ;
    schema:departureBusStop      <#from> ;
    schema:arrivalBusStop        <#to> ;
    schema:departureTime         "2026-08-02T08:15:00+02:00"^^xsd:dateTime ;
    schema:arrivalTime           "2026-08-02T14:40:00+02:00"^^xsd:dateTime ;
    solidshare:departurePlatform "B3" ;
    solidshare:duration          "PT6H25M"^^xsd:duration .

<#carrier> a schema:Organization ; schema:name "FlixBus" .
<#from> a schema:BusStop ; schema:name "Amsterdam Sloterdijk" ;
    solidshare:cityName "Amsterdam" ; solidshare:timeZone "Europe/Amsterdam" .
<#to> a schema:BusStop ; schema:name "Berlin ZOB" ;
    solidshare:cityName "Berlin" ; solidshare:timeZone "Europe/Berlin" .
<#seat0> a schema:Seat ; schema:seatNumber "12A" .
<#issuer> a schema:Organization ; schema:name "FlixBus" .
<#holder> a schema:Person ; schema:name "Erfan Gholami" .

<#barcode0> a solidshare:Barcode ;
    solidshare:payload   "MSBYRUZBTiBHSE9MQU1J…" ;
    solidshare:symbology "AZTEC" ;
    solidshare:encoding  "iso-8859-1" ;
    solidshare:altText   "PNR7X2Q" .

<#style> a solidshare:PassStyle ;
    solidshare:backgroundColor "rgb(115,196,63)" .
```

Reads follow the links, never the node names: the reservation, trip and event are discovered through `solidshare:reservation` and `schema:reservationFor` regardless of how a foreign writer named the fragments.

**Every `solidshare:`-prefixed term is minted**, under `https://solidshare.app/ns#` (`Shared/.../shared/vocab/SolidShare.kt:18`). In the excerpt that is: `category`, `source`, `reservation`, `artifact`, `logoImage`, `fareClass`, `departurePlatform` (buses and boats only — trains use `schema:departurePlatform`), `duration`, `cityName`, `timeZone`, the whole barcode node (`Barcode`, `payload`, `symbology`, `encoding`, `altText`) and the whole style node (`PassStyle`, `backgroundColor`). The full model mints further families the excerpt does not show: artifact bookkeeping (`artifactVerified`, the six image-role links), pkpass pass identity (`serialNumber`, `passTypeIdentifier`, `teamIdentifier`, `webServiceUrl`, `authenticationToken`, `sharingProhibited`, `groupingIdentifier`, `voided`, `silenceRequested`, `organizationName`), delay tracking (`originalDepartureTime`, `boardingTime`, `transitStatus`, …), relevance (`relevantDate`, `RelevantLocation`, `Beacon`, `WifiNetwork`), the `Detail` label/value catch-all, and per-node extras on seats, stops, venues, events and memberships. The rule is schema.org first — mint only where schema.org demonstrably has nothing — and the per-term justification is not repeated here: the normative dictionary is the SolidShare app repository's `documents/TICKET_VOCAB.md` (which `TicketRDF`'s KDoc cites), with the app-facing feature context in the same repository's `documents/TICKETS.md`.

#### The index document

`{container}index` is the document the type-index registration points at — a `solidshare:TicketIndex` (minted) caching one row per ticket so a wallet list renders from a single GET. The Turtle from the codec's own KDoc (`Shared/.../shared/rdf/tickets/TicketsIndexRDF.kt:22`):

```turtle
<> rdf:type solidshare:TicketIndex .

<…/{uuid}/ticket#this>
    rdf:type            schema:Ticket ;
    schema:name         "Concert" ;
    solidshare:category "EVENT" ;
    schema:startDate    "2026-07-14T19:30:00Z"^^xsd:dateTime ;
    solidshare:issuer   "Ticketmaster" ;
    schema:validThrough "2026-07-14T23:59:00Z"^^xsd:dateTime .
```

A row also carries the pass colours (`solidshare:backgroundColor` / `foregroundColor`, `TicketsIndexRDF.kt:95`) so the list can paint each card without fetching its document. `solidshare:issuer` here is minted too — a cached display-name literal, distinct from the document's `schema:issuedBy` node. Rows without a title are skipped on read.

Public surface

`SolidTicketsDataModule` (`api/.../datamodule/tickets/SolidTicketsDataModule.kt:18`) is the facade, obtained via `getInstance(authenticator)` or `getInstance(resourceManager)`; its single role interface is `tickets: TicketStore`. The engine behind it is `TicketEngine` (`api/.../tickets/implementation/TicketEngine.kt:13`), a thin adapter that wraps every helper call in `solidCatching` so each verb returns a `SolidResult`.

#### `TicketStore` verbs

`api/.../datamodule/tickets/TicketStore.kt:49`

| Verb                                                                                                  | What it does                                                                                                                                                                                                                                                       |
| ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `list(ownerWebId)`                                                                                    | Ticket summaries from every registered tickets index (`solid:instance`, both type indexes). One GET per index; a missing or unreadable index contributes nothing.                                                                                                  |
| `get(ownerWebId, ticketUri)`                                                                          | The full ticket at a URI, read as `TicketRDF` and decoded to `Ticket`.                                                                                                                                                                                             |
| `create(ownerWebId, newTicket, storage, artifact, artifactContentType, images, isPrivate, container)` | Creates the ticket in its own `{uuid}/` sub-container, storing the artifact and image roles beside it; bootstraps the collection on first use. `isPrivate` picks the type index (private by default); `container` pins a collection instead of the registered one. |
| `update(ownerWebId, ticketUri, updated)`                                                              | Rewrites the document from a `NewTicket` with replace semantics under compare-and-set, then re-caches the index row.                                                                                                                                               |
| `putArtifact(ownerWebId, ticketUri, artifact, artifactContentType, images)`                           | Replaces the stored binaries in place — the artifact, plus whichever image roles are provided — refreshing the document links and `dcterms:modified`. Built for issuer pass refresh (the pkpass web service).                                                      |
| `delete(ownerWebId, ticketUri)`                                                                       | Removes the ticket: its index row and its whole `{uuid}/` container. Returns the removed ticket.                                                                                                                                                                   |
| `getArtifact(ownerWebId, artifactUri)`                                                                | Downloads a stored binary — the artifact or any image — as `TicketArtifact(uri, contentType, bytes)`.                                                                                                                                                              |

The models are in `Shared/.../shared/model/tickets/Ticket.kt`: `NewTicket` (`:422`) is the complete writable state, `Ticket` (`:473`) adds the server-managed fields (`uri`, `artifactUri`, `artifactVerified`, `images`, timestamps, `etag`), `TicketSummary`/`TicketList` are the index rows, and `NewTicketImages` (`:374`) carries the binary image slots on create. Unknown stored enum values are tolerated on read (category falls back to `GENERIC`, source to `MANUAL`, symbology to `QR_CODE`), so foreign or future writers cannot make a ticket unreadable.

#### The shareable-entity contract

`TicketStore` implements `ShareableEntityStore<Ticket>` (`api/.../datamodule/ShareableEntityStore.kt:24`), which is what lets the sharing engine treat a ticket as a data identity while staying type-open:

- **`entityTypeIri`** is `https://schema.org/Ticket` — the discriminator written on typed share records and notifications.
- **`shareTarget(entityUri)`** (`TicketStore.kt:54`) maps `…/{uuid}/ticket#this` to `…/{uuid}/`: a person-to-person share grants on the ticket's own container, so one authorization covers the document, the artifact and the images (WAC `acl:default` / ACP member policies make members
- **`publicShareTarget(entity)`** (`TicketStore.kt:63`) is the original artifact (`entity.artifactUri`) — the single file an anyone-with-the-link receiver can consume without a Solid client — and `null` when there is no artifact or the pass carries `solidshare:sharingProhibited`, in which case there is deliberately no public form at all.
- **`displayName(entity)`** is the title, used to label shares and notifications.
- **`findInContainer(ownerWebId, containerUri)`** resolves the ticket inside a container someone shared, by followed links only (below).

How it flows

#### Creating a ticket with an artifact and images

`SolidTicketsDataModuleHelper.createTicket` (`api/.../tickets/implementation/SolidTicketsDataModuleHelper.kt:108`) is the toolkit's canonical create, with the ticket-specific cargo:

1. `ensureIndex` returns the index to write to, bootstrapping container, index and registration on a fresh pod.
1. `allocateEntity` mints and creates `{collection}{uuid}/`.
1. The `TicketRDF` is built at `{uuid}/ticket#this` from the `NewTicket`, stamped with `dcterms:created` and `dcterms:modified`.
1. The artifact, when given, is stored as `{uuid}/artifact{ext}` via the toolkit's `putAttachment` (extension derived from the content type, so a pkpass lands as `artifact.pkpass`), and each non-null image slot as `{uuid}/{role}.png` with content type `image/png` (`SolidTicketsDataModuleHelper.kt:268`). The returned URIs go into the document **before** it is written — the document is never published pointing at files that do not exist.
1. The document is created, and `updateIndex` adds the row under compare-and-set.

#### Updating and reindexing

`update` runs a `casUpdate` on the document (`SolidTicketsDataModuleHelper.kt:159`): re-read, apply `setTicketData`, write with `If-Match`, retry on conflict. `setTicketData` has replace semantics — every fragment node and optional property is rebuilt from the snapshot, and anything absent is gone. Only the type triple, `dcterms:created`, `solidshare:artifact`, `solidshare:artifactVerified` and the image links survive, because they belong to the resource lifecycle rather than the ticket's editable content (`TicketRDF.kt:184`); a document that never had `dcterms:created` gets one backfilled. The fresh document then goes through `reindex` (`SolidTicketsDataModuleHelper.kt:247`), which replaces the cached row — or adds it if the index had somehow lost it.

`putArtifact` (`SolidTicketsDataModuleHelper.kt:179`) reuses the ticket's existing artifact URI so the file is overwritten in place and no link moves; a ticket that never had an artifact gets one at `{uuid}/artifact{ext}`. Provided image roles are re-uploaded and merged link-wise — roles absent from the call keep their stored files — then the document is CAS-updated with the links and a fresh `dcterms:modified`, and the row is re-cached.

#### Deleting

`delete` removes the ticket's whole `{uuid}/` container — document, artifact and images in one tolerant container delete — then drops its index row under compare-and-set.

#### Reading a ticket out of a shared container

A receiver holds only "somebody granted me this container". `findInContainer` (`TicketEngine.kt:25` over `SolidTicketsDataModuleHelper.findTicketInContainer:97`) delegates to the toolkit's `findEntity`: the membership is listed, a member named `ticket` is read typed, and otherwise each member document is scanned for a subject typed `schema:Ticket` — a container whose writer named the document differently still resolves, from a foreign pod, with no guessed names. When the scan already read the document, its quads are wrapped into a `TicketRDF` rather than fetched again.

Failure behaviour

- **A missing artifact is a shape, not an error.** `publicShareTarget` returns `null` and the consumer offers no public link; `putArtifact` mints a URI and creates the link; `getArtifact` on a dangling link fails with `NOT_FOUND` like any read. derives the artifact name from the document name; stored image roles are skipped for it (`SolidTicketsDataModuleHelper.kt:198`) — there is no per-ticket container to put them in, and scattering role-named files through the shared collection was rejected.
- **Delete is tolerant end to end.** If the document cannot be read first, the container (or document) is still removed and the index row still cleared; the returned `Ticket` is then an empty-titled shell rather than a failure (`SolidTicketsDataModuleHelper.kt:236`).
- **Index reconciliation is continuous, not a repair job.** `reindex` re-adds a row that went missing; `list` swallows a broken or missing index document and simply reads the others (`SolidTicketsDataModuleHelper.kt:84`); removing a row that is not there is a no-write. All index rewrites ride the toolkit's compare-and-set, so two devices reindexing concurrently do not lose rows.
- Everything else — bootstrap create races, the 404 rule, storage discovery failure — is the toolkit's behaviour, documented in [Data modules](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/#failure-behaviour).

Extension points

What the module contributes to the toolkit is exactly its `CollectionSpec` (`api/.../tickets/implementation/SolidTicketsDataModuleHelper.kt:56`), and nothing more:

| Field                | Tickets value                                       |
| -------------------- | --------------------------------------------------- |
| `registeredTypeIri`  | `schema:Ticket`                                     |
| `entityTypeIri`      | `schema:Ticket`                                     |
| `rootSuffix`         | `datamodule/tickets/`                               |
| `entityDocumentName` | `ticket`                                            |
| `entityFragment`     | `#this`                                             |
| `indexDocumentName`  | `index`                                             |
| `indexCodec`         | `TicketsIndexRDF`                                   |
| `newIndex`           | An empty JSON-LD `TicketsIndexRDF` at the given URI |

Unlike contacts, `registeredTypeIri` and `entityTypeIri` are the same class: the type index registers the index document *for* `schema:Ticket`, and each entity *is* one. The RDF codecs (`TicketRDF`, `TicketsIndexRDF`) and the store verbs are the module's own work; every seam it plugs into — `EntityCollection`, the attachment helpers, `ShareableEntityStore` — belongs to the toolkit and is documented in [Data modules](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/index.md).

Tests

`api/src/test/java/com/erfangholami/androidsolidservices/api/datamodule/tickets/TicketEngineTest.kt` runs `TicketEngine` against the shared `inMemoryPod` fixture and pins the ticket-specific decisions: create puts the document, `artifact.pkpass` and only the provided `{role}.png` files in one fresh `{uuid}/` container, caches the index row and registers the index in the private `solid:instanceContainer` registration is migrated onto its existing `index.ttl`, old flat rows and new per-container rows sharing it; delete removes the whole per-ticket container, while a update re-caches the row; `copiedFrom` provenance survives the create round trip; `findInContainer` resolves by the conventional document name, falls back to type-scanning for unconventional names, and fails `NOT_FOUND` on a ticketless container; the shareable-entity contract maps both layouts' share targets and withholds the public artifact when it is absent or the pass prohibits sharing; and `putArtifact` overwrites the artifact and the provided image roles in place while untouched roles keep their bytes and links. The toolkit behaviour underneath is pinned separately in `EntityCollectionTest` — see [Data modules](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/#tests).

Specifications

- [schema.org `Ticket`](https://schema.org/Ticket) and its reservation family (`FlightReservation`, `TrainReservation`, `EventReservation`, …). Deliberate deviations, each justified term-by-term in the SolidShare app repository's `documents/TICKET_VOCAB.md`: a vocabulary is minted under `https://solidshare.app/ns#` only where schema.org confirmably has nothing — barcodes (`schema:Barcode` is an image class with no payload or symbology), scheduled-versus-actual times, pass presentation, and the `Detail` no-data-lost catch-all are the largest families. The primary barcode payload is mirrored into `schema:ticketToken` (`TicketRDF.kt:246`) so a plain schema.org reader still sees it. Two pending schema.org terms are used knowingly (`BoatReservation`/`BoatTrip` have no stable alternative; `schema:provider` supersedes `Flight.carrier`), and `FlightReservation`-scoped terms are reused on train and bus reservations because `domainIncludes` is non-constraining.
- [Solid type index](https://github.com/solid/type-indexes) — discovery via `solid:instance` for `schema:Ticket`, with one deliberate bend: the registration points at the tickets *index document* rather than at each ticket instance, trading strict instance semantics for one `solid:instanceContainer` form is still read and is migrated on first write.
- [Solid Protocol](https://solidproject.org/TR/protocol) — inherited through the resource manager and the toolkit; nothing ticket-specific is added on top.
