# Changelog

All notable changes to this project are documented here.

## [Unreleased]

Correctness and data-integrity hardening on top of the in-progress 0.6.0 contacts/tickets work.
**Source-compatible** — no public signatures change; behaviour becomes more correct.

### Added

- **Unified result/error model (`SolidResult` / `SolidError`)** — the foundation for collapsing the
  library's six historical error idioms into one. `SolidResult<T>` is `Success(value)` |
  `Failure(SolidError)`, with combinators (`map`/`flatMap`/`fold`/`recover`) and accessors
  (`getOrNull`/`errorOrNull`/`getOrThrow`). `SolidError` is a typed, sealed superset of every existing
  failure (HTTP statuses, transport/TLS/timeout/cancellation, and the sharing/notification/access
  domain), each carrying a machine `code` (`SolidErrorCode`), a `retryable` hint, the originating
  `httpStatus`, and the `cause`. HTTP statuses map to an error in exactly one place
  (`SolidError.fromHttp`); throwables via `SolidError.fromThrowable`. Transitional `toResult()` /
  `toNetworkResponse()` bridges let features migrate one at a time while everything keeps compiling —
  callers can already opt in with `response.toResult()`. **Additive**; the per-feature migration of
  public signatures (and the retirement of `SolidNetworkResponse` / `DataModuleResult`) follows.

### Fixed

- **Binary resources no longer corrupt over IPC**: `NonRDFResource` parcels its body as raw bytes
  instead of round-tripping through a UTF-8 string, so images, PDFs, and `.pkpass` files survive AIDL
  transport byte-for-byte. The ~1 MB binder limit is now documented on the type.
- **Binary reads carry their metadata**: `SolidResourceManager.read(...)` on a non-RDF resource now
  returns the server's response headers (ETag, `Content-Length`, `WAC-Allow`, …) instead of empty
  metadata, so conditional requests and size/last-modified are available without a separate `HEAD`.
- **Foreign RDF types are preserved**: the contacts/tickets/address-book/group codecs no longer strip
  an `rdf:type` written by another application (e.g. `foaf:Person`) when re-serialising a document; a
  new `RDFResource.ensureType(...)` appends the codec's own type without clobbering others.
- **Safer contact/ticket deletes**: the pod resource is deleted first and the result is checked, then
  the index row is removed — a failed delete now surfaces as a failure and leaves the index
  consistent instead of reporting success and leaving a ghost row. `404`/`410` are treated as
  already-deleted.
- **Contact UID is preserved on update**: updating a contact without an explicit `uid` carries the
  existing persistent identifier forward instead of erasing it.
- **Valid `tel:` / `mailto:` IRIs**: phone numbers are stripped of RFC 3966 visual separators and
  remaining illegal characters are percent-encoded, so ordinary formatted numbers no longer produce
  malformed IRIs; a non-IRI contact UID is wrapped as `urn:uid:` and unwrapped on read.
- **Correct date typing**: date-only values (birthday, anniversary, ticket/event dates) are typed
  `xsd:date` rather than `xsd:dateTime`.
- **URI encoding preserves reserved characters**: `encodeUri` / `encodeUriString` now rebuild from the
  raw components and only percent-encode genuinely-illegal characters, so an identifier containing an
  encoded `%2F` / `%23` is no longer corrupted; the operation is idempotent.
- **Optimistic-concurrency cache correctness**: a `412 Precondition Failed` on write now invalidates
  the response cache so a compare-and-swap retry reads fresh state; share-index patch retries add
  randomised backoff, and a failed `updateShare` index write no longer revokes a receiver's existing
  live access.
- **Cancellation is honoured**: the response-cache single-flight follower and the contacts/tickets
  result wrappers rethrow `CancellationException` instead of swallowing it (which previously let a
  cancelled reader busy-spin).
