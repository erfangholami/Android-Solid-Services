---
title: Access control
description: What the pod actually enforces — WAC and ACP, the modes they grant, and how to check what a user may do.
tags:
  - access control
  - wac
  - acp
---

# Access control

[Sharing](sharing.md) is the API you call. This is what the pod does underneath, and it is worth
knowing because the two access-control systems in the Solid ecosystem behave differently and your
users' servers will not all speak the same one.

The library picks the backend per resource and writes whichever the server understands, so you do
not branch on it. What you do need is the modes, and how to check them before offering an action.

## What you can build

- UI that shows the right controls: edit for those who may edit, read-only for those who may not.
- A drop-box — others can add, but cannot read what is already there.
- Confidence that "revoke" really revoked, by reading the ACL rather than your own index.
- Recovery when a resource's ACL loses its owner and becomes unmanageable.

## The modes

| Mode | Called | Lets the receiver |
|---|---|---|
| `READ` | View | read the resource, and list a container's members |
| `APPEND` | Add | create new members, without reading or changing existing ones |
| `WRITE` | Edit | read, change and delete |

Granting one writes the modes it implies — Add is Read + Append, Edit is Read + Write — because
partial sets behave inconsistently between servers. Reading them back collapses to a single
logical mode per receiver.

**Control** is the fourth WAC mode and the library never grants it. It lets the holder rewrite the
ACL itself, which means granting away the ability to revoke.

## Recipes

### Check what the user may do

```kotlin
when (val probe = resources.probeAccess(webId, uri)) {
    is AccessProbe.Accessible -> {
        val canEdit = ShareMode.WRITE in probe.modes
        render(canEdit = canEdit, owner = probe.ownerWebId)
    }
    AccessProbe.Denied -> hideResource()
}
```

This reads the server's `WAC-Allow` header — the pod's own answer, not a guess from your index.

!!! danger "A failure is not a denial"
    A 401 blip, a 5xx or a dropped connection **throws**. It does not come back as `Denied`.
    Treating an error as "no access" is how apps hide controls the user is entitled to.

### Read who actually has access

```kotlin
val live = sharing.getGivenSharesForResource(webId, uri)
```

Straight from the resource's ACL, bypassing the index. This is the authoritative answer, and the
one to show on a screen where the user is auditing access.

### Make something private again

```kotlin
sharing.makePrivate(webId, uri)
```

Strips every share in one call, leaving the resource owner-only.

### Repair a resource that lost its owner

```kotlin
sharing.repairOwnerControl(webId, uri)
```

If an ACL was written elsewhere without the owner's `acl:Control`, nobody can change access any
more — including the owner. This re-asserts it. The library refuses to write an ACL that would
cause this in the first place, but resources touched by other tools can arrive in that state.

### Know which backend a server uses

You rarely need to, but it explains behaviour differences:

| | WAC | ACP |
|---|---|---|
| Used by | Community Solid Server, most community pods | Inrupt ESS / PodSpaces |
| Rules live in | an `.acl` sidecar beside the resource | an Access Control Resource, often on a separate origin |
| Discovered via | `Link rel="acl"` | `Link rel="acl"`, pointing at the ACP endpoint |
| Inheritance | a container's ACL defaults down to members | policies matched per resource |
| Creating rules | PUT the `.acl` | the endpoint refuses PUT-to-create; policies are patched |

The library probes the resource and uses whichever it finds. A server that offers neither raises
`UnsupportedAuthBackendException`.

## How it flows

```mermaid
sequenceDiagram
    autonumber
    participant SDK as SDK
    participant Pod as Pod server

    SDK->>Pod: HEAD resource
    Pod-->>SDK: Link rel="acl"; WAC-Allow: user="read write"
    alt the link is a sidecar on the same origin
        Note over SDK: WAC
        SDK->>Pod: PUT .acl with the implied modes
    else the link points at an authorization origin
        Note over SDK: ACP
        SDK->>Pod: PATCH the ACR's policies
    end
    Pod-->>SDK: 205
```

## Errors you'll hit

