# Contacts

Address books, contacts and groups, stored on the user's pod as standard vCard RDF. You work with Kotlin data classes; the module handles the RDF, the indexes and the discovery.

Because the layout matches the one SolidOS uses, contacts your app writes show up in other Solid contact apps, and theirs show up in yours.

## What you can build

- A contacts app whose data lives on the user's pod instead of your backend.
- Import from the device address book or a `.vcf` file into a pod.
- A "share this person" feature — a contact is a shareable entity, not just a file.
- Duplicate detection when someone scans a WebID.
- Anything that needs contacts to outlive your app: the user keeps them when they switch.

## Setup

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:client:0.7.2")
}
```

```kotlin
import com.erfangholami.androidsolidservices.client.sdk.Solid

val contacts = Solid.getContactsDataModule(context)
```

Wait for the service before your first call:

```kotlin
contacts.contactsDataModuleServiceConnectionState().first { connected -> connected }
```

Calls return `T?` — `null` when there is no result — and throw `SolidException` on failure.

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:api:0.7.2")
}
```

```kotlin
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule

val contacts = SolidContactsDataModule.getInstance(authenticator)
```

Calls return `SolidResult<T>`. Use `getOrThrow()`, or `getOrNull()` where absence is fine.

Both surfaces are the same three stores — `books`, `contacts` and `groups` — so the recipes below differ only in how a result is unwrapped.

## Recipes

### Create an address book

Most apps want "the user's address book, whatever it is", not a new one every launch. `ensureDefault` returns the existing one or creates it:

```kotlin
val book = contacts.books.ensureDefault(webId)
    ?: error("could not open an address book")

val bookUri = book.uri
```

```kotlin
val book = contacts.books.ensureDefault(webId).getOrThrow()
val bookUri = book.uri
```

To create a named one explicitly:

```kotlin
val book = contacts.books.create(
    webId = webId,
    title = "Conference contacts",
    isPrivate = true,   // (1)!
)
```

1. `true` registers the book in the user's **private** type index, so only they can discover it. `false` registers it publicly — the book becomes listable by anyone who reads their profile. Private is almost always what you want.

```kotlin
val book = contacts.books.create(
    webId = webId,
    title = "Conference contacts",
    isPrivate = true,   // (1)!
).getOrThrow()
```

1. `true` registers the book in the user's **private** type index, so only they can discover it. `false` registers it publicly — the book becomes listable by anyone who reads their profile. Private is almost always what you want.

### List the books a user already has

```kotlin
val books = contacts.books.list(webId)
val allUris = books?.let { it.privateAddressBookUris + it.publicAddressBookUris }.orEmpty()
```

```kotlin
val books = contacts.books.list(webId).getOrThrow()
val allUris = books.privateAddressBookUris + books.publicAddressBookUris
```

An unreadable or missing type index reads as **empty**, not as an error — a pod that has never stored contacts is not a failure case.

### Add a contact

Contact data is one immutable snapshot built with the `contactData { }` DSL:

```kotlin
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressType
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailType
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType
import com.erfangholami.androidsolidservices.shared.model.contacts.contactData

val jane = contactData {
    fullName = "Dr. Jane A. Doe"
    name {                                   // (1)!
        given = "Jane"
        family = "Doe"
        prefix = "Dr."
    }
    nickname = "Janey"
    phone("+31 6 1234 5678", PhoneType.CELL)
    email("jane@example.com", EmailType.HOME)
    email("jane@work.example", EmailType.WORK)
    address(AddressType.HOME) {
        street = "Kerkstraat 1"
        locality = "Amsterdam"
        countryName = "Netherlands"
    }
    organizationName = "NLnet"
    title = "Senior Software Engineer"
    birthday = "1990-04-01"
    category("Friends")
    webId("https://jane.solidcommunity.net/profile/card#me")   // (2)!
    note = "Met at FOSDEM"
}
```

1. The structured name is optional — `fullName` alone is enough. Supply both when you have them; vCard consumers use the parts for sorting.
1. Recording someone's WebID is what makes `findByWebId` and person-to-person sharing work later.

Then write it:

