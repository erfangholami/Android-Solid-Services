# Notifications

Every pod can have an inbox. This is how your app reads what has arrived — offers of access, withdrawals, rejections and requests — and how it delivers those to someone else's pod.

Pull-only

There is no push subscription. Poll from a background worker (Android's floor is 15 minutes) and on user-initiated refresh. Anything promising a live socket is not this layer.

## What you can build

- A "shared with me" notification badge that works across apps and devices.
- Request-to-share: ask someone for access and let them answer from their own app.
- An activity feed of who granted or withdrew what, and when.
- Onboarding that provisions an inbox so the user can be reached at all.

## Setup

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:client:0.7.0")
}
```

```kotlin
import com.erfangholami.androidsolidservices.client.sdk.Solid

val notifications = Solid.getNotificationsClient(context)
notifications.connectionState().first { connected -> connected }
```

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:api:0.7.0")
}
```

```kotlin
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager

val notifications = NotificationsManager.getInstance(authenticator)
```

## Recipes

### Make sure the user has an inbox

Without one, nobody can notify them of anything. It is idempotent, so calling it during onboarding costs nothing on a pod that already has one:

```kotlin
val inbox = notifications.ensureInbox(webId)
```

This creates the container **and** advertises it from the WebID profile, which is how other pods discover where to deliver.

### Read the inbox

```kotlin
val messages = notifications.listNotifications(webId)

messages.forEach { m ->
    println("${m.type} — ${m.resourceName ?: m.resourceUri} from ${m.ownerWebId}")
}
```

Each message carries the resource it is about, the mode offered, who sent it, and — since 0.7.0 — `resourceType` and `resourceName`, so you can render "Alice shared a **contact**" rather than a bare URI.

### Read incoming access requests

```kotlin
val requests = notifications.listRequests(webId)
```

Answer them through the sharing client, which creates the share and notifies the requester in one step:

```kotlin
sharing.acceptShareRequest(webId, requests.first())
sharing.rejectShareRequest(webId, requests.first(), reason = "not this one")
```

### Ask someone for access

```kotlin
notifications.sendRequest(
    requesterWebId = webId,
    ownerWebId = "https://alice.example/profile/card#me",
    resourceUri = uri,
    requestedMode = ShareMode.READ,
    summary = "For the trip planning",
)
```

### Poll on a schedule

```kotlin
class InboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val messages = Solid.getNotificationsClient(applicationContext)
            .listNotifications(inputData.getString("webId")!!)
        if (messages.isNotEmpty()) postSystemNotification(messages)
        return Result.success()
    }
}

PeriodicWorkRequestBuilder<InboxWorker>(15, TimeUnit.MINUTES)
```

Fifteen minutes is Android's effective minimum for periodic work. Pair it with a manual refresh.

### Keep the inbox from growing forever

```kotlin
notifications.compactInbox(webId)                                  // drop offer/undo pairs
notifications.compactInbox(webId, olderThanIso = "2026-01-01T00:00:00Z")
notifications.deleteNotification(webId, message.notificationUri)   // one message
```

An offer that was later withdrawn is two messages saying nothing; compaction removes both.

## How it flows

```
sequenceDiagram
    autonumber
    participant Alice as Alice's app
    participant APod as Alice's pod
    participant BPod as Bob's pod
    participant Bob as Bob's app

    Alice->>APod: createShare(uri, READ, bob)
    APod-->>Alice: ACL written
    Alice->>BPod: GET bob's profile — ldp:inbox?
    BPod-->>Alice: inbox URI
    Alice->>BPod: POST Offer (Activity Streams 2.0, DPoP-signed)
    BPod-->>Alice: 201 Created

    Note over Bob: later — polling, not push
    Bob->>BPod: GET inbox
    BPod-->>Bob: the Offer
    Note over Bob: sender is verified against<br/>the resource's actual owner
    Bob->>Bob: render "Alice shared a file"
```

## Errors you'll hit