- **Notification impersonation gate hardened**: an inbound share offer is dropped unless its actor is
  provably the resource owner; the bare "same host as the actor's WebID" fallback that let any user on
  a shared multi-tenant pod forge an offer for another user's resource has been removed.
- Tickets reject a blank title; blank seat parts are dropped; group documents are named by UUID rather
  than a title-derived path (so a `#`/`/` in a group name can't corrupt the target URI). The
  in-progress DPoP token-type check is now case-insensitive, matching the post-login path.

### Internal

- Library unit tests now run in CI (`.github/workflows/ci.yml`) and as a gate before release; added a
  Robolectric + coroutines-test harness and ~40 new tests covering the fixes above.
- **Test safety net for the untested core** — added ~40 more tests pinning the behaviour of the code
  that grants access and moves bytes, all of which previously had zero coverage: `WacBackend` and
  `AcpBackend` grant/revoke/list matrices (implied modes, owner re-assertion, append-only, container
  inheritance, 412 retry) against an in-memory ACL/ACR pod that round-trips through the N-Triples
  codec; `SolidHttpClient` over a real `MockWebServer` (DPoP-nonce retry, expired-token
  force-refresh, conditional-write status, redirects, header propagation); the `createShare` index
  write/rollback contract; the `ShareMode`/collapse logic; and the inbox access-request gate. Adds
  `mockwebserver` + `mockito-core` test dependencies.
- **Codec & server-quirk shields pinned** — round-trip/escaping tests for the RDF codecs that carry
  user-controlled and cross-server data: `N3Patch` (literal escaping so a contact name/note can't
  inject triples; N3 + SPARQL-Update rendering; `fromDiff`), the hand-rolled `NTriples` writer/parser
  (typed/language/control-character literals, blank nodes, relative-IRI resolution), the `InruptAcrJson`
  quirk parser (maps Inrupt's remote-`@context` ACR straight to ACP quads instead of an empty set, and
  declines non-Inrupt bodies), and the `GivenSharesIndexRDF` reified-share/legacy-row reader. Adds an
  `org.json` test dependency.

## [0.5.1] — June 2026

Added consumer and proguard rules. 

## [0.5.0] — June 2026

**Resource sharing** and a **Linked Data Notifications inbox**, on top of a
security-focused overhaul of the authentication layer and a clean-architecture refactor of the
`Shared`, `api`, and `client` libraries. **Source-breaking** for external SDK consumers (the project
is pre-1.0 and unstable).

### Features

#### Resource sharing

- New `SharingManager` (`api`), `Solid.getSharingClient()` (`client`), and `IASSharingService`
  (AIDL): share any pod resource or container with another WebID at a chosen level — **View** (Read),
  **Add** (append-only), or **Edit** (read/write) — and change the level or revoke it later,
  optionally notifying the receiver.
- Authorization works on both **Web Access Control (WAC)** and **Access Control Policy (ACP)** pods;
  the backend is auto-selected from the resource's advertised authorization links. A grant writes the
  full set of implied ACL modes (Add = Read + Append, Edit = Read + Write) and the index collapses
  them back to one logical mode per receiver.
- Make a resource **private** (owner-only) in a single call.
- **Add-only uploads**: `createInContainer(...)` lets a recipient with append access POST new files
  and folders into a shared container without read or write access to its other contents.
- **Share links** as `https://solidshare.app/s…` Android App Links, for inviting a receiver
  out-of-band.
- A catalog of given and received shares is persisted as an index on the pod for fast listing and can
  be rebuilt by walking the pod's own ACLs; a configurable exclude list keeps protocol paths
  (`/inbox`, `/profile/card`, the app's own storage) out of it.
- A typed `SharingException` hierarchy (no inbox, unauthorized/forbidden inbox, delivery failure,
  stale ACL, unsupported auth backend, …) surfaces precise failure causes.

#### Notifications & inbox (Linked Data Notifications)

- New `NotificationsManager` (`api`), `Solid.getNotificationsClient()` (`client`), and
  `IASSNotificationsService` (AIDL) implementing a full LDN loop over the user's inbox: an owner
  offers access — or a peer requests it — the notification lands in the target's inbox, and the
  recipient accepts or rejects, with a response notification sent back and the decision recorded.
- The inbox is auto-provisioned and advertised with **public append-but-not-read** access, as the
  notifications and access-control specs require.
- Cross-pod safe: notification senders are verified by reading their WebID profile **anonymously**, a
  profile-document URL is resolved to its real fragment WebID before granting, and a notification's
  declared mode is trusted when a cross-pod access probe is blocked.
- The transport is a generic Linked Data Notifications layer, decoupled from sharing so it can carry
  other notification types later.

#### Authentication

- **Solid-OIDC Client ID Document**: the app can authenticate with a stable, hosted `client_id`
  document instead of per-device dynamic registration — removing the forced re-login that happened
  when a provider garbage-collected an old registration.
- **Per-account DPoP keys**: each account now gets its own DPoP keypair in the Android Keystore
  instead of sharing one key across accounts.

#### Resources & contacts over IPC

- `head()` (metadata-only), `patch()`/`patchRaw()` (N3 Patch), and conditional `update(…, ifMatch)`
  (ETag optimistic concurrency) — added to the in-process `api` in 0.4.0 — are now exposed over IPC,
  so third-party apps reach them through `SolidResourceClient`.
- New **size, created-time, and modified-time** accessors on the resource models, derived from
  `Content-Length`/`Last-Modified` headers and `dcterms`/`stat` triples.
- The **contacts data module** is now wired end-to-end over the IPC service and `client` SDK.

### Improvements

#### Authentication hardening

- The persisted token/profile store is now **encrypted at rest** (AES-256-GCM under an Android
  Keystore key); pre-0.5.0 plaintext stores are migrated transparently on first read.
- A login is **rejected unless the token issuer is authorized by the WebID** (`solid:oidcIssuer`),
  and the **ID token returned by a refresh is re-validated** (signature, issuer, and that the WebID
  never changes mid-session).
- **Silent token refresh fixed** against servers that require a `DPoP-Nonce` on the token endpoint
  (e.g. Inrupt ESS): refresh runs through a DPoP-aware token request that reads the nonce and retries
  once on a `use_dpop_nonce` challenge ([RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449)
  §8). Nonces are tracked **per origin** (§9), a **recoverable** failure (nonce, network, 5xx) no
  longer invalidates the session, and concurrent refreshes for one WebID are **coalesced** so a
  rotated refresh token isn't spent twice.
- Token/header/nonce plumbing (`getLastTokenResponse`, `getAuthHeaders`, `updateDPoPNonce`) is
  **removed from the public `Authenticator`** and moved to an internal session seam — consumers no
  longer handle access tokens or `Authorization`/`DPoP` headers, and AppAuth's `TokenResponse` no
  longer leaks through the public surface. `submitAuthorizationResponse` now takes the redirect
  `Intent` directly. **Source-breaking** (no known external caller).

#### Networking

- `SolidHttpClient` gained an **in-memory response cache** — per-account keyed, TTL freshness,
  ETag/Last-Modified revalidation, single-flight de-duplication of concurrent identical reads, and
  LRU eviction — with write-through invalidation, on by default. It eliminates the redundant repeat
  reads that dominated request time.

#### Library clean-architecture refactor (`Shared` / `api` / `client`)

- **Shared**: the catch-all `domain.*` package is replaced with intent-based packages (`model/`,
  `rdf/`, `http/`, `result/`, `error/`, `util/`, `vocab/`). The public API no longer exposes okhttp
  or titanium-json-ld types — resource models carry a plain `String` content-type and a
  `SolidHeaders` value type, and the JSON-LD codec is an internal `implementation` dependency. RDF
  quads cross the IPC boundary as structured `@Serializable` data instead of being re-serialised to
  JSON-LD per parcel. **Source-breaking** (`getHeaders()` returns `SolidHeaders`; the `MediaType`
  constructors are gone).
- **api**: organised by feature with `internal` implementations; removed the dead
  `SolidAccountResourceManager`; internalised the authorization backends; `jjwt-api`/titanium are no
  longer transitive; added test seams (injectable clock and `OkHttpClient`).
- **client**: extracted a shared `ServiceConnector` (removing ~660 lines of duplicated AIDL
  bind/callback boilerplate) with self-healing binding that re-binds on binder death; added the
  required Android 11+ `<queries>` manifest entry; **unified the error contract** so every client
  method throws `SolidException` (sealed) rather than returning `SolidNetworkResponse.Error`.
  **Source-breaking** for resource calls.

#### App

- The ASS app was restructured into data / domain / ui layers with use cases, and the login and
  sign-in flow reworked accordingly.

### Bug fixes

- Fixed adding received shares from notifications when a cross-pod access probe fails — the
  notification's declared mode is now trusted instead of dropping the share.
- Fixed **ACP** grants to write the implied ACL modes, matching WAC behaviour.
- 401 retry handling now distinguishes a DPoP-nonce rotation from an expired token, force-refreshes
  at most once per call, and never returns an expired or post-failed-refresh token to callers.

## [0.4.1] — May 2026

Namespace migration. Source code, Maven coordinates, and Gradle module folders move under `com.erfangholami.androidsolidservices`. No source-level API changes.

### Maven coordinates

- `com.pondersource.solidandroidclient:solidandroidclient` → `com.erfangholami.androidsolidservices:client`
- `com.pondersource.solidandroidapi:solidandroidapi` → `com.erfangholami.androidsolidservices:api`
- `com.pondersource.shared:shared` → `com.erfangholami.androidsolidservices:shared`

### Kotlin packages

- `com.pondersource.androidsolidservices.*` → `com.erfangholami.androidsolidservices.*`
- `com.pondersource.solidandroidapi.*` → `com.erfangholami.androidsolidservices.api.*`
- `com.pondersource.solidandroidclient.*` → `com.erfangholami.androidsolidservices.client.*`
- `com.pondersource.shared.*` → `com.erfangholami.androidsolidservices.shared.*`

### Gradle modules

- `SolidAndroidApi/` → `api/`
- `SolidAndroidClient/` → `client/`

### App-level changes (ASS)

- `applicationId` is now `com.erfangholami.androidsolidservices`. Existing installs cannot auto-upgrade — users must uninstall the old build and install 0.4.1 fresh.
- AccountManager `accountType` changed accordingly. Existing accounts on user devices will be orphaned.
- `appAuthRedirectScheme` changed. Update any OAuth callback URIs registered with Solid identity providers.

### Sharing 
Add basic classes for sharing resources

---

## [0.4.0] — May 2026

### New API — `SolidResourceManager`

- `head(webid, uri)` — HTTP HEAD returns `SolidMetadata` (ETag, Content-Type, Content-Length, WAC-Allow, ACL link, Accept-Patch/Post/Put, Last-Modified, and more) without transferring the resource body. Useful for caching checks and permission discovery before a full read.
- `patch(webid, uri, patch)` / `patchRaw(webid, uri, n3Body)` — N3 Patch support for atomic partial updates to RDF resources. The typed overload accepts an `N3Patch` value; the raw overload accepts a pre-serialised `text/n3` string.
- `update()` now accepts `ifMatch` — pass the ETag from a prior `head` or `read` call for optimistic-concurrency protection (server returns 412 on version mismatch).
- `delete(webid, resourceUri: URI)` — delete a resource by URI directly, without reading it first.

### New Type — `N3Patch` (`com.erfangholami.androidsolidservices.shared.domain.crud`)

Type-safe DSL and diff-based factory for building Solid N3 Patch documents:

```kotlin
val patch = N3Patch.build {
    where(contactUri, VCARD.FN, variable = "oldName")
    deleteVar(contactUri, VCARD.FN, variable = "oldName")
    insertLiteral(contactUri, VCARD.FN, "Alice")
}

val patch = N3Patch.fromDiff(originalResource, modifiedResource)
```

### Authentication

- DPoP algorithm negotiation: the generator reads the server's `WWW-Authenticate` header and selects the best supported algorithm, improving compatibility with different pod servers.
- New `IdTokenVerifier` validates claims in received ID tokens.
- Fixed a race condition where token refresh conflicted with an in-flight DPoP nonce update.
- Fixed incorrect WebID extraction from the token response.
- Removed several crash points from the token exchange and session handling paths.

### Multi-account in `SolidAndroidClient`

Third-party apps must now pass the target WebID on resource and contacts calls. This enables correct IPC routing when the user has multiple Solid accounts active.

### Resource Operations

- ETag headers are now sent on PUT requests for optimistic concurrency.
- Resource URIs containing spaces or other special characters are now percent-encoded correctly.
- `delete` and `deleteContainer` paths unified; redundant network round-trips removed.

### Architecture

- Removed the Inrupt Java Client library; replaced with a custom `SolidHttpClient` (OkHttp-based). Significantly leaner dependency footprint.
- API Validator plugin added to all modules. The public API surface is tracked via `.api` files to prevent accidental binary-incompatible changes.
- All AIDL parcelable definitions consolidated in the `Shared` module.
- Release builds of the ASS app are now minified with ProGuard.
- Extended RDF vocabulary constants in the `Shared` module (`Solid`, `DC`, and others).

### Bug Fixes

- Fixed wrong Dublin Core namespace in the Contacts data module; old data is still readable.
- Fixed `ETag` header name casing.
- Fixed granted apps not persisting across process restarts.

### UI

- ASS now shows a dialog prompting users to grant the overlay draw permission when it is missing.
- Updated "Sign in with Solid" login screen.
- UI strings moved to Android string resources.

### Dependencies

- Updated several library versions across all modules.

---

## [0.3.1] — April 2026

- Fix saving accounts bug.

---

## [0.3.0] — April 2026

- Multi-account support — log in with multiple Solid accounts and switch between them from the Settings page.
- All resource and contacts data module methods are now Kotlin `suspend` functions.
- Resource operations return `SolidNetworkResponse<T>` (sealed: `Success`, `Error`, `Exception`); contacts operations return `DataModuleResult<T>`.
- New `SolidException` sealed class hierarchy with typed subclasses.
- Proper DPoP token support for all authenticated pod requests.
- Replaced Gson with `kotlinx.serialization`.
- Upgraded project JVM target from 11 to 17.
- Updated compile SDK from 35 to 36.
- GitHub Actions workflows for publishing libraries and the application.
- Implementation classes are now `internal`, exposing only the public SDK surface.

---

## [0.2.1] — December 2024

- Remove SolidCommunity.net from the login provider list.
- Add app screenshots to documentation.

---

## [0.2.0] — December 2024

- Full contacts management over IPC: create, read, rename, and delete address books, contacts, and groups stored on the pod.
- `Flow<Boolean>` connection state for all IPC services.
- Recursive deletion of LDP containers.
- Support for Solid type index registration (private and public).
- Apps can programmatically revoke their own access grant.
- Extracted common types into a standalone `Shared` library.
- Bug fixes: token saving on network error, app relogin flow, authentication edge cases.

---

## [0.1] — March 2024

Initial public release.

- OpenID Connect login with DPoP via AppAuth.
- Resource CRUD on a Solid pod over IPC (AIDL).
- `SolidContainer` support for listing and navigating containers.
- `getWebId()` exposed as an IPC service.
- Permission dialog and access grant management in the ASS app.
- First version of the `SolidAndroidClient` library (renamed to `client` in v0.4.1).