| What you see | Why | What to do |
|---|---|---|
| `UnsupportedAuthBackendException` | the server offers neither WAC nor ACP | access control is unavailable there |
| `AccessDeniedException` | you lack `acl:Control` on the resource | only the owner can change access |
| `StaleAclException` | the ACL changed under a conditional write | re-read and reapply |
| `403` where your index says you have access | the ACL was changed outside your app | `getGivenSharesForResource` for the truth, then refresh |
| Nobody can change access, not even the owner | the ACL lost its owner `Control` | `repairOwnerControl` |
| A grant "worked" but the receiver cannot read | only a partial mode set was written by another tool | re-grant; this library always writes implied modes |

## Under the hood

Every share the library creates ends life as an edit to an access-control document on the owner's
pod. Two mechanisms exist in the wild for that document — Web Access Control, where a `.acl`
sidecar carries `acl:Authorization` rules, and Access Control Policy, where an Access Control
Resource (ACR) carries policies and matchers — and the library implements both behind one internal
interface, `AccessBackend`
(`api/src/main/java/com/erfangholami/androidsolidservices/api/access/AccessBackend.kt:7`). The
sharing engine speaks five verbs — grant, revoke, list, make owner-only, reclaim owner control —
and never knows which dialect it is writing. Which backend answers is decided per resource, from
how the server advertises the document, not per pod and not by configuration.

<details class="info" markdown id="pod-shape">
<summary>Pod shape</summary>


The subject resource names its access-control document in a `Link: rel="acl"` response header;
the library never guesses the name. What lives at that URI differs by server family. Both forms
below are exactly what the backends write; the documents go over the wire as
`application/n-triples` (`api/src/main/java/com/erfangholami/androidsolidservices/api/access/NTriples.kt:9`)
and are shown here in Turtle for legibility.

#### WAC — the `.acl` sidecar

A grant is one `acl:Authorization` per receiver, minted as `#share-{uuid}` on the ACL's own URI,
next to the `#owner` rule the backend re-asserts on every write
(`api/src/main/java/com/erfangholami/androidsolidservices/api/access/WacBackend.kt:55` and `:325`).
This is a container's ACL after sharing it with Bob at View level and opening it publicly for
Append (the LDN-inbox pattern):

```turtle
@prefix acl:  <http://www.w3.org/ns/auth/acl#> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

<https://alice.pod/shared/.acl#owner>
    a acl:Authorization ;
    acl:accessTo <https://alice.pod/shared/> ;
    acl:default  <https://alice.pod/shared/> ;
    acl:mode     acl:Read, acl:Write, acl:Control ;
    acl:agent    <https://alice.pod/profile/card#me> .

<https://alice.pod/shared/.acl#share-6f8a2c1e-9b47-4d02-8f3a-5e7d1b9c0a44>
    a acl:Authorization ;
    acl:accessTo <https://alice.pod/shared/> ;
    acl:default  <https://alice.pod/shared/> ;
    acl:mode     acl:Read ;
    acl:agent    <https://bob.pod/profile/card#me> .

<https://alice.pod/shared/.acl#share-b3d91f72-0c5e-4a68-9e21-7f4a6d8c3b10>
    a acl:Authorization ;
    acl:accessTo <https://alice.pod/shared/> ;
    acl:default  <https://alice.pod/shared/> ;
    acl:mode     acl:Append ;
    acl:agentClass foaf:Agent .
```

The public form is `acl:agentClass foaf:Agent` — `ShareReceiver.Public.toRdfSubject()` resolves to
the `foaf:Agent` IRI
(`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/vocab/Solid.kt:69`). The
container-inheritance form is `acl:default`: a rule on a container carries both `acl:accessTo` and
`acl:default` naming the container itself, so members inherit; a rule on a plain resource carries
`acl:accessTo` only (`WacBackend.kt:58`). Group grants use `acl:agentGroup` with the group's URI.

#### ACP — the ACR

A grant is a chain of three fragments minted with one shared UUID suffix — access control, policy,
matcher — appended by `AcpBackend.appendPolicy`
(`api/src/main/java/com/erfangholami/androidsolidservices/api/access/AcpBackend.kt:297`). The same
container shared with Bob at Edit level:

```turtle
@prefix acp: <http://www.w3.org/ns/solid/acp#> .
@prefix acl: <http://www.w3.org/ns/auth/acl#> .

<https://alice.pod/shared/.acr>
    a acp:AccessControlResource ;
    acp:resource            <https://alice.pod/shared/> ;
    acp:accessControl       <https://alice.pod/shared/.acr#ac-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55> ;
    acp:memberAccessControl <https://alice.pod/shared/.acr#ac-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55> .

<https://alice.pod/shared/.acr#ac-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55>
    a acp:AccessControl ;
    acp:apply <https://alice.pod/shared/.acr#policy-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55> .

<https://alice.pod/shared/.acr#policy-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55>
    a acp:Policy ;
    acp:allow acl:Read, acl:Write ;
    acp:allOf <https://alice.pod/shared/.acr#matcher-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55> .

<https://alice.pod/shared/.acr#matcher-2d7e0b9a-5c31-4f86-a04d-8b1c6e9f2a55>
    a acp:Matcher ;
    acp:agent <https://bob.pod/profile/card#me> .
```

The public form is `acp:agent acp:PublicAgent` on the matcher (`AcpBackend.kt:331`). The
container-inheritance form is the ACR linking the same access control through **both**
`acp:accessControl` and `acp:memberAccessControl` (`AcpBackend.kt:311`), so the policy applies to
the container and transitively to its members. `acp:allow` values are the WAC mode IRIs — ACP
reuses them, so `ShareMode` maps identically on both backends. An owner self-control chain uses
the same shape with suffix `owner-{uuid}` and `acp:allow acl:Read, acl:Write, acl:Control`.

</details>

<details class="info" markdown id="public-surface">
<summary>Public surface</summary>


Apps never see `AccessBackend`. The verbs surface on `SharingManager`
(`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/SharingManager.kt`), which
also maintains the bookkeeping indexes and notifications documented in [Sharing](sharing.md):

| Verb | Backend effect |
|---|---|
| `createShare(webId, resourceUri, mode, receiver, …)` (`:167`) | `grant` — one authorization/policy per receiver, prior grant to the same receiver replaced |
| `updateShare(…)` (`:192`) | `grant` with the new mode; the pair's old rule is dropped first, so widening and narrowing are the same write |
| `revokeShare(webId, resourceUri, receiver)` (`:211`) | `revoke` — the receiver's rule removed, everyone else untouched |
| `getGivenSharesForResource(webId, resourceUri)` (`:141`) | `listShares` — read straight from the live ACL/ACR, authoritative over the stored index |
| `makePrivate(webId, resourceUri)` (`:132`) | `ensureOwnerOnly` — WAC resets the document to a single owner rule; ACP guarantees an owner policy (see below) |
| `repairOwnerControl(webId, resourceUri)` (`:118`) | `reclaimOwnerControl` — re-asserts owner Read/Write/Control without touching anyone else |

Probing what *you* hold on someone else's resource never reads their ACL — you cannot. It reads
the `WAC-Allow` response header instead:

```kotlin
suspend fun probeAccess(webId: String, uri: String): SolidResult<AccessProbe>
```

(`api/src/main/java/com/erfangholami/androidsolidservices/api/resource/SolidResourceManager.kt:136`.)
The result is three-valued on purpose: `AccessProbe.Accessible(modes, ownerWebId)`,
`AccessProbe.Denied` for a definitive 403/404, and `SolidResult.Failure` for anything
indeterminate (a 401 refresh blip, 5xx, transport error) — so a flaky network is never mistaken
for a revocation
(`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/resource/AccessProbe.kt`).
Servers work the same way on both backends here: ACP servers emit `WAC-Allow` too.

</details>

<details class="info" markdown id="how-it-flows">
<summary>How it flows</summary>


#### Backend selection

`pickBackend`
(`api/src/main/java/com/erfangholami/androidsolidservices/api/access/AuthBackendDiscovery.kt:7`)
decides per resource from one HEAD: if the advertised `rel="acl"` URI has the same scheme, host
and port as the resource, it looks like a WAC sidecar and the WAC backend answers; anything else —
notably Inrupt PodSpaces, whose ACRs live on `authorization.inrupt.com`, a different host from the
storage — routes to ACP. A resource with no `acl` link at all also routes to ACP, which then fails
with `SharingException.UnsupportedAuthBackend` (`AcpBackend.kt:239`) rather than inventing a
document location. When the HEAD itself fails, `SharingManagerHelper.backendFor` falls back to WAC
(`api/src/main/java/com/erfangholami/androidsolidservices/api/sharing/implementation/SharingManagerHelper.kt:185`).
There is no server-capability negotiation and no cached per-pod answer; the heuristic is re-run on
every operation because it costs a HEAD the operation needed anyway.

