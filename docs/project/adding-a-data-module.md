# Data modules

A data module is a typed collection of things on a pod — tickets, contacts, and whatever comes
next — that the library reads and writes on an app's behalf. Every module is the same machine with
different cargo: a container to live in, a registration so it can be found again, an index
document listing its rows, one container per entity, binary attachments beside each entity. That
machine is written once in `api/datamodule/core/`; a module contributes a description of itself
and its own RDF, and inherits the rest.

## Pod shape

Every module allocates below a single framework-owned root, so a pod accumulates one folder for
app data rather than one per module:

```
{storage}
├── datamodule/                     ← framework-owned, created on first bootstrap
│   ├── tickets/
│   │   ├── index                   ← registered in the type index for schema:Ticket
│   │   └── {uuid}/                 ← one container per ticket
│   │       ├── ticket              ← the schema:Ticket document, subject `#this`
│   │       ├── artifact.pkpass
│   │       └── logo.png, strip.png, …
│   └── contacts/
│       └── {bookUuid}/             ← an address book
│           ├── index.ttl#this      ← vcard:AddressBook
│           ├── people.ttl          ← the book's name/email index
│           ├── groups.ttl
│           └── Person/{uuid}/
│               ├── index.ttl#this  ← vcard:Individual
│               └── photo.jpg
└── solidshare/                     ← sharing bookkeeping; not a data module
```

The root lives in `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/datamodule/Constants.kt:13`
as `DATA_MODULE_ROOT`, and each module appends only its own segment
(`TICKETS_DIRECTORY_SUFFIX`, `CONTACTS_DIRECTORY_SUFFIX`). A module cannot invent a new top-level
folder even by accident, because it never sees the storage root — `CollectionSpec.rootSuffix`
already carries the prefix.

Two document names are extension-less on purpose. `index` and `ticket` are linked data reached by
URL: the type index links to the index document and its rows link to each ticket, so the URI never
encodes a representation. The module reads and writes JSON-LD; how the server persists it is the
server's business. Discovery always follows the registered URL, whatever it happens to be named.

### Existing pods are not relocated, and that is a decision

A pod that registered `{storage}tickets/` before the `datamodule/` root existed keeps working
untouched. Only newly bootstrapped containers use the new root.

This is deliberate, not laziness. A container's URI **is** its identity. Relocating a live
container would break every share link that points at it, invalidate every WAC authorization and
ACP policy written against the old URI, and turn every receiver's stored row in their
`received_shares.ttl` into a 404. Nothing about the move is local to the owner's pod.

Before the first release shipped, the older flat layouts and their `solid:instanceContainer`
registrations were deleted outright rather than carried: no user data exists at the old
locations, so the engine reads exactly one registration form (`solid:instance`) and one layout.
The no-relocation rule above is what will protect *future* layout ideas from breaking published
pods; it starts applying the day real pods exist.

## Public surface

### `CollectionSpec` — what a module contributes

`api/src/main/java/com/erfangholami/androidsolidservices/api/datamodule/core/CollectionSpec.kt:27`

| Field | Meaning |
|---|---|
| `registeredTypeIri` | The class registered in the type index, whose instance is the index document. Tickets register `schema:Ticket`; contacts register `vcard:AddressBook`. |
| `entityTypeIri` | The class one entity carries, used to locate an entity inside a container someone shared. |
| `rootSuffix` | Path appended to a storage root when allocating, always below `datamodule/`. |
| `entityDocumentName` | Document name minted for an entity inside its own container (`ticket`, `index.ttl`). |
| `entityFragment` | Fragment identifying the entity's primary subject (`#this`). |
| `indexDocumentName` | Document name minted for the index when bootstrapping. |
| `indexCodec` | The RDF type the index document is read as. |
| `newIndex` | Builds an empty index document at a given URI. |

The tickets instantiation is a single literal, at
`api/.../datamodule/tickets/implementation/SolidTicketsDataModuleHelper.kt:56`.

### `EntityCollection` — what a module inherits

