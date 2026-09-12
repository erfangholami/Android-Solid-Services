# Changelog

All notable changes to this project are documented here.

## [0.7.2] — 12th September 2026

A one-fix release: Community Solid Server pods no longer break every second read.

### Bug fixes

- **A `304 Not Modified` that carries a `Content-Length` no longer fails the request.** Community
  Solid Server answers a conditional `GET` for a resource served in its stored format with a `304`
  plus the `Content-Length` of the full body and no body. OkHttp treats that as a truncated body and
  throws, which surfaced as "could not reach your pod" on every re-read of the share indexes on
  such servers (seen on `solid.redpencil.io`). The transport no longer reads a body on `304`, `204`
  or `HEAD` responses.

## [0.7.1] — 12th September 2026

A maintenance release driven by Solid Share's crash reports: a signed-out account no longer floods
telemetry, inbox notifications parse with no network, and `client` callers see an expired session
as `SolidNotLoggedInException`. Nothing changes on the wire or in the public API.

### Bug fixes

- **A request for an account with no valid session fails as `SolidError.NotAuthenticated`.** It
  used to trip the auth-header precondition, surface as an `IllegalArgumentException`, and reach
  the host's crash reporter as a non-fatal on every attempt. That made it the largest issue on
  Solid Share's Crashlytics, with one event per request for each signed-out account. The transport
  now returns the typed error and leaves a log breadcrumb only. A 401 whose forced refresh fails
  comes back as that 401 instead of looping into the same precondition.
- **Activity Streams notifications parse with no network.** The JSON-LD processor fetched the
  `https://www.w3.org/ns/activitystreams` context from `www.w3.org` on every parse, so reading an
  inbox failed offline or whenever that host was slow. The context now ships inside `Shared` and
  is served locally. Other remote contexts are fetched once and kept in an in-memory cache.
- **An expired session reaches `client` callers as `SolidNotLoggedInException`.** The IPC host
  mapped `NOT_AUTHENTICATED` to `UnknownException`; it now maps it to `SOLID_NOT_LOGGED_IN`, which
  is what the error reference promised.

### Notes

- A request for a WebID with no usable session now fails with `SolidError.NotAuthenticated`
  (code `NOT_AUTHENTICATED`) instead of `SolidError.Unknown` wrapping an `IllegalArgumentException`.
  Branch on the code, not on the message.

## [0.7.0] — 5th August 2026

The IPC contract is rewritten, contacts is reshaped around one immutable write model, and every
data module moves under a shared root. Source- and wire-breaking: the app and the SDK have to be
updated together.

### Breaking changes

- **Two AIDL callbacks replace twenty-two.** Every per-type callback interface
  (`IASSStringCallback`, `IASSBooleanCallback`, `IASSUnitCallback`, `IASSGivenShareCallback`, and
  the rest) is gone, replaced by `IASSParcelableCallback` and `IASSParcelableListCallback`
  carrying a `Bundle` envelope. **An app built against `client` 0.6.x cannot talk to this version
  of Android Solid Services, and an app built against 0.7.0 cannot talk to an older one** — the
  two must be updated as a pair.
- **Contacts is three role stores, and every call names its WebID.** `contacts.books`,
  `contacts.contacts` and `contacts.groups` replace the flat `createAddressBook` /
  `createNewContact` / `renameContact` / `addNewPhoneNumber` surface. `NewContact` and
  `FullContact` give way to `ContactData` — one immutable snapshot with full vCard 4.0 coverage,
  built with `contactData { }` and derived with `buildUpon { }` — and to `SolidContact`. Note that
  `ContactStore.update` has **replace** semantics: properties absent from the snapshot are
  removed, so derive from the stored one rather than building a fresh snapshot.
- **`SolidSignInClient.getAccount(webId)`** now takes the WebID it is asking about.
- **Data modules allocate under `{storage}datamodule/`.** Existing pods are *not* relocated:
  discovery follows the type-index registration, so an address book or wallet registered under the
  older root keeps working exactly where it is. Only fresh allocations use the new root.