#### Granting to a WebID

WAC (`WacBackend.kt:38`) is a read-modify-write of the whole sidecar: read the ACL and its strong
ETag; find every existing rule that touches this `(resource, receiver)` pair plus every owner
self-rule; drop those subjects; write back all remaining quads verbatim plus a fresh `#owner` rule
and a fresh `#share-{uuid}` rule; PUT with `If-Match`. Dropping-then-re-adding is what makes
re-granting idempotent — the tests pin that granting Bob View then Edit yields one rule, not two
(`WacBackendTest.kt:109`). When the resource has no own ACL yet (the ACL URI answered 404), the
base rules are seeded from the nearest ancestor container's `acl:default` rules — walked up to 32
levels (`WacBackend.kt:219`) and materialized as `#inherited-{n}` rules on the child — so sharing
a child of a shared folder starts from the folder's audience instead of silently disinheriting
everyone else. The same walk backs `listShares` on ACL-less children (`WacBackendTest.kt:73`).

ACP (`AcpBackend.kt:22`) is surgical where WAC is wholesale: it finds matchers targeting this
receiver, follows them backwards through `acp:allOf` to policies and `acp:apply` to access
controls, removes exactly that cascade (`AcpBackend.kt:47`), appends an owner self-control chain
if none survives, appends the new chain, and PUTs the result. Every quad it did not positively
match survives verbatim — ACRs routinely carry server-managed policies the library never wrote,
and rewriting them from a partial model would destroy them.

#### Granting publicly

The same paths with the receiver swapped: WAC writes `acl:agentClass foaf:Agent`
(`WacBackend.kt:67`), ACP writes `acp:agent acp:PublicAgent` (`AcpBackend.kt:331`). On the read
side both map back to `ShareReceiver.Public`, and both hide the owner's own WebID from the listing
so "shared with" never lists yourself (`WacBackend.kt:125`, `AcpBackend.kt:397`).

#### Revoking

WAC revoke (`WacBackend.kt:85`) must handle rules it did not write: a hand-authored rule can name
several agents or several resources at once. `narrowAuthorization` (`WacBackend.kt:342`) splits
such a rule instead of deleting it — a `-keepres` copy keeps the other resources, a `-keepsubj`
copy keeps the other agents — so revoking Bob never revokes Carol
(`WacBackendTest.kt:142`). ACP revoke removes the receiver's matcher cascade and, as always,
re-asserts owner control before the write (`AcpBackend.kt:85`).

#### Implied modes

WAC has no mode subsumption — `acl:Write` does not grant `acl:Read` — so the library's three UI
levels expand to explicit mode sets: View → `Read`; Add → `Read, Append`; Edit → `Read, Write`
(`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/ShareMode.kt:34`).
Without the explicit Read, an Edit receiver could overwrite a resource they cannot GET.
`AcpBackend.grant` expands the same set into `acp:allow` values — ESS resolves modes just as
independently, and an earlier version that wrote only the named mode produced approved cross-pod
shares the receiver could not read. Both `grant` signatures take `includeImpliedModes: Boolean`
(default true); the one caller of `false` is inbox provisioning, where public Append must *not*
leak Read (`WacBackendTest.kt:85`, `AcpBackendTest.kt:86`). Reading collapses the expansion back:
each `(receiver, resource)` pair folds to its strongest mode via `collapseByReceiver`
(`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/sharing/ShareCollapse.kt:12`),
so a receiver appears once, at Edit, not three times.

</details>

<details class="info" markdown id="failure-behaviour">
<summary>Failure behaviour</summary>