```kotlin
val created = contacts.contacts.create(
    webId = webId,
    addressBookUri = bookUri,
    data = jane,
) ?: error("the module returned no contact")

val contactUri = created.uri
```

```kotlin
val created = contacts.contacts.create(
    webId = webId,
    addressBookUri = bookUri,
    data = jane,
).getOrThrow()

val contactUri = created.uri
```

The data must resolve to *some* display name. If you set no `fullName` and no name parts, the module falls back through nickname, first email, then first phone — and fails only if all of them are empty.

### List and read contacts

```kotlin
// Lightweight summaries, straight from the book's cached index — one request.
val summaries = contacts.contacts.list(webId, bookUri)?.contacts.orEmpty()

// The full record for one person.
val full = contacts.contacts.get(webId, contactUri)
val emails = full?.data?.emails.orEmpty()
```

```kotlin
val summaries = contacts.contacts.list(webId, bookUri).getOrThrow().contacts

val full = contacts.contacts.get(webId, contactUri).getOrThrow()
val emails = full.data.emails
```

Listing does **not** fetch each contact document — the display names are cached in the book's index, so rendering a list is one request no matter how many contacts there are.

### Update a contact

`update` replaces, it does not merge

Anything absent from the snapshot you pass is **removed** from the contact. Always derive the new snapshot from the stored one with `buildUpon { }` rather than constructing a fresh `contactData { }`, or you will silently delete the fields you did not mention.

```kotlin
import com.erfangholami.androidsolidservices.shared.model.contacts.buildUpon

val stored = contacts.contacts.get(webId, contactUri) ?: return
val updated = stored.data.buildUpon {
    phone("+31 20 123 4567", PhoneType.WORK)   // added; everything else survives
    note = "Met at FOSDEM, follow up in March"
}

contacts.contacts.update(webId, bookUri, contactUri, updated)
```

```kotlin
import com.erfangholami.androidsolidservices.shared.model.contacts.buildUpon

val stored = contacts.contacts.get(webId, contactUri).getOrThrow()
val updated = stored.data.buildUpon {
    phone("+31 20 123 4567", PhoneType.WORK)
    note = "Met at FOSDEM, follow up in March"
}

contacts.contacts.update(webId, bookUri, contactUri, updated).getOrThrow()
```

Two things the library manages for you and `update` will not clear: the photo link (use `setPhoto`/`removePhoto`) and the UID (passing `null` carries the stored one forward).

Renaming ripples automatically — the cached display name is rewritten in the book index and in every group the contact belongs to.

### Attach a photo

```kotlin
contacts.contacts.setPhoto(
    webId = webId,
    contactUri = contactUri,
    photo = jpegBytes,
    contentType = "image/jpeg",
)

// Reading it back
val photo = contacts.contacts.getPhoto(webId, requireNotNull(stored.photoUri))
```

```kotlin
contacts.contacts.setPhoto(
    webId = webId,
    contactUri = contactUri,
    photo = jpegBytes,
    contentType = "image/jpeg",
).getOrThrow()

val photo = contacts.contacts.getPhoto(webId, requireNotNull(stored.photoUri)).getOrThrow()
```

Photos travel inline over Binder

On the `client` path the bytes cross a Binder transaction, which caps at roughly **1 MB**. A larger image fails the call rather than being truncated — downscale before uploading. The `api` path has no such limit.

The upload happens before the link is written, so an interrupted call leaves at worst an orphaned image, never a contact pointing at a photo that is not there.

### Group contacts

```kotlin
val group = contacts.groups.create(webId, bookUri, "FOSDEM 2026")
contacts.groups.addMember(webId, group.uri, contactUri)
contacts.groups.removeMember(webId, group.uri, contactUri)
```

Groups are `vcard:Group` documents inside the book, so they travel with it — deleting the book deletes its groups.

### Find someone by WebID

The duplicate check before adding a scanned profile:

```kotlin
val match = contacts.contacts.findByWebId(webId, "https://jane.example/profile/card#me")
if (match?.contactUri != null) {
    // already in the pod — offer to open rather than create
}
```

```kotlin
val match = contacts.contacts.findByWebId(webId, "https://jane.example/profile/card#me")
    .getOrThrow()
```