- **`ExceptionsErrorCode` moved** from `shared.error` to `shared.result`.
- **`SettingTypeIndex` lost its address-book helpers** — `getAddressBooks`, `addAddressBook`,
  `containsAddressBook` and `removeAddressBook`. The collection toolkit owns that registration now.
- **`SharingManager.getShareDeepLink` changed signature.**

### Features

- **Share an entity, not a file.** A contact or a ticket can be shared as the thing it is: the
  grant covers the entity's whole container, so a receiver gets the document and its photo or
  artifact together. Share records and notifications now carry `resourceType` and `resourceName`,
  so a receiving app can say "Alice shared a contact" instead of showing a bare URI. Contacts are
  never shareable publicly — that is enforced at the contract level, not left to UI policy.
- **`purgeGivenShares(webId, resourceUri, includeDescendants, notifyReceivers)`** — withdraw every
  share under a subtree and remove its bookkeeping, so records stop outliving the resources they
  describe and pointing at URIs that no longer resolve.
- **`ShareableEntityStore`** — the seam a data module implements to become first-class in typed
  entity sharing, without the sharing engine learning anything about the module.
- **`SolidSession`** — the session contract `Authenticator` now implements, separating what a
  caller needs from how sessions are stored.
- **`SolidAccount.hasRefreshToken`** — distinguishes a session that can be refreshed from one that
  never could, so expiry can be surfaced as a state rather than a surprise failure.

### Improvements

- **One collection engine for every data module.** Container bootstrap, type-index registration,
  UUID allocation, index caching and attachment naming live once in `api/datamodule/core/` instead
  of being reimplemented per module.
- **The build enforces the layering.** Module dependency rules are checked rather than documented,
  with empty baselines so nothing pre-existing is grandfathered in.
- **`listContainer` stops re-HEADing children** the listing already described — one request per
  child saved on servers that enrich their listings, with `enrichWithHead` for those that do not.
- **Auth persistence and the HTTP transport have packages of their own**, and session, refresh
  policy and account state are real seams rather than internals of one class.
- **The Firebase Performance Gradle plugin is gone**, its SDK kept — the plugin's build-time
  instrumentation was doing nothing the code did not already do explicitly.
- **The documentation site is organised by capability.** One page per thing you can build,
  usage first with the pod-level detail folded away, `client` and `api` shown as linked tabs, and
  a published `llms.txt` for agents. Dependency versions are generated from the git tag, so they
  cannot drift again.

### Bug fixes

- **Sessions survive a JWKS outage, a pruned keystore, and having no refresh token** — each was
  previously indistinguishable from a revoked session, and cost the user a fresh sign-in.
- **A refresh that finishes inline no longer corrupts the in-flight map**, which could leave a
  session wedged until the process restarted.
- **A client's binder can no longer crash the provider.** A misbehaving or dying consumer took the
  host app down with it.
- **The profile store no longer blocks the main thread while it initialises.**
- **A partial address book reads as empty instead of crashing.** A book root missing its index
  links used to sink the whole listing.
- **Stale index rows are dropped** for resources the pod no longer has.

### Notes

- Pre-1.0: the SDK is source-breaking between minor versions and the IPC contract changes with it.
  Pin a version, and update the app and the libraries together.

## [0.6.1] — 1st August 2026

Sign-in no longer needs the overlay permission, Solid profiles become real Android accounts, and
several release-only defects are fixed.

### Features

- **Overlay-free sign-in** — `AuthorizeWithSolid`, an `ActivityResultContract` your app launches
  from its own foreground. The app no longer requests `SYSTEM_ALERT_WINDOW` at all, and the
  dialog that used to demand it on first launch is gone.
- **Android Accounts** — each signed-in WebID appears in Settings → Accounts. "Add account" there
  opens the app's sign-in and returns where it was invoked; removing an account signs the profile
  out. `ChooseSolidAccount` offers the system account chooser to apps that want it.
- **Add an account mid-sign-in** — the authorize dialog can hand off to login, and the new account
  appears in it on return.