| What you see                                             | Why                                           | What to do                                                       |
| -------------------------------------------------------- | --------------------------------------------- | ---------------------------------------------------------------- |
| `NoInboxException`                                       | the receiver's profile advertises no inbox    | nothing to fix — they cannot be notified; the share still worked |
| `InboxUnauthorizedException` / `InboxForbiddenException` | their inbox rejected the post                 | treat delivery as best-effort                                    |
| `NotificationDeliveryException`                          | delivery failed in transit                    | retry the notification only; the access change already landed    |
| `ImpersonationDetectedException`                         | a message claimed a sender it could not prove | drop it — this is the anti-spoofing gate                         |
| Nothing ever arrives                                     | expecting push                                | this layer is pull-only; add a periodic worker                   |
| The same offer keeps reappearing                         | it was never compacted or deleted             | `compactInbox`, or delete on read                                |

## Under the hood

The notifications layer is how one account's sharing decisions reach another account's attention: an offer of access, a changed access level, a revocation, a request for access and its answer. It is two layers, deliberately. `NotificationTransport` is pure Linked Data Notifications — discover an inbox, POST an opaque activity into it, list and read and delete what is there, subscribe to a live channel — with no opinion about what any notification means. `NotificationsManager` is SolidShare's sharing semantics on top: typed Activity Streams 2.0 activities, an anti-impersonation gate on everything read, and inbox provisioning. The sharing layer is pull-only by decision: share notifications are infrequent enough that a periodic pull costs less battery than a persistent push connection, so callers refresh on demand or on a WorkManager schedule, and the transport's WebSocket channel exists for foreground live updates, not as a replacement.

Pod shape

One LDN inbox per account, a plain LDP container whose members are the notifications:

```text
{podRoot}
└── inbox/                            ← public acl:Append only; owner-read
    ├── solidshare-offer-{uuid}       ← as:Offer
    ├── solidshare-update-{uuid}      ← as:Update
    ├── solidshare-undo-{uuid}        ← as:Undo
    ├── solidshare-request-{uuid}     ← interop:AccessRequest
    ├── solidshare-accept-{uuid}      ← as:Accept (sent by an owner to a requester)
    └── solidshare-decision-accept-{uuid}  ← self-addressed as:Accept memo
```

The names are server-allocated from a `Slug` hint (`api/src/main/java/com/erfangholami/androidsolidservices/api/notifications/implementation/InboxNotifier.kt:139`); the prefixes come from the active profile's `NotificationSlugs`. The inbox is advertised with an `ldp:inbox` triple on the WebID — or, when the WebID document is not writable (Inrupt manages its own), on the first writable extended profile document (`NotificationsManagerImplementation.kt:332`).

An `as:Offer` is one activity document. For a typed entity share, `InboxNotifier` appends a description of the `as:object` — its RDF class and human title — so the receiver can render "shared a ticket" without dereferencing a resource it may not be able to read yet. The graph, with the values `TypedShareNotificationTest` pins:

```turtle
@prefix as:     <https://www.w3.org/ns/activitystreams#> .
@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .
@prefix acl:    <http://www.w3.org/ns/auth/acl#> .
@prefix schema: <https://schema.org/> .

<#offer>
    rdf:type      as:Offer ;
    as:actor      <https://alice.pod/profile/card#me> ;
    as:object     <https://alice.pod/tickets/u1/> ;
    as:target     <https://bob.pod/profile/card#me> ;
    acl:mode      acl:Read ;
    as:published  "2026-08-02T09:14:07Z"^^xsd:dateTime .

<https://alice.pod/tickets/u1/>
    rdf:type      schema:Ticket ;
    schema:name   "Coldplay — Music of the Spheres" .
```

(The wire bytes are more verbose than this rendering: the builder declares only the `as:`, `rdf:` and `xsd:` prefixes and then writes every term except `rdf:type` and the datatype as an absolute IRI in angle brackets — see `InboxNotifier.buildOfferTurtle` at `InboxNotifier.kt:164` and `objectDescriptionTurtle` at `InboxNotifier.kt:294`. The graph is identical.)

An `as:Update` is the same envelope with subject `<#update>` and type `as:Update` (`InboxNotifier.kt:183`), carrying the *new* mode; it exists as a distinct verb so the receiver sees "your access changed" rather than a fresh share. It takes the same optional object description. An `as:Undo` retracts access:

```turtle
<#undo>
    rdf:type      as:Undo ;
    as:actor      <https://alice.pod/profile/card#me> ;
    as:object     <https://alice.pod/tickets/u1/> ;
    as:target     <https://bob.pod/profile/card#me> ;
    as:published  "2026-08-02T09:20:00Z"^^xsd:dateTime .
```