- **Owner lockout is prevented, not repaired after the fact.** Every WAC rebuild and every ACP
  grant/revoke re-asserts the owner's Read/Write/Control before writing (`WacBackend.kt:78`,
  `AcpBackend.kt:58`), so no sequence of shares and revocations can strip the owner. For documents
  damaged by *other* clients there is `repairOwnerControl`, which adds the owner rule without
  touching anyone else's. It works only while the owner still holds Control; if even the HEAD
  fails with 401/403 the helper gives up with an explicit message that the lockout must be cleared
  through the pod provider's tooling (`SharingManagerHelper.kt:243`) — the library cannot write an
  ACL it cannot reach.
- **ACP fails fast on an ACR it cannot read.** `readAcr` distinguishes "the ACR does not exist"
  (start from empty, write fresh) from "the ACR exists but did not parse" — the latter sets
  `parseFailed` and every verb then throws `UnsupportedAuthBackend("ACP (unreadable ACR)")` before
  any PUT (`AcpBackend.kt:219`). The alternative — treating unparseable as empty — would rewrite
  the ACR from nothing and silently drop every co-share; the tests pin that no PUT happens
  (`AcpBackendTest.kt:147`).
- **Inrupt ACRs are parsed offline.** PodSpaces serves ACRs as JSON-LD whose `@context` lives at
  `authorization.inrupt.com` and does not always dereference, which used to fail the generic
  JSON-LD reader and trip the fail-fast above. `InruptAcrJson`
  (`api/src/main/java/com/erfangholami/androidsolidservices/api/access/InruptAcrJson.kt:9`)
  recognizes that context marker and maps the document to ACP quads with a hand-rolled walker —
  no network, no context resolution — hooked in ahead of the generic reader
  (`api/src/main/java/com/erfangholami/androidsolidservices/api/resource/implementation/SolidResourceParser.kt:100`).
  A non-Inrupt context declines so the generic reader still runs (`InruptAcrJsonTest.kt:64`).