| Verb | What it does |
|---|---|
| `indexes(ownerWebId)` | Every index document registered for this module. |
| `ensureIndex(ownerWebId, storage, isPrivate, container)` | Returns the index to write to, bootstrapping the container, creating the index and registering it when the pod has none. |
| `indexFor(ownerWebId, entityUri)` | The index holding an entity, resolved by matching containers rather than guessing a name. |
| `readIndex` / `updateIndex` | Read the index; rewrite it under compare-and-set, with the mutation returning `false` to skip the write. |
| `allocateEntity(ownerWebId, indexUri)` | Allocates and creates `{collection}{uuid}/` and returns its container, document and subject URIs as an `EntityLocation`. |
| `entityContainerOf(documentUri)` | The container holding the whole entity — every entity owns `{collection}/{uuid}/`. |
| `findEntity(ownerWebId, containerUri)` | Locates this module's single entity inside a container that was shared with us. |

### `PodCollections` — the free functions

`api/src/main/java/com/erfangholami/androidsolidservices/api/datamodule/core/PodCollections.kt`

- `registeredInstances(rm, ownerWebId, classIri)` — both type indexes, flattened.
- `putAttachment(rm, ownerWebId, container, role, contentType, body)` — stores a binary as
  `{container}{role}{ext}` and returns its URI. The name carries the *role*, not the bytes'
  identity, so replacing an attachment overwrites in place instead of accumulating orphans.
- `putBinary(rm, ownerWebId, uri, contentType, body)` — stores at an exact URI, for when the URI
  is already decided and re-deriving the name would move the file and orphan the link.
- `readAttachment(…)` — reads bytes plus the content type the pod reports.
- `deleteTolerant(…)` — deletion is idempotent by intent, so "already gone" is success.
- `requireStorage(rm, ownerWebId, storage)` — the caller's choice, else the one the profile
  advertises.
- `extensionForContentType(contentType, fallback)` — **one** table for every module. A contact
  photo and a pass artifact are the same problem, and two tables meant two answers for the same
  bytes. Modules that name files after a known role pass `""`; those that cannot pass `.bin`.
- `findEntityInContainer(…)` — the receiving half of entity sharing.
- `containerOf(documentUri)`, `String.ensureTrailingSlash()`.

## How it flows

### Bootstrapping and registering

1. `ensureIndex` reads both type indexes and collects the registered instances for
   `registeredTypeIri`.
2. If an instance already exists — and matches `container` when the caller pinned one — it is
   returned unchanged. A pod is never re-bootstrapped.
3. Otherwise the target container is the caller's `container`, else `{storage}{rootSuffix}` with
   the storage discovered from the profile.
4. `ensureContainer` creates the chain bottom-up, so the extra `datamodule/` level costs nothing.
5. The empty index is created. `CONFLICT` and `PRECONDITION_FAILED` are swallowed: another client
   creating it first is a success, not a failure.
6. `TypeIndexResolver.addInstance` registers the index document.

### Creating an entity with attachments

Reading `SolidTicketsDataModuleHelper.createTicket` (`:108`) top to bottom is the canonical
example:

1. `ensureIndex` yields the index URI.
2. `allocateEntity` mints `{collection}{uuid}/` and creates the container.
3. The module builds its own RDF at `location.subjectUri` — this is the part only the module can
   write.
4. `putAttachment` stores the artifact and each image role beside the document, and the returned
   URIs go into the RDF before it is written, so the document is never published pointing at
   files that do not exist yet.
5. `create` writes the document.
6. `updateIndex` adds the row under compare-and-set.

### Finding an entity someone shared with you

`findEntityInContainer` (`PodCollections.kt:36`) is given only "somebody granted me this
container". Every hop is a followed link, never a guessed name:

1. `listContainer` lists the membership.
2. If a member matches the conventional document name, that is the answer, and `raw` is left
   `null` so the caller can do its own typed read.
3. Otherwise each non-container member is read and scanned for a subject typed `entityTypeIri`.
   A module whose documents were named differently still resolves.
4. Nothing matches: a 404 naming the type and the container.

## Failure behaviour

- **404 means absent, everywhere.** `read` returning `NOT_FOUND` is data, not an error, and
  `deleteTolerant` treats it as success. The rule is uniform so a module never has to decide.