This deviates from strict AS2 on purpose: `as:object` names the resource whose access was withdrawn, not the URI of the prior Offer activity, because the receiver keys its received-shares index by `(owner, resource)` and the prior Offer may already have been deleted from its inbox. `sendUndo` never writes `acl:mode`. The request/accept/reject family uses the same envelope — `interop:AccessRequest` (SAI) as the type for requests, `as:inReplyTo` linking an Accept back to the request it answers, `as:summary` carrying a rejection rationale. The `decision-*` items are the owner's own Accept/Reject mirrored into their *own* inbox as a read-only memo of the decision; the reader tells them apart from a genuine incoming accept/reject purely by the actor being the inbox owner (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/ShareNotificationType.kt:26`).

The `acl:mode` triple on the activity is a WAC term inside an AS2 activity — an extension, not vocabulary invention. New notifications always carry it; a legacy `solidshare:mode` string literal is still accepted on read (`ShareNotificationRDF.mode`).

Public surface

#### `NotificationsManager` — the sharing semantics

`api/src/main/java/com/erfangholami/androidsolidservices/api/notifications/NotificationsManager.kt:30`, obtained via `getInstance(authenticator | resourceManager, profile)`. Every verb returns `SolidResult`.

| Verb                                                        | What it does                                                                           |
| ----------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `listNotifications(webId)`                                  | Every recognised activity in the inbox as typed `ShareNotification`s, ownership-gated. |
| `listRequests(webId)`                                       | Every `interop:AccessRequest` for a resource the reader owns, as `ShareRequest`s.      |
| `deleteNotification(webId, uri)`                            | Removes one inbox item — dismissal; the ACLs and share indexes stay the truth.         |
| `compactInbox(webId, olderThanIso?)`                        | Deletes cancelled Offer/Undo pairs and items older than the cutoff; returns the count. |
| `ensureInbox(webId)`                                        | Discovers or creates an append-enabled inbox and returns its URI; idempotent.          |
| `sendOffer(owner, receiver, resource, mode, type?, name?)`  | Posts an `as:Offer`; the optional pair announces a typed entity share.                 |
| `sendUpdate(…)`                                             | Posts an `as:Update` with the new mode; same typed-object option.                      |
| `sendUndo(owner, receiver, resource)`                       | Posts an `as:Undo`.                                                                    |
| `sendRequest(requester, owner, resource, mode, summary?)`   | Posts an `interop:AccessRequest` to the owner.                                         |
| `sendAccept(owner, requester, resource, mode, requestUri?)` | Posts an `as:Accept`, linked back via `as:inReplyTo`.                                  |
| `sendReject(owner, requester, resource, reason?)`           | Posts an `as:Reject` with the rationale in `as:summary`.                               |
| `recordDecisionGranted / recordDecisionRejected`            | The same Accept/Reject posted into the owner's **own** inbox as a memo.                |

The sends are used internally by `SharingManager.createShare`, `updateShare` and `revokeShare`, which treat them as best-effort: the WAC/ACP grant is the access relationship, the notification is the courtesy, and a share succeeds even when its notification cannot be delivered.