- **PATCH on SPARQL-only servers** — a `text/n3` patch refused with 415 is restated as SPARQL
  Update and retried, so patching works on Inrupt ESS through the IPC surface.

### Improvements

- **No dangerous permissions.** `SYSTEM_ALERT_WINDOW` went with the overlay dialog, and the three
  account permissions were dropped: `GET_ACCOUNTS` belongs to Android's **Contacts** group, so the
  app looked like it wanted the user's contacts, while `AUTHENTICATE_ACCOUNTS` and
  `MANAGE_ACCOUNTS` have been deprecated since API 23 and 22. Accounts are read by type instead of
  from the device-wide list, which needs no permission for accounts the app authenticates. Only
  normal-level permissions remain.
- **Static client registration** — the app now identifies itself with a hosted Solid-OIDC
  [Client ID Document](https://androidsolidservices.erfangholami.com/client.jsonld) instead of
  registering dynamically with each provider. A dynamic registration expires — Inrupt discards
  them after 24 hours, and the refresh token dies with the registration, which is what forced a
  fresh sign-in roughly once a day. Existing sessions keep the registration they were created
  with; only new sign-ins use the hosted identity.
- **Versioning from the git tag** — `versionName`, `versionCode` and the Maven coordinates all
  derive from `vX.Y.Z`; releasing is tagging.
- **`client` gains a test suite** — 36 unit and 152 instrumented tests driving every SDK call
  across a real binder, with the instrumented suite running on an emulator in CI.
- **Workflows** — CI on pull requests and pushes to `dev`; a release-tagged push runs the release
  alone, whose gate already runs CI on the tag.

### Bug fixes

- **Metadata calls failed in release builds.** R8 renamed the parcelable models, and their names
  travel inside the parcel, so `head`, `headPublic`, `readContainer` and enriched `listContainer`
  raised `BadParcelableException` in every consumer.
- **A crash in the app hung the caller forever.** Throwables escaping the AIDL dispatchers killed
  the process, and a call parked on a callback was never resumed. Failures now arrive as typed
  errors, and a dead service is retried after a rebind.
- **Out-of-range enum values crashed the app** — any app could reach the exported sharing and
  notification services with an unknown mode or receiver kind.
- **Third-party apps could not build** — `Shared` exposed AppAuth, forcing consumers to declare an
  `appAuthRedirectScheme` placeholder for a flow they never run.
- **A provider address without `https://` crashed the app.** A bare domain or an `http://` address
  reached the OIDC library, which rejects both from a background thread the app cannot catch.
  Addresses are now completed to `https://` and refused with a message when they cannot be.
- **A login finishing after the main screen opened** left no system account until the next cold
  start.
- **The authorize dialog flashed a purple status bar** as it opened and closed.

### Notes

- **`SolidSignInClient.requestLogin` no longer works.** It drew its picker over the calling app
  from a background service, which Android allows only with the overlay permission the app has now
  dropped. It fails immediately with `SolidServicesDrawPermissionDeniedException` rather than
  leaving the caller waiting on a callback that cannot arrive. The method stays on the AIDL
  interface so installed apps keep their transaction numbering. Migrate to `AuthorizeWithSolid`.
- The account type changed from a placeholder to `com.erfangholami.androidsolidservices`. Android
  purges accounts of the old type on update and they are re-registered on first launch; sessions
  and pod data are untouched.

## [0.6.0] — 30th July 2026

Tickets, WebID profiles, streaming and live notifications, on a unified result type. Adds crash
reporting on the Play build and a Firebase-free build for F-Droid. **Source-breaking** for SDK
consumers (pre-1.0).

### Features

- **Tickets data module** — store passes and reservations on a pod, with full `.pkpass` parity
  (identity and web-service block, beacons, relevancy, colours, rich detail fields) and BCBP
  coverage. Pass images live in per-ticket sub-containers.
- **WebID profile API** — read a profile (merging linked documents), update name fields, set an
  avatar.
- **Live resource notifications** — `WebSocketChannel2023` changes exposed as a lifecycle-scoped
  `Flow`, alongside the existing inbox polling.
- **Streaming read/write with progress**, so large files no longer buffer in memory.
- **Full IPC parity** — the `client` SDK now reaches every `api` capability, including tickets,
  contacts, derived resource verbs and streaming.
- **Resource helpers** — `exists`, `ensureContainer` and `probeAccess` replace duplicated call sites.
- **Contacts** gain instant-messaging handles, vCard `GEO` and `LANG`.
- **Crash reporting and performance monitoring** on the Google Play build, via a `TelemetrySink`
  seam in `Shared`. The libraries take on no monitoring dependency and stay silent until a host app
  installs a sink. Network spans report the origin only, never pod paths.
- **A FOSS build flavour** with no Firebase or Play Services, so the app can ship to F-Droid.

### Improvements

- **One result type.** Every API returns `SolidResult<T>` with a typed `SolidError` (machine code,
  retryable), replacing six historical error idioms. **Breaking.**
- **String IRIs** replace `URI` across the public API. **Breaking.**
- **Lost-update protection** — contact, group and ticket edits use conditional `If-Match` writes with
  retry, falling back to weak ETags on servers like NSS.
- **Type-index registration** uses compare-and-swap instead of a blind write.
- Narrowed the `api` consumer R8 rules: jjwt's implementation tree keeps only what is reached
  reflectively instead of every member, so apps embedding `api` pin far less.
- Sharing and authentication split into focused collaborators; the facades shrink substantially.
- Pass colours cached on index rows, so the wallet list paints from a single GET.
- Build and tooling: Spotless/ktlint and detekt with baselines, CI on every PR gating releases, a
  published Dokka API reference, `targetSdk` 36, AGP and SDK 37, v3 signing. Drops `work-gcm` and
  protobuf.

### Bug fixes

- **Login** — restored the lowercase Solid-OIDC `webid` scope and ID-token claim, broken by a rename;
  303 redirects are now followed when resolving a WebID.
- **Sessions** — accounts issued no refresh token stay alive until their access token expires instead
  of being dropped on the first forced refresh. Expired sessions remain visible via
  `expiredProfilesFlow` rather than disappearing.
- **Sharing** — ACP grant/revoke fails fast instead of silently wiping co-shares; WAC surfaces
  inherited access.
- **Transport** — redirects re-sign DPoP per hop, PATCH negotiates its format, pod storage is
  discovered rather than assumed.
- Binary IPC corruption and empty non-RDF metadata; cancellation is no longer treated as retryable.

## [0.5.1] — 16th June 2026

### Improvements

- Ship consumer ProGuard rules with the libraries, so minified apps need no keep rules of their own.
- Add the app's own R8 rules.

## [0.5.0] — 16th June 2026

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

## [0.4.1] — 12th May 2026

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

## [0.4.0] — 3rd May 2026

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

## [0.3.1] — 14th April 2026

- Fix saving accounts bug.

---

## [0.3.0] — 13th April 2026

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

## [0.2.1] — 19th December 2024

- Remove SolidCommunity.net from the login provider list.
- Add app screenshots to documentation.

---

## [0.2.0] — 17th December 2024

- Full contacts management over IPC: create, read, rename, and delete address books, contacts, and groups stored on the pod.
- `Flow<Boolean>` connection state for all IPC services.
- Recursive deletion of LDP containers.
- Support for Solid type index registration (private and public).
- Apps can programmatically revoke their own access grant.
- Extracted common types into a standalone `Shared` library.
- Bug fixes: token saving on network error, app relogin flow, authentication edge cases.

---

## [0.1] — 20th March 2024

Initial public release.

- OpenID Connect login with DPoP via AppAuth.
- Resource CRUD on a Solid pod over IPC (AIDL).
- `SolidContainer` support for listing and navigating containers.
- `getWebId()` exposed as an IPC service.
- Permission dialog and access grant management in the ASS app.
- First version of the `SolidAndroidClient` library (renamed to `client` in v0.4.1).