- **Weak ETags never reach `If-Match`.** `SolidMetadata.etag` carries only strong validators —
  `getETag()` reports `null` for a `W/"…"` tag
  (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/util/HeaderExtensions.kt:123`)
  — because a weak validator can never satisfy `If-Match` and sending one guarantees a 412 (Node
  Solid Server emits only weak ETags for RDF). With no strong tag the ACL write goes
  unconditional, and the WAC backend also seeds its base rules from ancestor defaults as if the
  ACL were fresh, while still preserving every quad it actually read. A *genuine* 412 — the
  document changed between read and write — raises `SharingException.StaleAcl`
  (`api/src/main/java/com/erfangholami/androidsolidservices/api/exceptions/SharingException.kt:85`);
  the WAC backend retries the whole read-modify-write up to three times (`WacBackend.kt:26`,
  pinned at `WacBackendTest.kt:153`), the ACP backend lets it propagate to the caller.
- **401/403 on the ACL itself means no Control.** Reading the subject resource's headers is not
  reading its ACL; a server answers 401/403 on the ACL URI precisely when the requesting agent
  lacks `acl:Control`. The WAC backend treats only 404 as "no ACL yet" and surfaces every other
  read failure as an error (`WacBackend.kt:277`) — an Edit receiver can modify a file but can
  never list or change who it is shared with.
- **Two deliberate asymmetries.** Group receivers (`acl:agentGroup`) exist only on WAC; ACP has no
  equivalent concept and `grant` rejects a `GroupReceiver` before writing anything
  (`AcpBackend.kt:228`, pinned at `AcpBackendTest.kt:176`). And `makePrivate` resets a WAC
  document to a single owner rule (`WacBackend.kt:153`) but on ACP only guarantees the owner
  policy exists (`AcpBackend.kt:171`): its caller is the duplicate flow, where the fresh copy's
  ACR has no grants to strip, and a wholesale ACR reset would delete server-managed policies the
  library did not author.

</details>

<details class="info" markdown id="extension-points">
<summary>Extension points</summary>


The seam is `AccessBackend` — five suspend functions over `(webId, resourceUri, isContainer)`
plus the shared `ShareMode` / `ShareReceiver` / `GivenShare` types:

```kotlin
internal interface AccessBackend {
    suspend fun grant(webId, resourceUri, mode, receiver, isContainer, includeImpliedModes = true)
    suspend fun revoke(webId, resourceUri, receiver, isContainer)
    suspend fun listShares(webId, resourceUri): List<GivenShare>
    suspend fun ensureOwnerOnly(webId, targetUri, isContainer)
    suspend fun reclaimOwnerControl(webId, targetUri, isContainer)
}
```

A new backend implements those five and earns a branch in `pickBackend`; nothing above the
`SharingManagerHelper` dispatch sites (`SharingManagerHelper.kt:193`, `:217`, `:232`, `:243`,
`:256`) changes. The contract the engine relies on, beyond the signatures: every write re-asserts
owner control; `grant` replaces rather than accumulates for a `(receiver, resource)` pair;
`listShares` collapses to one row per receiver and omits the owner; concurrent-modification
surfaces as `StaleAcl`; and an access document the backend cannot faithfully round-trip is an
error, never a rewrite. The interface is `internal` on purpose — the engine trusts those
invariants, and a third-party backend that broke them would corrupt access documents the library
gets blamed for. It becomes public when a real second consumer exists, not before.

</details>

<details class="info" markdown id="tests">
<summary>Tests</summary>


`api/src/test/java/com/erfangholami/androidsolidservices/api/access/` holds the suite, all running
against `InMemoryAccessPod` (`InMemoryAccessPod.kt:14`) — a `SolidResourceManager` that stores
documents as N-Triples bytes, mints ETags, advertises `{resource}.acl` links, and can be told to
412 the next PUT or make a document unreadable.

- `WacBackendTest.kt` pins the write shapes (Edit writes Read+Write; a container grant carries
  `acl:default`; a non-container grant does not; Append-only with implied modes off writes no
  Read), the collapse-and-hide-owner listing, inherited-default listing on ACL-less children,
  replace-not-accumulate on re-grant, revoke keeping the owner rule and the other receiver, and
  the 412-retry landing the grant.
- `AcpBackendTest.kt` pins the same behavioural surface in ACP terms (`memberAccessControl` on
  container grants, `PublicAgent` round-tripping as `Public`) plus the two guardrails: fail-fast
  on an unreadable ACR with zero PUTs, and group-receiver rejection with zero PUTs.
- `InruptAcrJsonTest.kt` pins the offline Inrupt mapping — including declining non-Inrupt contexts
  so the generic reader runs — and `NTriplesTest.kt` pins the wire format, down to a full
  authorization set round-tripping whole.
- One level up, `api/src/test/java/com/erfangholami/androidsolidservices/api/sharing/SharingEngineTest.kt:40`
  pins that a failed index write rolls back a fresh WAC grant, and that a failed index write on a
  mode change leaves the receiver's live access in place — the ACL, not the index, is the thing
  that must never end up wrong.

</details>

<details class="info" markdown id="specifications">
<summary>Specifications</summary>


- [Web Access Control](https://solidproject.org/TR/wac) — implemented for `acl:agent`,
  `acl:agentClass foaf:Agent`, `acl:agentGroup`, `acl:accessTo`, `acl:default`, and the four
  modes. `acl:origin` and other agent classes (`acl:AuthenticatedAgent`) are read and preserved
  but never written: the product's receivers are a person, a group, or everyone, and authoring
  rules the UI cannot represent would create shares the user cannot see or revoke.
- [Access Control Policy](https://solidproject.org/TR/acp) — the library authors only the
  `allOf` + `agent` matcher form with `acp:allow`; `anyOf`, `noneOf`, `acp:deny`, and
  client/issuer/VC matchers are preserved on round-trip but never written, for the same reason.
  ACP access grants (`acp:AccessGrant`) are not implemented.
- [Solid Protocol — `WAC-Allow`](https://solidproject.org/TR/protocol) — `probeAccess` and the
  received-share refresh read the header via `WacAllow.parse`
  (`Shared/src/main/java/com/erfangholami/androidsolidservices/shared/model/access/WacAllow.kt:55`).
  Two deliberate readings beyond the letter of the spec: user and public mode groups are folded
  together, because the probe answers "can I act" rather than "why"; and a reachable resource
  with no `WAC-Allow` at all is reported as View access, because the 200 already proved readability
  and some servers simply omit the header.
- One deviation from both authorization specs by design: the library's Add and Edit grants assert
  `acl:Read` explicitly rather than relying on any server-side implication, because neither spec
  implies it and at least one server (ESS) enforces that literally.


</details>