This one is expensive

It scans every book and fetches every contact — O(books × contacts) requests. It exists for a one-off duplicate check on a scanned profile, not for a search box. An unreadable book is skipped rather than failing the whole lookup.

### Delete a contact

```kotlin
contacts.contacts.delete(webId, bookUri, contactUri)
```

```kotlin
contacts.contacts.delete(webId, bookUri, contactUri).getOrThrow()
```

This removes the contact's document, its photo, its row in the book index, and its membership in every group — in that order, so a failure part-way leaves the contact findable rather than stranded.

Deleting the whole book (`contacts.books.delete`) removes everything in it and deregisters it from the type index.

## How it flows

Creating a contact on the `client` path, end to end:

```
sequenceDiagram
    autonumber
    participant App as Your app
    participant SDK as client SDK
    participant ASS as Android Solid Services
    participant Pod as Solid pod

    App->>SDK: contacts.create(webId, bookUri, data)
    SDK->>ASS: AIDL createContact(…)
    Note over ASS: routes on webId to<br/>the right account's tokens
    ASS->>Pod: PUT Person/{uuid}/index.ttl
    Pod-->>ASS: 201 Created
    ASS->>Pod: PATCH people.ttl (compare-and-set)
    Pod-->>ASS: 205 Reset Content
    ASS-->>SDK: SolidContact
    SDK-->>App: SolidContact
```

On the `api` path the two middle participants collapse: your process talks to the pod directly. The pod-side sequence is identical, which is why the same code shape works against both.

## Errors you'll hit