`ShareNotification` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/ShareNotification.kt:17`) carries the notification URI, the `ShareNotificationType`, actor, resource, mode, summary, published timestamp, the target WebID (meaningful for decision memos), and — for typed entity shares — `resourceType` and `resourceName` read off the object description.

#### `NotificationTransport` — the protocol layer

`api/src/main/java/com/erfangholami/androidsolidservices/api/notifications/NotificationTransport.kt:30` is public precisely so other domains — a chat message, a comment, a follow request — can ride the same machinery without inheriting sharing semantics.

| Verb                                           | What it does                                                                                   |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `discoverInbox(webId)`                         | The advertised inbox URI, or null.                                                             |
| `post(webId, inbox, contentType, body, slug?)` | POSTs an opaque body; returns the created `Location`.                                          |
| `list(webId, inbox)`                           | Every item decoded to a generic `RawNotification`; unparseable items are skipped, never fatal. |
| `read(webId, notificationUri)`                 | One item, decoded.                                                                             |
| `delete(webId, notificationUri)`               | `true` when the server confirmed.                                                              |
| `subscribe(webId, resourceUri)`                | A cold `Flow<RawNotification>` over a `WebSocketChannel2023` channel.                          |

`RawNotification` (`RawNotification.kt:23`) is the AS2 envelope — subject, types, actor, object, target, summary, published, inReplyTo — plus every raw quad, with `objectsOf(predicate)` (`RawNotification.kt:51`) as the escape hatch for predicates the envelope does not model, which is exactly how a domain layer reads something like `acl:mode` without the transport knowing WAC exists.

`subscribe` lives only while collected — cancelling the collection closes the socket. On Android that means collecting under a lifecycle scope while a screen is visible; a persistent socket does not survive Doze, which is why it complements rather than replaces inbox polling.

How it flows

#### Sending an Offer

1. `sendOffer` encodes the resource IRI and hands off to `InboxNotifier.postOffer` (`InboxNotifier.kt:25`).
1. `InboxDiscovery.resolveInboxOf(receiver, sender)` (`InboxDiscovery.kt:30`) finds the receiver's inbox by following advertisements, never guessing a path: an anonymous read of the receiver's profile for `ldp:inbox`, then an anonymous `HEAD` for a `Link: rel="…ldp#inbox"` header, then each extended profile document (`rdfs:seeAlso` / `foaf:isPrimaryTopicOf`) read anonymously and, failing that, with the sender's session.
1. The body is built — envelope plus, for a typed share, the object description — and POSTed as Turtle (`text/turtle`) with `Slug: solidshare-offer-{uuid}` (`InboxNotifier.kt:131`, `NotificationTransportImplementation.kt:77`).
1. The outcome collapses into `InboxPostResult` (`InboxNotifier.kt:323`) — success with the `Location`, or `NoInbox` / `Unauthorized` / `Forbidden` / `HttpError` / `NetworkError` — which the manager maps onto `SharingException` variants.

#### Reading the inbox

`InboxReader.listNotifications` (`InboxReader.kt:33`) resolves the reader's own inbox (`InboxDiscovery.resolveOwnInbox`, authenticated this time), lists the container, and parses each member as a `ShareNotificationRDF` (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/rdf/sharing/ShareNotificationRDF.kt:36`). Only `as:Offer`, `as:Update`, `as:Accept`, `as:Undo` and `as:Reject` are recognised; anything else — including other apps' LDN traffic — parses to a null type and is skipped, and each item is wrapped in its own `runCatching` so one malformed notification never fails the listing. An Accept or Reject whose actor is the inbox owner becomes `DECISION_GRANTED` / `DECISION_REJECTED` (`InboxReader.kt:133`). The mode is the strongest of the `acl:mode` IRIs, falling back to the profile's legacy literal. Typed entity shares surface through `ShareNotificationRDF.objectType()` and `objectName()` (`ShareNotificationRDF.kt:96`, `ShareNotificationRDF.kt:106`), which read `rdf:type` and `schema:name` off the `as:object` node rather than the activity node.

Listing is a pure read: it does not delete inbox items (the inbox is the durable history — `deleteNotification` is the explicit dismissal) and it does not itself rewrite the received-shares index. The mirror is the caller's second step: `SharingManager.syncReceivedShares(webId, notifications)` (`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/SharingManager.kt:310`) takes the already-gated batch, collapses it to the newest presence-changing event per `(owner, resource)` — grant beats revoke on a timestamp tie (`SharingManagerImplementation.kt:436`) — then applies OFFER/ACCEPTED/UPDATED as add-or-update and UNDO as removal (`ReceivedSharesEngine.kt:167`), so one inbox read feeds both the notifications feed and the "shared with me" view.

#### The anti-impersonation gate

An LDN inbox is world-appendable by design, so "the actor says they shared this" proves nothing: anyone can POST an Offer naming any actor and any resource. Before a notification is surfaced, `InboxReader.actorMatchesOwner` (`InboxReader.kt:209`) demands evidence that the claimed actor actually controls the resource:

1. **`solid:owner` first.** A `HEAD` of the resource as the reader; if the server sends a `Link: rel="solid:owner"` header (`SolidMetadata.ownerUri`), that WebID must equal the claimed actor — decisive in both directions, kept or dropped.
1. **Declared storage otherwise.** The claimed actor's WebID profile is read and the resource must sit under one of its `pim:storage` roots (`InboxReader.kt:275`): the canonical resource IRI starts with the storage root, the resource host equals the storage host, and the storage host is **same-site** with the WebID host — same registrable suffix, not same host (`InboxReader.kt:339`). Same-site is what admits the legitimate multi-domain split (`id.provider.example` identity, `storage.provider.example` storage) while the storage-prefix check still stops a tenant on a shared host from forging offers about a neighbour's data.

