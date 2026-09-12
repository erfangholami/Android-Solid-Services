# Sharing

Give someone access to something on the pod, see everything you have shared, and see what has been shared with you.

Access is enforced by the pod itself — Web Access Control, or Access Control Policy on servers that use it. This layer writes those rules for you and keeps an index so listing what you have shared does not mean crawling the whole pod.

## What you can build

- "Share this with…" on any resource, container, contact or ticket.
- A shared-with-me view that survives reinstalling your app, because the index is on the pod.
- Share links for people who are not in your address book.
- A request-to-share flow: publish what you own, let others ask, approve or decline.
- Revocation the user can actually see and trust.

## Setup

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:client:0.7.2")
}
```

```kotlin
import com.erfangholami.androidsolidservices.client.sdk.Solid

val sharing = Solid.getSharingClient(context)
sharing.connectionState().first { connected -> connected }
```

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:api:0.7.2")
}
```

```kotlin
import com.erfangholami.androidsolidservices.api.sharing.SharingManager

val sharing = SharingManager.getInstance(authenticator)
```

## The two things every call needs

```kotlin
enum class ShareMode { READ, APPEND, WRITE }   // shown to users as View / Add / Edit

sealed class ShareReceiver {
    data class WebIdReceiver(val webId: String)     // one person
    data class GroupReceiver(val groupUri: String)  // a vcard:Group — members inherit
    data object Public                              // anyone with the URL
}
```

`APPEND` is the drop-box mode: the receiver can add new items but cannot read or change what is already there. A grant writes the full set of implied modes — Add is Read + Append, Edit is Read + Write — and the index collapses them back to one mode per receiver when you read it.

## Recipes

### Share something with someone

```kotlin
sharing.createShare(
    webId = webId,
    resourceUri = "${storage}notes/trip.ttl",
    mode = ShareMode.WRITE,
    receiver = ShareReceiver.WebIdReceiver("https://bob.example/profile/card#me"),
    notifyReceiver = true,   // (1)!
)
```

1. Delivers an offer to their pod inbox so it shows up without them knowing the URL. Delivery is best-effort — the access change already happened either way.

```kotlin
sharing.createShare(
    webId = webId,
    resourceUri = "${storage}notes/trip.ttl",
    mode = ShareMode.WRITE,
    receiver = ShareReceiver.WebIdReceiver("https://bob.example/profile/card#me"),
    notifyReceiver = true,
).getOrThrow()
```

Sharing a **container** shares its members too, which is how you hand over a folder rather than a file at a time.

### Share with anyone who has the link

```kotlin
sharing.createShare(webId, resourceUri, ShareMode.READ, ShareReceiver.Public)
```

Public means public

The resource becomes readable by anyone who learns the URL — there is no second factor. Some entity types refuse this outright: a contact can never be shared publicly, because it is a document about a third person.

### List what you have shared

```kotlin
val given = sharing.getStoredGivenShares(webId)      // fast: reads the on-pod index
val fresh = sharing.refreshGivenShares(webId)        // re-validates against live ACLs
```

Use the stored read for rendering and the refresh for a pull-to-refresh. For one resource, `getGivenSharesForResource` skips the index and reads the ACL directly — the authoritative answer.

### List what has been shared with you

```kotlin
val received = sharing.getStoredReceivedShares(webId)
val fresh = sharing.refreshReceivedShares(webId)

// Someone sent you a link or a QR code:
sharing.addReceivedShare(webId, resourceUri)
```

### Change or withdraw access

```kotlin
sharing.updateShare(webId, resourceUri, ShareMode.READ, receiver)   // downgrade
sharing.revokeShare(webId, resourceUri, receiver)                    // remove entirely
sharing.makePrivate(webId, resourceUri)                              // strip every share at once
```

Revoking sends a best-effort withdrawal notification so the other side's list updates too.

### Clean up shares for resources you are deleting

Deleting a resource takes its ACL with it, but the share index sits beside the pod root and would outlive it, leaving records pointing at URIs that no longer resolve:

```kotlin
sharing.purgeGivenShares(
    webId = webId,
    resourceUri = containerUri,
    includeDescendants = true,
    notifyReceivers = true,
)
```

Run this **before** the delete, so receivers are told while the resource still exists.

### Publish what others may ask for

A public catalogue of things you own. The entries are public; the resources stay private until you grant access:

```kotlin
sharing.publishCatalogEntry(
    webId,
    CatalogEntry(
        resourceUri = uri,
        title = "Trip notes",
        description = "Ask if you want in",
        depictionUri = null,
    ),
)

val theirs = sharing.getOwnerCatalog(viewerWebId = webId, ownerWebId = otherPerson)
sharing.removeCatalogEntry(webId, uri)
```

### Answer an access request

Requests arrive through the [inbox](https://androidsolidservices.erfangholami.com/0.7/build/notifications/index.md):

```kotlin
val requests = notifications.listRequests(webId)

sharing.acceptShareRequest(webId, requests.first())          // creates the share, notifies them
sharing.rejectShareRequest(webId, requests.first(), "not this one")
```

### Repair an index that has drifted

```kotlin
val rebuilt = sharing.rebuildGivenIndex(webId)
```

This walks the whole pod

One request per resource — minutes on a populated pod, and IPC carries no cancellation, so stopping only abandons your side of the wait. Expose it as an explicit user action, never as something that runs on a screen opening.

## How it flows

```
sequenceDiagram
    autonumber
    participant App as Your app
    participant SDK as SDK
    participant Pod as Your pod
    participant Their as Their pod

    App->>SDK: createShare(uri, WRITE, bob)
    SDK->>Pod: HEAD uri — which backend?
    Pod-->>SDK: Link rel="acl" (WAC) or rel="acl" on an ACP origin
    SDK->>Pod: PUT/PATCH the ACL or ACR
    Note over SDK,Pod: writes implied modes:<br/>Edit = Read + Write
    Pod-->>SDK: 205
    SDK->>Pod: PATCH the given-shares index
    SDK->>Their: POST an Offer to bob's inbox
    Their-->>SDK: 201 (best effort)
    SDK-->>App: GivenShare
```

## Errors you'll hit

| What you see                         | Why                                                  | What to do                                           |
| ------------------------------------ | ---------------------------------------------------- | ---------------------------------------------------- |
| `AccessDeniedException`              | you are not the owner, or the server refused         | only an owner can change access                      |
| `UnsupportedAuthBackendException`    | the server speaks neither WAC nor ACP                | sharing is unavailable there                         |
| `NoInboxException`                   | the receiver advertises no inbox                     | the share still worked; only the notification failed |
| `StaleAclException`                  | the ACL changed under a conditional write            | re-read the shares and reapply                       |
| `ImpersonationDetectedException`     | an inbox message claimed a sender it could not prove | drop it — the spoofing gate did its job              |
| Index shows a share the ACL does not | the ACL was changed outside this app                 | `refreshGivenShares`, or `rebuildGivenIndex`         |
| Shares outliving deleted resources   | the index was not purged first                       | `purgeGivenShares` before deleting                   |
| `rebuildGivenIndex` appears to hang  | it is a full pod walk                                | expected; make it an explicit action                 |

## Under the hood

Sharing is the engine that turns "give Bob access to this" into durable, revocable state: it writes the access grant through the pluggable WAC/ACP backend (see [Access control](https://androidsolidservices.erfangholami.com/0.7/build/access-control/index.md)), records what was shared in a private bookkeeping index on the owner's pod, mirrors what was received in a matching index on the receiver's pod, and posts the courtesy notification (see [Notifications](https://androidsolidservices.erfangholami.com/0.7/build/notifications/index.md)). The engine is `SharingManager` (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/SharingManager.kt:34`), implemented as two engines behind one facade — `GivenSharesEngine` for shares you created, `ReceivedSharesEngine` for shares created for you — over a `SharingManagerHelper` that owns the index documents. A share can carry a *data identity*: an optional RDF class IRI and title mark a row as "a ticket named X" rather than "a folder", and those marks travel from the owner's index through the notification into the receiver's index. The app-facing flows built on top — screens, QR scanning, the copy-to-own receive path — are documented in the SolidShare app repository (`documents/share.md` for the sharing feature, `documents/ENTITY_SHARING.md` for entity shares); this page covers the library.

Pod shape

Sharing bookkeeping lives under `{podRoot}solidshare/` — beside, not inside, the `datamodule/` tree, because it is per-account state rather than a typed collection:

```text
{podRoot}
└── solidshare/
    ├── shares/                     ← owner-only ACL, asserted on every bootstrap
    │   ├── given_shares.ttl        ← what this account has shared
    │   └── received_shares.ttl     ← what has been shared with this account
    └── catalog.ttl                 ← public-read; only when the profile enables the catalog
```

The paths come from `ShareStorageLayout` (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/ShareStorageLayout.kt:19`) and the name constants in `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/Constants.kt:12`. The container is `solidshare/` rather than a dot-prefixed name because Community Solid Server reserves the `/.*` namespace for itself and answers 403 for anything under it. Bootstrap (`SharingManagerHelper.ensurePrivateSharesContainer`, `api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/SharingManagerHelper.kt:111`) creates the containers, resets `shares/` to owner-only, creates both index documents empty, and registers the container in the private type index for `solidshare:Share` — so another client of the same account can discover the bookkeeping instead of re-deriving the path.

Two honest notes on the names. The `.ttl` suffix is historical: the empty documents are created as JSON-LD (`SharingManagerHelper.kt:147`) and every later write is an N3 PATCH, so the name never encodes the representation — the same rule as the data modules' extension-less documents. And each record's node IRI is deterministic, `#share-` plus twenty hex characters of `SHA-1(counterpart|resourceUri)` (`SharingManagerHelper.kt:554`), so concurrent writers converge on the same node and a patch can delete exactly the triples it once inserted.

Each share is a reified record node, because a bare grant triple has nowhere to put a timestamp or a type. A typed given share, with the values `TypedShareEngineTest` pins:

```turtle
@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix acl:        <http://www.w3.org/ns/auth/acl#> .
@prefix dcterms:    <http://purl.org/dc/terms/> .
@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .
@prefix schema:     <https://schema.org/> .
@prefix solidshare: <https://solidshare.app/ns#> .

<#share-…>
    rdf:type                solidshare:Share ;
    solidshare:resource     <https://alice.pod/tickets/u1/> ;
    solidshare:receiver     <https://bob.pod/profile/card#me> ;
    acl:mode                acl:Read ;
    dcterms:created         "2026-08-01T10:00:00Z"^^xsd:dateTime ;
    solidshare:resourceType schema:Ticket ;
    dcterms:title           "Coldplay — Music of the Spheres" .
```

The received index is symmetric with one substitution: `solidshare:owner` names the sender in place of `solidshare:receiver` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/rdf/sharing/ReceivedSharesIndexRDF.kt:35`). Where each term comes from:

| Term                                                                                                            | Vocabulary                                                                                                                                                                                        |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `solidshare:Share`, `solidshare:resource`, `solidshare:receiver`, `solidshare:owner`, `solidshare:resourceType` | **Minted** under `https://solidshare.app/ns#` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/vocab/SolidShare.kt:36`–`:53`) — no published vocabulary reifies a share record |
| `acl:mode` and the `acl:Read/Append/Write` IRIs                                                                 | Web Access Control                                                                                                                                                                                |
| `dcterms:created`, `dcterms:title`                                                                              | Dublin Core terms                                                                                                                                                                                 |
| `rdf:type`                                                                                                      | RDF                                                                                                                                                                                               |
| `vcard:Group` (marker on a group receiver's IRI)                                                                | vCard ontology                                                                                                                                                                                    |

`solidshare:resourceType` and `dcterms:title` appear only on typed entity shares; a plain file/folder share carries neither, and readers render absent or unknown types generically — the vocabulary is open by decision. A `ShareReceiver.Public` share stores `foaf:Agent` as the receiver IRI (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/ShareReceiver.kt:39`), and a group receiver keeps the extra `<receiver> rdf:type vcard:Group` marker so a group URI is distinguishable from a WebID on read. One record holds every `acl:mode` granted to a `(receiver, resource)` pair, which is why the pair has exactly one `dcterms:created`.

Files written by earlier releases stored a share as a bare `<receiver> <acl:Read> <resource>` triple. Those legacy rows are still read (`GivenSharesIndexRDF.getLegacyFlatShares`, `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/rdf/sharing/GivenSharesIndexRDF.kt:113`, surfacing with `createdAt = null`) and migrated to node form the next time their pair is touched — the writer deletes the flat triple in the same patch that writes the node.

Public surface

`SharingManager.getInstance(authenticator | resourceManager, profile)` — every suspend verb returns `SolidResult` and dispatches to `Dispatchers.IO` internally. The core verbs, with the line each is declared at in `SharingManager.kt`:

| Verb                                                                                                            | What it does                                                                                                                                                                |
| --------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `createShare(webId, resourceUri, mode, receiver, notifyReceiver = true, resourceType?, resourceName?)` (`:167`) | Grants through the backend, records the index row, posts a best-effort `as:Offer`.                                                                                          |
| `updateShare(…, notifyReceiver = false, …)` (`:192`)                                                            | The same write with a new mode, preserving the record's `dcterms:created`; posts `as:Update` when asked, so a silent re-grant stays silent.                                 |
| `revokeShare(webId, resourceUri, receiver)` (`:211`)                                                            | Removes the receiver's authorization and the index row; posts a best-effort `as:Undo`.                                                                                      |
| `purgeGivenShares(webId, resourceUri, includeDescendants = true, notifyReceivers = true)` (`:232`)              | Drops index rows for a resource — and everything beneath it — **without touching access control**; for calling right after deleting the resource. Returns the removed rows. |
| `getGivenSharesForResource(webId, resourceUri)` (`:141`)                                                        | Reads straight from the live ACL/ACR — authoritative, may differ from the index.                                                                                            |
| `refreshGivenShares(webId)` (`:70`)                                                                             | Re-validates every stored given share against the live ACLs and returns the reconciled index.                                                                               |
| `addReceivedShare(webId, resourceUri, ownerHint?, resourceType?, resourceName?)` (`:277`)                       | Probes access from the receiver's side and records (or removes) the received row — the QR/link path.                                                                        |
| `syncReceivedShares(webId, notifications)` (`:310`)                                                             | Reconciles the received index against a batch of gate-verified inbox notifications — the notification path.                                                                 |
| `getShareDeepLink(resourceUri, ownerWebId?, resourceType?)` (`:421`)                                            | Encodes the app link for QR codes; `parseShareDeepLink` (`:432`) reverses it, `getShareBareUrl` (`:440`) is the plain `https://` form for non-Solid scanners.               |

The typing pair rides on the three verbs that write rows: `resourceType` is the RDF class IRI of the data-module entity the share carries (`https://schema.org/Ticket`, `…vcard/ns#Individual`) and `resourceName` its human title at share time. Both land on the index record as `solidshare:resourceType` and `dcterms:title`, and both are announced on the notification's `as:object` so the receiver can render "shared a ticket" before probing (see [Notifications](https://androidsolidservices.erfangholami.com/0.7/build/notifications/#pod-shape)). Passing `null` records an untyped row on create — but never strips typing an existing record already carries: the diff treats `null` as "no change" (`SharingManagerHelper.tripleChange`, `SharingManagerHelper.kt:398`), so a plain re-grant cannot demote a ticket back to a folder.

The rest of the surface, briefly: `getStoredGivenShares` / `getStoredReceivedShares` (`:55`, `:243`) are the fast single-read forms; `rebuildGivenIndex` (`:100`) walks the whole pod tree and rebuilds the given index from live ACLs — expensive by design, an explicit user action; `refreshReceivedShares` (`:251`) re-probes each received row via `WAC-Allow` and prunes what was revoked; `removeReceivedShare` (`:288`) drops one row without touching the resource; `makePrivate` (`:132`) and `repairOwnerControl` (`:118`) are the owner-safety verbs documented in [Access control](https://androidsolidservices.erfangholami.com/0.7/build/access-control/#public-surface); `acceptShareRequest` / `rejectShareRequest` (`:347`, `:360`) turn an inbox `interop:AccessRequest` into a grant-plus- `as:Accept` or an `as:Reject`, each mirrored into the owner's own inbox as a decision memo; `getAccessGrants` (`:333`) unifies both indexes, pending requests, and — best-effort, never failing the call — grants discovered through a pod's SAI registries (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/SaiAccessGrantReader.kt:17`). `publishCatalogEntry` / `removeCatalogEntry` / `getOwnerCatalog` (`:375`, `:384`, `:398`) maintain the owner's publicly readable `catalog.ttl` — `solidshare:CatalogEntry` nodes with `dcterms:title` / `dcterms:description` / `foaf:depiction` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/rdf/sharing/CatalogRDF.kt:33`) — which lets a requester discover what to ask for while the listed resources stay private.

Share links are two encodings for two audiences. The deep link is `https://solidshare.app/s?resource=…&owner=…&type=…` (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/SolidShareLinkCodec.kt:16`): `owner` lets a receiver who scanned a QR name the sender without any notification, `type` hints the entity kind so the confirmation UI can render it before probing, and the title is never in the link — a URL is not a place for content. `parse` (`:23`) accepts both the HTTPS form and the legacy `solidshare://` custom scheme. The bare URL (`:36`) is just the resource URI, for a public share any browser can open.

How it flows

#### Creating a share

`GivenSharesEngine.createShare` (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/GivenSharesEngine.kt:208`):

1. The pod root is resolved from the profile's `pim:storage` (cached per WebID) and the bookkeeping container is bootstrapped (`SharingManagerHelper.kt:91`, `:111`).
1. **The receiver is canonicalized** (`GivenSharesEngine.kt:358`). A WebID receiver without a fragment is usually a profile-document URL, and WAC/ACP match the exact agent IRI — granting to the document misses the person. The document is read (anonymously first, then with the caller's session) and followed through `foaf:primaryTopic` — or the inverse `foaf:isPrimaryTopicOf` — preferring a fragment on the same document, so `https://bob.pod/profile/card` becomes `https://bob.pod/profile/card#me` (`GivenSharesEngine.kt:378`). A receiver that already carries a fragment skips resolution.
1. The engine notes whether this receiver already held a grant — that decides the rollback direction if step 5 fails.
1. The grant is written through the per-resource backend, with `acl:default` / `acp:memberAccessControl` inheritance for containers and the implied-mode expansion — all of which belongs to [Access control](https://androidsolidservices.erfangholami.com/0.7/build/access-control/#how-it-flows). The engine also stamps `dcterms:creator <ownerWebId>` onto the resource itself if absent (`GivenSharesEngine.kt:399`), best-effort and skipped for non-RDF resources: it is the provenance a link receiver resolves the owner from when no notification ever arrives.
1. **The index row is written** as a diffed N3 PATCH: `setShareModesForReceiver` (`SharingManagerHelper.kt:290`) computes exactly the triples to insert and delete against the record node — modes changed, `dcterms:created` filled only when absent, type/title replaced only when a non-null value differs, legacy flat rows for the pair deleted — and applies it under `If-Match`, retrying a 412 up to three times with jittered backoff (`SharingManagerHelper.kt:561`).
1. If the index write fails, the response is asymmetric on purpose (`GivenSharesEngine.kt:236`): a brand-new share rolls the grant back, because a grant nobody can see or revoke is worse than no grant; a mode change keeps the live grant, because revoking somebody's existing access over bookkeeping would be destructive — the index reconciles on the next successful write or `rebuildGivenIndex`. `SharingEngineTest` pins both halves.
1. If asked and the receiver is a WebID, a best-effort `as:Offer` carrying the mode and the typed-object description is posted (`GivenSharesEngine.kt:261`); delivery failure never fails the share — the grant is the relationship, the notification is the courtesy. `updateShare` (`GivenSharesEngine.kt:279`) is `createShare` with the notification swapped for an `as:Update`, so the receiver sees "your access changed" instead of a fresh share.

#### Refreshing given shares

`refreshGivenShares` (`GivenSharesEngine.kt:34`) re-reads the live ACL of every distinct resource in the stored index, then reconciles the index and returns **the index's resulting state — never the ACL-derived rows**. The reason is what the ACL cannot know: a live grant carries a receiver and modes but no `dcterms:created` and no typed-entity marks, and an earlier version that returned ACL rows made every refresh drop the type and title — in the app, typed rows visibly reverted from ticket cards to plain file icons. So verified pairs are written back through `setShareModesForReceiver` carrying the stored `createdAt` / `resourceType` / `resourceName` forward, and the returned list matches `getStoredGivenShares` row for row (pinned at `api/src/test/java/com/erfangholami/androidsolidservices/api/sharing/TypedShareEngineTest.kt:128`).

Pruning is deliberately conservative. A stored pair is removed only when its resource was positively observed (ACL read succeeded and the pair is gone from it) or the resource is **definitively gone** — `resourceIsGone` (`SharingManagerHelper.kt:263`) is true only when the server answered 404; a 401, 403, 5xx or transport failure reads as "still there", so a flaky network never deletes bookkeeping (`TypedShareEngineTest.kt:216`). `rebuildGivenIndex` (`GivenSharesEngine.kt:163`) applies the same rule at pod scale: `PodShareScanner` (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/PodShareScanner.kt:37`) walks the tree eight nodes at a time, skips the profile's excluded paths (its own bookkeeping, the LDN inbox, the public profile document — protocol-mandated access is not a share the user could revoke, `ShareStorageLayout.kt:43`), and reports whether the walk was complete; rows for unobserved regions are preserved, rows for excluded paths are pruned, and stored timestamps and typing survive the rebuild.

#### Receiving

A received share becomes a row by one of two paths, both serialized per WebID by a mutex (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/ReceivedSharesEngine.kt:26`).

Via notification: the caller lists the inbox once — reading, typing and the anti-impersonation gate are the notification layer's job ([Notifications](https://androidsolidservices.erfangholami.com/0.7/build/notifications/#reading-the-inbox)) — and hands the same batch to `syncReceivedShares` (`ReceivedSharesEngine.kt:153`). The batch is collapsed to the newest presence-changing event per canonical `(owner, resource)` pair — `OFFER`, `ACCEPTED`, `UPDATED` and `UNDO` count; on a timestamp tie a grant beats a revoke (`SharingManagerImplementation.kt:436`, `:447`). Each survivor is applied (`ReceivedSharesEngine.kt:167`): a grant probes the resource, skips on a definitive denial, and otherwise upserts the row — owner from the probe else the notification, mode from the notification else the probe else Read, `addedAt` from the offer's `as:published` so the stored time is the owner's share time, and the notification's `resourceType` / `resourceName` carried onto the row. An `UNDO` removes the pair. `REJECT` and the owner's own decision memos change no rows. Every item is individually best-effort: one failure is logged and skipped, never failing the batch.

Via link or QR: `addReceivedShare` (`ReceivedSharesEngine.kt:86`) probes the resource from the receiver's side (`WAC-Allow`, `SharingManagerHelper.kt:585`). Granted → the row is written with the strongest probed mode, `addedAt = now`, and the owner resolved in trust order: the link's `ownerHint` first (the sender put it there), then the probe's `solid:owner`, then the `dcterms:creator` stamp from share time, then the storage description's `solid:owner`, and as a last resort the pod origin, logged as a display-only identifier rather than a real WebID (`ReceivedSharesEngine.kt:221`). The same call is the re-verifier: scanning a QR after the sender revoked finds Denied and removes the stale row.

Failure behaviour

- **A deleted resource never strands its bookkeeping.** `revokeShare` (`GivenSharesEngine.kt:317`) tolerates the revoke failing when `resourceIsGone` confirms a 404 — there is no authorization left to narrow, so only the stale index row is removed (`TypedShareEngineTest.kt:188`). `getGivenSharesForResource` on a gone resource returns the stored rows instead of erroring, precisely so a caller can still clear them (`GivenSharesEngine.kt:108`). And `purgeGivenShares` is the fast path for the common case — the owner just deleted the resource: it drops the rows for the resource and, by default, every descendant (deleting a container deleted its members' shares too), touches no access control because the authorizations died with the resource, and sends each WebID receiver a best-effort `as:Undo` so their list drops the entry without waiting for its next re-validation (`GivenSharesEngine.kt:125`).
- **Transient failure is never treated as absence.** `resourceIsGone` demands a 404; refresh skips pruning unobserved resources; the pod scanner marks partial walks and preserves the unobserved rows. The bias is uniform: a wrongly kept row costs a stale list entry, a wrongly pruned row silently forgets who has access.
- **An unresolvable receiver does not block the share.** When canonicalization can read no profile and find no `foaf:primaryTopic`, the engine shares with the given IRI as-is and logs that the WAC rule may not match the receiver's real WebID (`GivenSharesEngine.kt:365`) — the grant and index row exist and are revocable, and the fix is to re-share once the profile resolves. On the receiving side the equivalent is typed, not silent: `addReceivedShare` throws `SharingException.AccessDenied` on a definitive denial with no stored row, and `AccessIndeterminate` when the probe cannot answer and nothing is stored (`api/src/main/java/com/erfangholami/androidsolidservices/api/exceptions/SharingException.kt:34`, `:106`) — the caller can distinguish "revoked" from "try again on network".
- **Index writes are compare-and-set, and divergence has a direction.** Every index mutation is an exact-diff N3 PATCH under `If-Match`, retried three times on 412 (`SharingManagerHelper.kt:561`); the received index adds the per-WebID mutex. When the given index still cannot be written, the ACL is the thing that must never end up wrong: new share → grant rolled back, mode change → live access kept (`SharingEngineTest.kt:40`, `:53`).
- **One logical mode per pair — the strongest.** A grant writes several `acl:mode`s ("Add" is Read+Append — the expansion is documented in [Access control](https://androidsolidservices.erfangholami.com/0.7/build/access-control/#implied-modes)), and hand-written ACLs can say anything. Reading folds all of it back: `collapseByReceiver` / `collapseByOwner` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/ShareCollapse.kt:12`, `:31`) reduce every `(receiver, resource)` — or `(owner, resource)` — group to one row at `ShareMode.strongest` (Write > Append > Read), keeping the earliest timestamp and the first known typing. A receiver therefore never appears twice on one resource, and the received index stores a single strongest mode per pair.

Extension points

- **The engine is type-open, and stays that way by construction.** `resourceType` and `resourceName` are threaded as opaque strings from the call site to the index record to the notification's object description to the receiver's row; nothing in `api/sharing/` imports a data module — the only `datamodule` dependency is the shared `TypeIndexResolver` utility used to register the bookkeeping container. A new module's entities become shareable without the sharing engine changing at all.
- **`ShareableEntityStore` is the contract a module implements** (`api/src/main/java/com/erfangholami/androidsolidservices/api/datamodule/ShareableEntityStore.kt:24`): `entityTypeIri` (the class written on typed records), `shareTarget(entityUri)` (the entity's own container, so one grant covers document plus attachments via inheritance), `publicShareTarget(entity)` (the single artifact an anyone-with-the-link share exposes, or `null` when none exists or the issuer prohibits it), `displayName(entity)`, and `findInContainer` (resolving the entity inside a granted foreign container by followed links). The sharing engine never calls this interface — the consumer wires a store's answers into `createShare` — which is exactly what keeps the dependency arrow pointing one way. Tickets and contacts implement it (`api/.../datamodule/tickets/TicketStore.kt`, `api/.../datamodule/contacts/ContactStore.kt`); implementing it is step five of [adding a data module](https://androidsolidservices.erfangholami.com/0.7/project/adding-a-data-module/#adding-a-module).
- **`SharingProfile` rebrands the whole pipeline** (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/SharingProfile.kt:17`): the RDF vocabulary (`ShareVocabulary`, `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/vocab/ShareVocabulary.kt:10`), the pod layout including the scan exclusions (`ShareStorageLayout`), the link codec (`ShareLinkCodec`, `api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/ShareLinkCodec.kt:13`), and whether the catalog exists at all. The default `SolidShareProfile` reproduces SolidShare's on-pod format byte for byte, so existing pods keep working unchanged — the same seam pattern as the notification layer's `ShareNotificationProfile`.

Tests

`api/src/test/java/com/erfangholami/androidsolidservices/api/sharing/` holds the suite, on two purpose-built fakes. `InMemorySharingPod` (`InMemorySharingPod.kt:18`) serves WebID and ACL reads but **fails every given-index patch on purpose**, which is how `SharingEngineTest.kt` pins the rollback asymmetry: a failed index write on a new share leaves the receiver with no access, and the same failure on a mode change leaves the live grant standing (`SharingEngineTest.kt:40`, `:53`). `PatchApplyingSharingPod` (`TypedShareEngineTest.kt:274`) is the round-trip complement — it actually applies N3 patches to both indexes, can delete everything under a prefix the way removing a container does, and can fail HEADs transiently.

`TypedShareEngineTest.kt` pins the typed-share and lifecycle decisions: create records type and title; an untyped re-grant never strips stored typing; `updateShare` refreshes the title in place; revoke leaves no orphaned `#share-` triples in the index; **refresh returns the typed index rows, not the bare ACL grants**, with `createdAt` surviving; purging a deleted container drops its rows and every row beneath it while sparing siblings; revoking against a gone resource still clears the row; refresh prunes gone resources but keeps transiently failing ones; and both receive paths carry `resourceType` / `resourceName` into the received index. `SolidShareLinkCodecTest.kt` pins the link round-trip, the null type on untyped links, and that the legacy `solidshare://` scheme still parses. `SolidShareProfileTest.kt` pins that the default profile reproduces the existing pod paths and namespace byte for byte, and that a custom layout actually retargets the engine. The backend write shapes are pinned one page over ([Access control](https://androidsolidservices.erfangholami.com/0.7/build/access-control/#tests)), and the typed notification wire format in [Notifications](https://androidsolidservices.erfangholami.com/0.7/build/notifications/#tests).

Specifications

- [Web Access Control](https://solidproject.org/TR/wac) — the substance of every grant, and the `acl:mode` IRIs reused inside index records so a record and the ACL it mirrors speak the same mode language. Implementation details and deviations live in [Access control](https://androidsolidservices.erfangholami.com/0.7/build/access-control/#specifications).
- [Access Control Policy](https://solidproject.org/TR/acp) — the second authorization dialect; the sharing engine never knows which one answered, and that ignorance is the design.
- [Activity Streams 2.0](https://www.w3.org/TR/activitystreams-core/) — the Offer/Update/Undo/Accept/Reject activities the engine sends and consumes; envelope, deviations and the impersonation gate are documented in [Notifications](https://androidsolidservices.erfangholami.com/0.7/build/notifications/index.md).
- [Linked Data Notifications](https://www.w3.org/TR/ldn/) — the inbox those activities travel through; the engine only ever sees the typed `ShareNotification` result.
- [Dublin Core terms](https://www.dublincore.org/specifications/dublin-core/dcmi-terms/) — `dcterms:created` for share time, `dcterms:title` for the entity's name at share time, `dcterms:creator` as the owner-provenance stamp, `dcterms:description` in the catalog. The rule behind the vocabulary split: a published term is used wherever one exists, and `solidshare:` mints only what nothing published can say — the reified share record itself and its three links.