- **Index create races** are expected: `CONFLICT` and `PRECONDITION_FAILED` on the bootstrap
  create are swallowed (`EntityCollection.kt:150`).
- **Index rewrites are compare-and-set.** `updateIndex` delegates to `casUpdate`, which re-reads
  and retries on `If-Match` failure, so two devices adding rows concurrently do not lose one.
- **A missing storage is programmer error**, not a pod condition: `requireStorage` throws with the
  WebID in the message rather than silently allocating somewhere else.

## Adding a module

What you write:

1. **Models** in `Shared/.../shared/model/{module}/` — the domain types and a `Constants.kt`
   whose directory suffix is `DATA_MODULE_ROOT + "{segment}/"`.
2. **RDF codecs** in `Shared/.../shared/rdf/{module}/` — one `RDFResource` subclass for the entity
   and one for the index. This is the real work, and it is the part that is genuinely yours.
3. **A `CollectionSpec`** — nine fields, one literal.
4. **A store interface** in `api/datamodule/{module}/` with the verbs your callers need, plus an
   engine that implements them against `EntityCollection` and your codecs.
5. **`ShareableEntityStore`** on that store if the module's entities should be shareable as data
   identities (`api/src/main/java/.../api/datamodule/ShareableEntityStore.kt`): the entity type
   IRI, how an entity URI maps to its share target, an optional public target, and a display name.
6. **A facade** — `SolidXDataModule` — exposing the role interfaces.

What you inherit: container bootstrap, type-index registration, UUID
allocation and per-entity layout, index row caching with CAS rewrite, attachment naming and
storage, tolerant delete, `findInContainer`, foreign-pod reads, and the 404 rule.

What still has to be edited, honestly:

- **Module registration** — the facade has to be constructible, so the `getInstance(...)` factory
  and the app's DI module gain a line.
- **The IPC layer** — a cross-process consumer needs one typed AIDL interface for the module's
  verbs, an `:app` service stub and a `:client` SDK class. The per-return-type cost is gone:
  every verb takes one of the two generic callbacks (`IASSParcelableCallback` /
  `IASSParcelableListCallback`), results travel in the Bundle envelope owned by
  `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/ipc/IpcEnvelope.kt` (which
  also owns setting the Bundle class loader — no call site does), stubs answer through the
  `dispatch*` helpers in `app/.../services/AidlDispatch.kt`, and the SDK suspends through the
  bridges in `client/.../sdk/CallbackBridges.kt` (`suspendParcelable`, `suspendParcelableList`,
  `suspendUnit`, …). A new module writes no callback types and no `Stub()` bridges.

Anything beyond those two is a defect in the toolkit rather than a cost of the module.

## Tests

`api/src/test/java/com/erfangholami/androidsolidservices/api/datamodule/core/EntityCollectionTest.kt`
pins the decisions rather than the mechanics: that bootstrapping allocates under `datamodule/` and
registers the index; that an existing registration is reused rather than reallocated; that
entities get distinct containers; that the index of an entity resolves by matching its collection
container; and that an entity is found in a shared container both by convention and by type.

`api/src/test/java/com/erfangholami/androidsolidservices/api/testing/InMemoryPodResourceManager.kt`
is the fixture. `inMemoryPod(webId, privateTypeIndexUri, publicTypeIndexUri, …)` seeds the
identity and both type indexes, with optional lambdas for pre-registering instances, so a module's
engine test starts at its first real assertion instead of fifty lines of preamble.

## Specifications

- [Solid Protocol](https://solidproject.org/TR/protocol) — LDP containers, resource identity and
  the rule that a URI is not a filename.
- [Solid type index](https://github.com/solid/type-indexes) — registration-based discovery; data
  modules register and read `solid:instance` only (sharing's own bookkeeping still uses the
  container form).
- [Shape Trees](https://shapetrees.org/TR/specification/) — not implemented; a module's layout is
  described by its `CollectionSpec` in code rather than by a published shape tree. Named here so a
  reader can tell an omission from a decision: this is a decision, revisited when a second client
  needs to write the same collections.