The foreign actor's profile is fetched with `readPublic` first and the authenticated session only as a fallback (`InboxReader.kt:308`), and that order is load-bearing: some identity hosts — Inrupt's `id.inrupt.com` among them — refuse an authenticated read of a foreign WebID profile with a 401 while serving the same document anonymously, so authenticating first silently dropped every notification from those actors.

Two kinds skip the gate (`InboxReader.kt:150`): the reader's own decision memos, and an incoming `as:Reject` — a rejection confers no access, so forging one gains nothing, and the reader frequently cannot probe a resource it has just been denied.

`listRequests` gates differently: an `interop:AccessRequest` is only surfaced when the requested resource belongs to the *reader* — under the reader's own declared storage, or on the reader's WebID host (`InboxReader.kt:242`). A request about somebody else's resource is noise.

Failure behaviour

- **No inbox discoverable on send** — `InboxPostResult.NoInbox` becomes `SharingException.NoInbox` ("No ldp:inbox advertised…", `api/src/main/java/com/erfangholami/androidsolidservices/api/exceptions/SharingException.kt:51`). `SharingManager` ignores it: the grant already stands, and the receiver can still discover the share by probing access. The notifier never provisions somebody else's inbox — `ensureInbox` is strictly for one's own account.
- **Append-only is the working state, not a failure.** Senders POST blind and never read the target inbox. On the reader's side, a 401/403 reading one's *own* inbox is loud — `SharingException.InboxUnauthorized` / `InboxForbidden` (`InboxReader.kt:84`) — because that is a misconfiguration, not a policy; any other read error lists as empty. `ensureInbox` re-asserts public `acl:Append` on every call (`NotificationsManagerImplementation.kt:203`), so an inbox created before the grant existed, or by another client, is repaired. The grant is write-only on both backends — `includeImpliedModes = false` (`InboxProvisioner.kt:43`) skips the sharing UI's implied `acl:Read`, since WAC's `acl:Append` suffices to create a member and LDN gates writing, not reading.
- **An actor whose profile will not resolve** fails closed. When neither the anonymous nor the authenticated profile read succeeds and no `solid:owner` header vouches for the resource, the storage check cannot run and the notification is dropped and logged (`InboxReader.kt:308`). Unverifiable is treated as forged.
- **Multi-domain pods once broke the gate.** The earlier check required the resource host to equal the WebID host, which dropped every legitimate notification from ESS-style deployments that split identity and storage across sibling subdomains. The fix is the current two-step gate — `solid:owner` first, then declared-`pim:storage` matching with the same-site rather than same-host comparison — and `InboxReaderGateTest` pins the ESS-style split as kept.
- **`compactInbox` deletes best-effort** (`NotificationsManagerImplementation.kt:75`): a cancelled Offer/Undo pair for the same `(actor, resource)` deletes both sides, an age cutoff removes stale items, per-item delete failures are logged and skipped, unrecognised foreign notifications are left alone, and an item without `as:published` is never deleted by age — only by pair-cancellation.
- **`subscribe` fails early and typed**: without an authenticated session, or when the pod's storage description advertises no `WebSocketChannel2023` service, it returns a failure rather than a dead Flow (`NotificationTransportImplementation.kt:155`). Channel negotiation retries a 401 once with a forced token refresh, honours DPoP nonces, and gives up after three attempts (`WebSocketChannel2023Client.kt:35`); undecodable frames are dropped, and socket failure closes the Flow with the cause.

Extension points