| What you see                                | Why                                              | What to do                                                                                                              |
| ------------------------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| `SolidNotLoggedInException`                 | no account signed in for that WebID              | send the user back through sign-in                                                                                      |
| `SolidAppNotFoundException`                 | Android Solid Services is not installed          | prompt to [install it](https://androidsolidservices.erfangholami.com/0.7/start/install-app/index.md), or use `api`      |
| `NotPermissionException`                    | the user has not granted your app access         | the grant dialog was declined; ask again                                                                                |
| `403` on a write                            | signed in, but no write access to that container | the pod owner has to grant it — see [Sharing](https://androidsolidservices.erfangholami.com/0.7/build/sharing/index.md) |
| Empty list where you expected contacts      | the book or its index does not exist yet         | this is normal on a fresh pod; call `books.ensureDefault`                                                               |
| Fields silently disappearing after `update` | replace semantics                                | derive from the stored snapshot with `buildUpon { }`                                                                    |
| Photo call fails on a large image           | the ~1 MB Binder limit on the `client` path      | downscale first, or use `api`                                                                                           |
| `findByWebId` is slow                       | it scans every contact                           | use it once, on demand — never in a list or a search box                                                                |

## Under the hood

Everything below describes how the module behaves on the pod. You do not need it to use the API, but you will want it when something surprises you.

Pod shape and RDF

The contacts data module reads and writes a pod user's address books, contacts and contact groups in the W3C vCard ontology, laid out the way SolidOS lays them out so other Solid contact apps find the same data. It is a [data module](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/index.md), but the **nested** one: where tickets are a flat collection — one index, one entity type, one container per row — an address book is itself a container of containers (contacts under `Person/`, groups under `Group/`), with three interlinked documents at its root.

```text
{storage}datamodule/contacts/
└── {bookUuid}/                     ← one address book
    ├── index.ttl#this              ← vcard:AddressBook: owner, title, links to both indexes
    ├── people.ttl                  ← name-email index: one row per contact, cached vcard:fn
    ├── groups.ttl                  ← groups index: one row per group, cached vcard:fn
    ├── Person/
    │   └── {uuid}/                 ← one container per contact
    │       ├── index.ttl#this      ← the vcard:Individual document
    │       └── photo.jpg           ← vcard:hasPhoto target, role-named attachment
    └── Group/
        └── {uuid}.ttl              ← one vcard:Group document (no per-group container)
```

New books are allocated under the framework root: `CONTACTS_DIRECTORY_SUFFIX` is `DATA_MODULE_ROOT + "contacts/"` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/contacts/Constants.kt:8`). Books are found through the type index, never through the path, so pods that registered a book under the older `{storage}contacts/` root keep working untouched — the same [no-relocation decision](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/#existing-pods-are-not-relocated-and-that-is-a-decision) the toolkit makes for every module.

The document names keep SolidOS's `.ttl` convention because the layout is SolidOS's, but the module writes and reads `application/ld+json` bodies and lets the server negotiate the representation. The excerpts below render the exact quads the codecs write, pinned by `Shared/src/test/java/com/erfangholami/androidsolidservices/shared/rdf/contacts/ContactRDFDataTest.kt` and `api/src/test/java/com/erfangholami/androidsolidservices/api/datamodule/contacts/ContactEngineTest.kt`, as Turtle for readability.

#### The book root

From the fixture in `ContactEngineTest.kt:61` (identical to what `AddressBookEngine.createBook` writes):

```turtle
@prefix vcard:   <http://www.w3.org/2006/vcard/ns#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix acl:     <http://www.w3.org/ns/auth/acl#> .

<https://alice.pod/datamodule/contacts/b1/index.ttl#this>
    a vcard:AddressBook ;
    acl:owner <https://alice.pod/profile/card#me> ;
    dcterms:title "Contacts" ;
    vcard:nameEmailIndex <https://alice.pod/datamodule/contacts/b1/people.ttl> ;
    vcard:groupIndex     <https://alice.pod/datamodule/contacts/b1/groups.ttl> .
```

`people.ttl` holds one row per contact — the membership triple plus a cached type and display name, so a list renders without fetching every contact document (`Shared/.../shared/rdf/contacts/NameEmailIndexRDF.kt:49`). Note the direction the module writes: the *book* is the subject of `vcard:inAddressBook`. `groups.ttl` is the same pattern with `vcard:includesGroup`.

```turtle
<https://alice.pod/datamodule/contacts/b1/index.ttl#this>
    vcard:inAddressBook <https://alice.pod/datamodule/contacts/b1/Person/p1/index.ttl#this> .

<https://alice.pod/datamodule/contacts/b1/Person/p1/index.ttl#this>
    a vcard:Individual ;
    vcard:fn "Jane" .
```

#### A contact

The maximal contact from `ContactRDFDataTest.kt:30` (a fixture that predates the `datamodule/` root — discovery does not care), exactly as `ContactRDF.setContactData` writes it:

```turtle
@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .
@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .

<https://alice.pod/contacts/book1/Person/p1/index.ttl#this>
    a vcard:Individual ;
    vcard:fn "Dr. Jane A. Doe" ;
    vcard:hasName <https://alice.pod/contacts/book1/Person/p1/index.ttl#name> ;
    vcard:nickname "Janey" ;
    vcard:hasTelephone _:phone0, _:phone1 ;
    vcard:hasEmail _:email0, _:email1 ;
    vcard:hasInstantMessage _:im0, _:im1 ;
    vcard:hasAddress _:addr0, _:addr1 ;
    vcard:bday "1990-04-01"^^xsd:date ;
    vcard:anniversary "2015-06-20"^^xsd:date ;
    vcard:organization-name "NLnet" ;
    vcard:organization-unit "Grants" ;
    vcard:role "Engineer" ;
    vcard:title "Senior Software Engineer" ;
    vcard:note "Met at FOSDEM" ;
    vcard:hasCategory "Friends", "Colleagues" ;
    vcard:hasGender vcard:Female ;
    vcard:hasGeo <geo:52.3676,4.9041> ;
    vcard:hasLanguage _:lang0, _:lang1 ;
    vcard:url _:url0, _:url1 ;
    vcard:hasUID <urn:uuid:2f1c0000-0000-0000-0000-000000000001> ;
    vcard:hasPhoto <https://alice.pod/contacts/book1/Person/p1/photo.jpg> .

<https://alice.pod/contacts/book1/Person/p1/index.ttl#name>
    vcard:family-name "Doe" ;
    vcard:given-name "Jane" ;
    vcard:additional-name "A." ;
    vcard:honorific-prefix "Dr." ;
    vcard:honorific-suffix "PhD" .

_:phone0 a vcard:Cell ; vcard:value "tel:+31612345678" .
_:phone1 a vcard:Work ; vcard:value "tel:+31201234567" .
_:email0 a vcard:Home ; vcard:value "mailto:jane@example.com" .
_:email1 a vcard:Work ; vcard:value "mailto:jane@work.example" .
_:im0    a vcard:Home ; vcard:value "xmpp:jane@jabber.example" .
_:im1    a vcard:Work ; vcard:value "skype:jane.doe" .

_:addr0 a vcard:Address, vcard:Home ;
    vcard:street-address "Kerkstraat 1" ;
    vcard:locality "Amsterdam" ;
    vcard:region "NH" ;
    vcard:postal-code "1017GA" ;
    vcard:country-name "Netherlands" .

_:addr1 a vcard:Address, vcard:Work ;
    vcard:street-address "Science Park 400" ;
    vcard:locality "Amsterdam" ;
    vcard:country-name "Netherlands" ;
    vcard:post-office-box "94079" .

_:lang0 vcard:language "nl" .
_:lang1 vcard:language "en-GB" .

_:url0 a vcard:WebId ;    vcard:value <https://jane.solidcommunity.net/profile/card#me> .
_:url1 a vcard:Homepage ; vcard:value <https://jane.example> .
```

That is the full coverage the model round-trips — `ContactRDFDataTest` asserts this snapshot survives both an in-memory rewrite and a serialize-parse wire trip. The write forms worth knowing, all in `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/rdf/contacts/ContactRDF.kt`:

| Property          | Stored form                                                                                                                                                                                           |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Structured name   | Fragment node `{document}#name`, not a blank node (`ContactRDF.kt:151`)                                                                                                                               |
| Phones            | `_:phone{n}` typed `Cell/Home/Work/Fax/Pager/Voice/Text/Video/TextPhone`; value normalized to a real `tel:` IRI — RFC 3966 visual separators stripped, the rest percent-encoded (`ContactRDF.kt:312`) |
| Emails            | `_:email{n}` typed `Home/Work`; value a `mailto:` IRI                                                                                                                                                 |
| Instant messaging | `_:im{n}` typed `Home/Work`; handle kept verbatim — schemes vary too much to normalize                                                                                                                |
| Addresses         | `_:addr{n}`, always `vcard:Address` plus `Home/Work` when classified; six postal parts                                                                                                                |
| URLs              | `_:url{n}` typed `Home/Work/Homepage/WebId/PublicId`; the value is an IRI object, unlike the literal `vcard:value` of phones and emails                                                               |
| Dates             | `bday`/`anniversary` verbatim, typed `xsd:dateTime` when the value contains a `T`, `xsd:date` otherwise                                                                                               |
| Gender            | One of the five ontology classes as an IRI, never a literal                                                                                                                                           |
| Geo               | Direct IRI object per the TR; a bare `lat,lng` gains the `geo:` scheme on write; a foreign entry on a value node still reads (`ContactRDF.kt:516`)                                                    |
| Languages         | `_:lang{n}` carrying `vcard:language` BCP-47 tags; list order carries vCard `PREF` order                                                                                                              |
| UID               | An IRI: absolute IRIs verbatim, anything else wrapped `urn:uid:{percent-encoded}` so the object is always valid; unwrapped on read (`ContactRDF.kt:483`)                                              |

Two compatibility rules run through all of it: entries of type `OTHER` are written **without** a type triple, byte-compatible with older writers, and untyped or unrecognized foreign nodes read back as `OTHER` instead of being dropped. Blank-node labels are counter-based (`_:phone0`, `_:addr1`) because values like URLs and phone numbers are not safe labels.

The public surface in full

`SolidContactsDataModule` (`api/src/main/java/com/erfangholami/androidsolidservices/api/datamodule/contacts/SolidContactsDataModule.kt:19`) is a facade over three role interfaces, obtained via `getInstance(authenticator)` or `getInstance(resourceManager)`:

| Role                      | Verbs                                                                                                                                    |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `books: AddressBookStore` | `list`, `ensureContainer`, `get`, `create`, `rename`, `delete`, `ensureDefault`                                                          |
| `contacts: ContactStore`  | `get`, `list`, `create`, `update`, `delete`, `setPhoto`, `removePhoto`, `getPhoto`, `findByWebId`, plus the `ShareableEntityStore` verbs |
| `groups: GroupStore`      | `create`, `get`, `delete`, `addMember`, `removeMember`                                                                                   |

Everything returns `SolidResult<T>`. The writable state of a contact is one immutable snapshot, `ContactData`, built with the `contactData {}` DSL and derived with `buildUpon {}` (`Shared/.../shared/model/contacts/ContactData.kt:109`) — typed phones, emails, IMs, addresses and URLs, plus every scalar in the coverage table. Reads return `SolidContact` (uri, etag, modified, photoUri, data); listings carry lightweight `Contact(uri, name)` summaries straight from the cached index names, and `AddressBook` bundles the root's title with both summary lists. `ContactStore.update` has **replace semantics** — properties absent from the snapshot are removed — with two library-managed exceptions: the photo link is preserved (managed only through `setPhoto`/`removePhoto`) and a `null` uid carries the stored UID forward rather than clearing it. `create` requires the data to resolve to *some* display name — `effectiveFullName()` falls back through name parts, nickname, first email, first phone (`ContactData.kt:89`) — and assigns `urn:uuid:{contactId}` when no uid is supplied. `findByWebId` scans every book for a WebId-typed URL at a cost of one fetch per contact; its KDoc says O(books × contacts) and means it — it exists for duplicate-checking a scanned profile, not for hot paths.

`ContactStore` is also the module's `ShareableEntityStore<SolidContact>` implementation (`api/src/main/java/com/erfangholami/androidsolidservices/api/datamodule/ShareableEntityStore.kt`): `entityTypeIri` is `vcard:Individual`, and `shareTarget` maps a contact URI to its `Person/{uuid}/` container (`ContactStore.kt:31`) so one grant covers the document and its photo. `publicShareTarget` returns `null` unconditionally (`ContactStore.kt:38`): **contacts are never shared publicly, and that is a decision**, made at the contract level rather than left to UI policy. An anyone-with-the-link grant would disclose a document about a third person to an unbounded audience, and unlike a ticket — whose public form is its self-contained `.pkpass` artifact — a contact has no single representation that makes sense to a receiver without pod access. Shares of contacts are always person-to-person grants.

How it flows in detail

#### Bootstrapping a book

`AddressBookEngine.createBook` (`api/.../datamodule/contacts/implementation/AddressBookEngine.kt:136`) is the part the toolkit does not do for contacts, because a book is three documents, not one:

1. `ensureContainer` creates `{storage}datamodule/contacts/{uuid}/` bottom-up (the storage root resolved by the toolkit's `requireStorage` when the caller passes none).
1. The two indexes are created first — an empty `people.ttl` and `groups.ttl` — and the `index.ttl#this` root **last**, carrying `acl:owner`, `dcterms:title` and the two index links. The root is never published pointing at documents that do not exist yet, the same attachments-before-document ordering the toolkit's ticket flow uses.
1. `TypeIndexResolver.addInstance` registers the root in the private (default) or public type index for `vcard:AddressBook`.

`ensureDefault` (`AddressBookEngine.kt:102`) returns the first private-index book or creates one titled "Contacts"; `ensureContainer` (`AddressBookEngine.kt:30`) just provisions `{storage}datamodule/contacts/` and returns the (possibly empty) book list, so a consumer can call it on every read of an unprovisioned pod without failing.

#### Creating a contact with a photo

`ContactEngine.create` (`api/.../implementation/ContactEngine.kt:66`) then `setPhoto` (`ContactEngine.kt:150`):

1. A UUID is minted and `{book}Person/{uuid}/` created (`ensureContainer`, which also provisions the intermediate `Person/` on servers that do not auto-create parents).
1. The `ContactRDF` is written at `{container}index.ttl#this`, uid defaulted to `urn:uuid:{uuid}`.
1. The people index gains its row under compare-and-set (`ContactsPodAccess.updatePeopleIndex` delegates to `casUpdate`), and any requested group memberships are added.
1. `setPhoto` stores the binary through the toolkit's `putAttachment` with role `photo` — so a replacement overwrites `photo.jpg` in place — **before** CAS-linking `vcard:hasPhoto` in the document; the document never links a binary that is not there. If the link previously pointed at a different URI (a content-type change renames the file), the stale binary is deleted tolerantly afterwards.

Renames ripple: when an `update` changes the display name, `ContactsPodAccess.refreshCachedName` (`ContactsPodAccess.kt:54`) rewrites the cached `vcard:fn` in the people index and in every group document of the book, each under CAS.

#### Deleting a contact and its group memberships

`ContactEngine.delete` (`ContactEngine.kt:125`) runs strictly in this order:

1. Read the contact once, best-effort, so the removed entity can be returned.
1. `deleteTolerant` the whole `Person/{uuid}/` container — document and photo in one stroke. If this fails, the call fails **here**, and the index row survives to keep the contact discoverable (pinned by `ContactEngineTest`).
1. Remove the people-index row under CAS.
1. Only when a row was actually removed, walk the book's groups index and strip the membership (and its cached name) from each group document.

`AddressBookStore.delete` is the cascade one level up: the whole book container first, the type-index deregistration second — so a failed container delete leaves the registration intact and the data still findable.

#### Finding a contact inside a shared container

A receiver granted someone's `Person/{uuid}/` container calls `contacts.findInContainer(ownerWebId, containerUri)` (`ContactEngine.kt:38`), which is the toolkit's `findEntityInContainer` parameterized with document name `index.ttl`, fragment `#this` and type `vcard:Individual`: membership is listed, the conventional name wins when present (followed by a typed re-read), otherwise every member document is scanned for a subject typed `vcard:Individual`. Every hop is a followed link against the foreign pod; nothing is a guessed path.

Failure behaviour

- **A partially written or foreign book root does not crash.** `AddressBookRDF.getOwner`, `getTitle`, `getNameEmailIndex` and `getGroupsIndex` all return `null` when the triple is absent (`Shared/.../shared/rdf/contacts/AddressBookRDF.kt:39`), and every caller treats absence as empty: `readBook` substitutes empty index codecs (`AddressBookEngine.kt:123`), `fetchAll` returns an empty list (`ContactEngine.kt:229`), and the index-update helpers silently skip a book that declares no index (`ContactsPodAccess.kt:75`). These reads used to be non-null and a book root missing its links crashed the listing; the null path is now pinned explicitly by `AddressBookRDFTest`.
- **A missing people index means "no contacts", not an error.** `contacts.list` returns empty when the book or its people index reads `NOT_FOUND` (`ContactsPodAccess.dataOrNullIfMissing`, `ContactsPodAccess.kt:114`) — only genuinely other errors surface. Likewise `books.list` returns empty lists when a type index is unreadable, and `findByWebId` skips an unreadable book instead of sinking the whole lookup, returning a clean `ContactMatch()` miss.
- **A photo that will not load fails only the photo.** `vcard:hasPhoto` is just a link, so the contact document reads fine with a dangling target; `getPhoto` returns the pod's error as a `SolidResult.Failure` for that call alone. The upload-before-link ordering in `setPhoto` and the unlink-before-delete ordering in `removePhoto` mean a crash between steps leaves at worst an orphaned binary, never a document pointing at nothing.
- **Index rewrites are compare-and-set** through `casUpdate`, so two devices adding contacts to the same book do not lose each other's rows — the toolkit's rule, inherited unchanged.
- **Cascade order protects discoverability.** Both delete flows destroy data before bookkeeping (container before index row, container before type-index registration), so any failure leaves the entity findable rather than leaking an unreachable container.

Extension points

What contacts takes from the [toolkit](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/index.md) versus what it keeps, precisely:

- **Used from the toolkit**: the type-index registry verbs (`TypeIndexResolver.addInstance` / `removeResource` — books register as `vcard:AddressBook` instances), storage resolution (`requireStorage`), attachment storage for photos (`putAttachment` / `readAttachment` with the shared content-type table), `deleteTolerant`, `findEntityInContainer`, `containerOf`, and `casUpdate` for every index and document rewrite.
- **Kept module-specific**: the three-document book bootstrap, the people/groups index row formats with their cached names, the rename ripple, the two-level cascade deletes, and the `Person/` / `Group/` nesting itself. `EntityCollection` assumes one index document with rows of one entity type per collection container; a book root that *links* two sibling indexes and parents two entity families does not reduce to that, so contacts does not instantiate a `CollectionSpec` at all.

The seams a consumer plugs into: `ShareableEntityStore` makes contacts first-class in typed entity sharing (the sharing engine threads `vcard:Individual` and the display name as opaque strings); the `ContactData` DSL is the write model for every source an app builds (device import, `.vcf`, scanned profiles); and the cross-process mirror lives in `client/src/main/java/com/erfangholami/androidsolidservices/client/sdk/SolidContactsDataModule.kt` for apps consuming the module over IPC. `addressBookUriForContact` (`Shared/.../shared/model/contacts/ContactUris.kt:3`) maps a contact URI back to its book root by the `Person/` segment, for callers that hold only the contact.

Tests

`api/src/test/java/com/erfangholami/androidsolidservices/api/datamodule/contacts/ContactEngineTest.kt` runs all three engines against the shared `inMemoryPod` fixture and pins the decisions: that `create` registers the row in the people index and assigns a `urn:uuid:` uid; that `update` refreshes the cached name in the people index *and* in group documents while preserving the photo link and a `null`-uid carry-forward; that `delete` cleans index, memberships and the whole `Person/` container — and that a failed container delete surfaces and keeps the index row; that `findByWebId` hits with its book and misses cleanly; that `ensureDefault` reuses before creating; that a missing people index, book root or type index reads as empty rather than failing; that `ensureContainer` and `create` provision the nested container chain; that a book delete removes container plus registration, keeping the registration when the container delete fails; and that `findInContainer` and the `shareTarget` mapping honor the shareable-entity contract.

`Shared/src/test/java/com/erfangholami/androidsolidservices/shared/rdf/contacts/AddressBookRDFTest.kt` pins the codec's failure posture: a book that declares nothing reads as all-null rather than throwing, a fully described book reads every link back, and the legacy `https` `dcterms:title` variant still reads.

The full-fidelity round-trip itself — the maximal snapshot surviving rewrite and wire trip, OTHER entries written untyped, legacy untyped nodes reading as OTHER, counter-based blank-node labels, date datatypes by value shape — is pinned by `ContactRDFDataTest.kt` beside the codec.

Specifications implemented

- [vCard Ontology (W3C)](https://www.w3.org/TR/vcard-rdf/) — the vocabulary for everything on a contact. Deliberate deviations: multi-valued entries live on blank nodes carrying a `vcard:value` and a classifying `rdf:type` (the SolidOS convention this module must read and write to coexist with SolidOS contacts) rather than the TR's direct-object forms; and the book layout terms — `vcard:AddressBook`, `nameEmailIndex`, `groupIndex`, `inAddressBook`, `includesGroup` — are SolidOS extensions in the vCard namespace, not defined by the TR at all. One module-internal quirk to know: the people index writes the *book* as the subject of `vcard:inAddressBook`, and reads only its own form.
- [RFC 6350 (vCard 4.0)](https://www.rfc-editor.org/rfc/rfc6350) — the property semantics the model mirrors: typed TEL/EMAIL/ADR/IMPP, `GEO` as a `geo:` URI, `LANG` preference order, `GENDER` reduced to the ontology's five classes (the RFC's free-text identity component is not modeled), `UID` always an IRI. Not implemented: `RELATED` (the vocabulary constant exists at `Shared/.../shared/vocab/VCARD.kt:71`, no codec support), `TZ`, `KEY`, `LOGO`, `SOUND` — none of the module's sources (device contacts, `.vcf` files in the wild, scanned profiles) produce them and no consumer renders them. Because updates mutate the fetched quad set in place, foreign triples using those properties survive a rewrite untouched; they are invisible to `ContactData`, not destroyed by it.
- [Solid type index](https://github.com/solid/type-indexes) — book discovery and registration, private by default, public on request; the module follows registrations, never paths, which is what lets pre-`datamodule/` books stay where they are.