- **`ShareNotificationProfile`** (`api/src/main/java/com/erfangholami/androidsolidservices/api/notifications/ShareNotificationProfile.kt:19`) — the only two SolidShare-specific conventions in the layer, made pluggable: the legacy mode-literal vocabulary accepted on read, and the `Slug` prefixes written on send. The wire shape itself is standards-based (AS2 + WAC `acl:mode` + SAI `interop:AccessRequest`), so passing a different profile to `getInstance` rebrands the flow without forking it.
- **`NotificationTransport` as a foundation** — post and read your own activity kinds in your own domain layer; `RawNotification.objectsOf` reaches any predicate the AS2 envelope does not model, which is the same seam the sharing layer uses for `acl:mode`.
- **Typed object descriptions** — `resourceType` / `resourceName` on `sendOffer` and `sendUpdate` are how data modules announce "this is a ticket named X"; a new module passes its own class IRI and display name and the reader surfaces both without changes.
- **Live channels** — `subscribe` is the seam for foreground reactivity. Only `WebSocketChannel2023` is implemented; the other channel types are named in `Shared/src/main/java/com/erfangholami/androidsolidservices/shared/vocab/Notify.kt:8` but deliberately not built: webhooks need a public endpoint a phone does not have, and for a foreground-only complement to polling one channel type suffices.
- **Background push is deliberately absent.** The sharing layer polls (the KDoc on `NotificationsManager` states the reasoning): sharing notifications are rare, Android's effective periodic-work floor is 15 minutes, and a persistent connection buys latency nobody asked for at a battery cost everybody pays.

Tests

All under `api/src/test/java/com/erfangholami/androidsolidservices/api/notifications/`, on a `FakeSolidResourceManager` fixture with injectable `onRead` / `onReadPublic` / `onHead` lambdas.

`InboxReaderGateTest.kt` pins the gate's decisions: a forged offer for another tenant's resource on a shared host is dropped; an offer under the actor's own declared storage is kept; a `solid:owner` header naming the actor keeps a notification that would otherwise fail the storage check, and one naming somebody else drops it; and the ESS-style identity/storage split across sibling subdomains is kept. `TypedShareNotificationTest.kt` pins both halves of typed shares — the notifier writes the object description with `rdf:type` and `schema:name`, an untyped offer writes none, and the reader surfaces `resourceType` / `resourceName` on the parsed notification. `InboxReaderRequestsTest.kt` pins the requests gate: own-resource requests surface, foreign-resource requests drop, untyped items are ignored.

`NotificationTransportTest.kt` pins the protocol layer: the created `Location` on success, HTTP error propagation, delete delegation, inbox discovery via a HEAD link and the null result when nothing is advertised, a listing that surfaces read errors without throwing, and both `subscribe` refusals. `WebSocketChannel2023ClientTest.kt` pins channel negotiation (`notify:receiveFrom` extraction) and that pushed frames decode into `RawNotification`s. `RawNotificationParserTest.kt` and `RawNotificationTest.kt` pin the envelope: AS2 field extraction, `as:inReplyTo`, empty documents, preferring an AS2-typed subject over a blank node, and `objectsOf` scoping to the activity subject. `SolidShareNotificationProfileTest.kt` pins that the default profile reproduces SolidShare's literals and slugs exactly and that a custom profile rebrands them.

Specifications

- [Solid Notifications Protocol](https://solidproject.org/TR/notifications-protocol) — the subscription model behind `subscribe`; only the resource-change channel is used, not push delivery of sharing notifications, which is the pull-only decision above.
- [Linked Data Notifications](https://www.w3.org/TR/ldn/) — the inbox itself: sender (POST to a discovered `ldp:inbox`) and consumer (list the container, read the members) are implemented against any conformant receiver. One lean on a SHOULD: notifications are posted as Turtle, which LDN receivers should accept alongside the mandatory JSON-LD, and every Solid server is an RDF store anyway.
- [Activity Streams 2.0 Core](https://www.w3.org/TR/activitystreams-core/) and [Vocabulary](https://www.w3.org/TR/activitystreams-vocabulary/) — the activity envelope and the Offer/Update/Accept/Undo/Reject types (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/vocab/AS.kt:9`). Two deliberate deviations: `as:Undo` targets the resource rather than the prior activity, and WAC `acl:mode` rides on the activity as an extension predicate.
- [WebSocketChannel2023](https://solid.github.io/notifications/websocket-channel-2023) — the one channel type implemented. `EventSourceChannel2023`, `WebhookChannel2023`, `StreamingHTTPChannel2023` and `LDNChannel2023` are named in the vocabulary and not implemented — a decision, for the reasons under extension points.
- [Web Access Control](https://solidproject.org/TR/wac) — the `acl:mode` IRIs inside notifications and the append-only inbox grant.
- [Solid Application Interoperability](https://solidproject.org/TR/sai) — the `interop:AccessRequest` class for the request-to-share flow; the registries themselves are the sharing feature's concern, not this layer's.
