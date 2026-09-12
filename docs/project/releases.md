# Releases

All releases are published on the [GitHub Releases page](https://github.com/erfangholami/Android-Solid-Services/releases).

Library versions are published to Maven Central:

- [`client`](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client)
- [`api`](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api)
- [`shared`](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/shared)

---

## v0.7.1 — 12th September 2026

A maintenance release driven by Solid Share's crash reports. Nothing changes on the wire or in the
public API: pin `0.7.1` and rebuild.

### Bug fixes

- **A signed-out account no longer floods telemetry.** A request for a WebID with no usable
  session — never signed in, signed out, or expired — used to trip the transport's auth-header
  precondition and reach the host's crash reporter as a non-fatal on every attempt. It now fails
  fast with `SolidError.NotAuthenticated` (`SolidNotLoggedInException` over IPC) and leaves a
  breadcrumb only. A 401 whose forced token refresh fails is returned as that 401.
  See [Telemetry](../build/telemetry.md).
- **Inbox notifications parse with no network.** The Activity Streams 2.0 context every
  notification names ships inside `Shared`, so reading an inbox no longer depends on `www.w3.org`
  answering; other remote contexts are fetched once and cached in memory.
  See [Notifications](../build/notifications.md).
- **An expired session reaches `client` callers as `SolidNotLoggedInException`** instead of
  `UnknownException`, as the [error reference](../reference/errors.md) promised.

## v0.7.0 — 5th August 2026

The IPC contract is rewritten, contacts is reshaped around one immutable write model, and every
data module moves under a shared root. **Source- and wire-breaking**: the app and the SDK have to
be updated together.

### Breaking changes

- **Two AIDL callbacks replace twenty-two.** Every per-type callback interface is gone, replaced
  by `IASSParcelableCallback` and `IASSParcelableListCallback` carrying a `Bundle` envelope. An app
  built against `client` 0.6.x cannot talk to this version of Android Solid Services, and one built
  against 0.7.0 cannot talk to an older one.
- **Contacts is three role stores, and every call names its WebID.** `contacts.books`,
  `contacts.contacts` and `contacts.groups` replace the flat surface; `NewContact` and
  `FullContact` give way to `ContactData`, built with `contactData { }` and derived with
  `buildUpon { }`. `ContactStore.update` has replace semantics. See [Contacts](../build/contacts.md).
- **`SolidSignInClient.getAccount(webId)`** now takes the WebID it is asking about.
- **Data modules allocate under `{storage}datamodule/`.** Existing pods are not relocated;
  discovery follows the type-index registration.
- **`ExceptionsErrorCode` moved** to `shared.result`, **`SettingTypeIndex` lost its address-book
  helpers**, and **`SharingManager.getShareDeepLink` changed signature**.

### New — Entity sharing

- **Share an entity, not a file.** A contact or a ticket can be shared as the thing it is; share
  records and notifications carry `resourceType` and `resourceName`. Contacts are never shareable
  publicly. See [Sharing](../build/sharing.md).
- **`purgeGivenShares`** withdraws every share under a subtree and removes its bookkeeping.
- **`ShareableEntityStore`** is the seam a data module implements to join typed entity sharing.
- **`SolidSession`** is the session contract `Authenticator` implements, and
  **`SolidAccount.hasRefreshToken`** tells a refreshable session from one that never was.

### Improvements

- One collection engine for every data module, in `api/datamodule/core/`.
- The build enforces the module layering, with empty baselines.
- `listContainer` stops re-HEADing children the listing already described.
- Auth persistence and the HTTP transport have packages of their own.
- The Firebase Performance Gradle plugin is gone, its SDK kept.
- The documentation site is organised by capability, with a published `llms.txt`.

### Bug fixes

- Sessions survive a JWKS outage, a pruned keystore, and having no refresh token.
- A refresh that finishes inline no longer corrupts the in-flight map.
- A client's binder can no longer crash the provider.
- The profile store no longer blocks the main thread while it initialises.
- A partial address book reads as empty instead of crashing.
- Stale index rows are dropped for resources the pod no longer has.

## v0.6.1 — 1st August 2026

Sign-in no longer needs the overlay permission, Solid profiles become real Android accounts, and
sessions stop expiring after a day. Several release-only defects are fixed.

### New — Sign-in

- **`AuthorizeWithSolid`**, an `ActivityResultContract` your app launches from its own foreground.
  The app no longer requests `SYSTEM_ALERT_WINDOW` at all, and the dialog that used to demand it
  on first launch is gone. See [Getting Started](../start/quickstart.md).
- **Solid accounts in Android Settings.** Each signed-in WebID appears under Settings → Accounts.
  "Add account" there opens sign-in and returns where it was invoked; removing an account signs
  that profile out. `ChooseSolidAccount` offers the system account chooser to apps that want it.
- **Add an account mid-sign-in** — the authorize dialog can hand off to login, and the new account
  is waiting in the list on return.

### Improvements

- **No dangerous permissions.** `SYSTEM_ALERT_WINDOW` went with the overlay dialog, and the three
  account permissions were dropped — `GET_ACCOUNTS` belongs to Android's **Contacts** group, so the
  app looked like it wanted your contacts, while `AUTHENTICATE_ACCOUNTS` and `MANAGE_ACCOUNTS` have
  been deprecated since API 23 and 22. Only normal-level permissions remain.
- **Static client registration.** The app identifies itself with a hosted
  [Client ID Document](https://androidsolidservices.erfangholami.com/client.jsonld) instead of
  registering dynamically with each provider.
  Dynamic registrations expire — Inrupt discards them after 24 hours and the refresh token dies
  with them, which is what forced a fresh sign-in roughly once a day. Existing sessions keep the
  registration they were created with; only new sign-ins use the hosted identity. Your own app can
  do the same: see [Client ID Document](../reference/client-id-document.md).
- **PATCH on SPARQL-only servers** — a `text/n3` patch refused with 415 is restated as SPARQL
  Update and retried, so patching works on Inrupt ESS over IPC too.
- **Versions come from the git tag**, so a release is a tag and nothing is edited by hand.
- **The `client` SDK gained a test suite** — 36 unit and 152 instrumented tests driving every call
  across a real binder, running on an emulator in CI.

### Bug fixes

- **Metadata calls failed in release builds.** R8 renamed the parcelable models, and their names
  travel inside the parcel, so `head`, `headPublic`, `readContainer` and enriched `listContainer`
  raised `BadParcelableException` in every consumer.
- **A crash in the app hung the caller forever.** Failures now arrive as typed errors, and a call
  left parked on a dead service is retried after a rebind.
- **Out-of-range enum values crashed the app** — any app could reach the exported sharing and
  notification services with an unknown share mode or receiver kind.
- **Third-party apps could not build** — `Shared` exposed AppAuth, forcing consumers to declare an
  `appAuthRedirectScheme` placeholder for a flow they never run.
- **A provider address without `https://` crashed the app** when signing in with a custom provider.
- **A login finishing after the main screen opened** left no system account until the next cold start.
- **The authorize dialog flashed a purple status bar** as it opened and closed.

!!! warning "`requestLogin` no longer works"

    The deprecated `SolidSignInClient.requestLogin` drew its picker over the calling app from a
    background service, which Android allows only with the overlay permission the app has now
    dropped. It fails immediately with `SolidServicesDrawPermissionDeniedException` instead of
    leaving you waiting on a callback that cannot arrive. Migrate to `AuthorizeWithSolid`.

!!! note

    The account type changed from a placeholder to `com.erfangholami.androidsolidservices`. Android
    purges accounts of the old type on update, and they are re-registered on first launch — sessions
    and pod data are untouched.

---

## v0.6.0 — 30th July 2026

Tickets, WebID profiles, streaming and live notifications, on a unified result type. Adds crash
reporting on the Play build and a Firebase-free build for F-Droid. **Source-breaking** for SDK
consumers (the project is pre-1.0 and unstable).

### New — Tickets

- Store passes and reservations on a pod, with full `.pkpass` parity — pass identity and
  web-service block, beacons, relevancy interval, colours and rich detail fields — plus BCBP
  coverage for boarding passes.
- Pass images live in per-ticket sub-containers; colours are cached on index rows so a wallet list
  paints from a single GET.

### New — Profiles, streaming and live updates

- **WebID profile API**: read a profile (merging linked documents), update name fields, set an avatar.
- **Streaming read/write with progress**, so large files no longer buffer entirely in memory.
- **Live resource notifications** over `WebSocketChannel2023`, exposed as a lifecycle-scoped `Flow`
  alongside the existing inbox polling.

### New — Monitoring and distribution

- Crash reporting and performance monitoring on the **Google Play build**, through a `TelemetrySink`
  seam in `Shared`. The published libraries take on no monitoring dependency and stay silent until a
  host app installs a sink — you can plug in your own (Sentry, OpenTelemetry, logs).
- Network spans report the **origin only** (`scheme://host[:port]`), never pod paths. See the
  [Privacy Policy](privacy.md).
- A **FOSS build flavour** with no Firebase or Play Services, for F-Droid.

### Improvements

- **One result type**: every API returns `SolidResult<T>` with a typed `SolidError` (machine code,
  retryable), replacing six historical error idioms. **Breaking.**
- **String IRIs** replace `URI` across the public API. **Breaking.**
- The `client` SDK reaches **every `api` capability** over IPC — tickets, contacts, derived resource
  verbs and streaming.
- **Lost-update protection**: contact, group and ticket edits use conditional `If-Match` writes with
  retry, falling back to weak ETags on servers like NSS.
- New resource helpers — `exists`, `ensureContainer`, `probeAccess`.
- Contacts gain instant-messaging handles, vCard `GEO` and `LANG`.
- Type-index registration uses compare-and-swap instead of a blind write.
- Narrowed the `api` consumer R8 rules: jjwt's implementation tree keeps only what is reached
  reflectively instead of every member, so apps embedding `api` pin far less.
- Build and tooling: ktlint and detekt in CI on every PR, a published
  [API reference](../api/index.html), `targetSdk` 36, AGP and SDK 37.

### Bug fixes

- **Login**: restored the lowercase Solid-OIDC `webid` scope and ID-token claim, and 303 redirects
  are now followed when resolving a WebID.
- **Sessions**: accounts issued no refresh token stay signed in until their access token actually
  expires; expired sessions stay visible instead of silently disappearing.
- **Sharing**: ACP grant/revoke fails fast instead of silently wiping co-shares; WAC surfaces
  inherited access.
- **Transport**: redirects re-sign DPoP per hop, PATCH negotiates its format, and pod storage is
  discovered rather than assumed.
- Fixed binary corruption over IPC; cancellation is no longer retried.

---

## v0.5.1 — 16th June 2026

### Improvements

- The libraries ship consumer ProGuard rules, so minified apps need no keep rules of their own.

---

## v0.5.0 — 16th June 2026

Headline release: **resource sharing** and a **Linked Data Notifications inbox**, plus a
security-focused overhaul of authentication and a clean-architecture refactor of the libraries.
**Source-breaking** for external SDK consumers (the project is pre-1.0 and unstable).

### New — Resource sharing

- **`SharingManager`** (`api`) / **`Solid.getSharingClient()`** (`client`): share any pod resource or
  container with another WebID at a chosen level — **View** (Read), **Add** (append-only), or **Edit**
  (read/write) — and change the level or revoke it later, optionally notifying the receiver.
- Authorization works on both **Web Access Control (WAC)** and **Access Control Policy (ACP)** pods;
  the backend is auto-selected from the resource's advertised authorization links.
- Make a resource **private** (owner-only) in one call; add-only recipients can upload into a shared
  container via `createInContainer(...)` without read/write on its other contents.
- **Share links** as `https://solidshare.app/s…` Android App Links, a public catalog of given/received
  shares (rebuildable from the pod's own ACLs), and a typed `SharingException` hierarchy.

### New — Notifications & inbox (Linked Data Notifications)

- **`NotificationsManager`** (`api`) / **`Solid.getNotificationsClient()`** (`client`): a full LDN
  loop over the user's inbox. An owner offers access — or a peer requests it — the notification lands
  in the target's inbox, and the recipient accepts or rejects, with a response sent back.
- The inbox is auto-provisioned with **public append-but-not-read** access. Notification senders are
  verified cross-pod by reading their WebID profile anonymously. Pull-only (no push subscription yet).

### New — Authentication

- **Solid-OIDC Client ID Document** — authenticate with a stable, hosted `client_id` instead of
  per-device dynamic registration, removing forced re-logins when a provider drops an old
  registration. See [Using a Client ID Document](../reference/client-id-document.md).
- **Per-account DPoP keys** — each account gets its own DPoP keypair in the Android Keystore.

### New — Resources & contacts over IPC

- `head()`, `patch()`, and conditional `update(…, ifMatch)` (ETag optimistic concurrency) are now
  reachable over IPC through `SolidResourceClient`.
- New **size**, **created-time**, and **modified-time** accessors on the resource models.
- The **contacts data module** is now wired end-to-end over the IPC service and `client` SDK.

### Improvements

- **Authentication hardening** — the token/profile store is now **encrypted at rest** (AES-256-GCM
  via an Android Keystore key, with transparent migration of pre-0.5.0 plaintext stores); logins are
  rejected unless the token issuer is authorized by the WebID (`solid:oidcIssuer`); the ID token
  returned by a refresh is re-validated; silent token refresh is fixed against servers that enforce a
  `DPoP-Nonce` on the token endpoint (e.g. Inrupt ESS), with per-origin nonce tracking and coalesced
  concurrent refreshes. Transport-level token/header plumbing was removed from the public
  `Authenticator` (**source-breaking**; no known external caller).
- **Networking** — `SolidHttpClient` gained an in-memory response cache (per-account keyed, TTL
  freshness, ETag/Last-Modified revalidation, single-flight de-duplication, LRU eviction) with
  write-through invalidation, on by default.
- **Library refactor** — `Shared` reorganized into intent-based packages (`model/`, `rdf/`, `http/`,
  …) and stripped of okhttp / titanium-json-ld types on its public API (resource models now expose a
  `String` content-type and a `SolidHeaders` value type); the `client` library extracted a shared,
  self-healing `ServiceConnector` and **unified its error contract** so every method throws
  `SolidException` (**source-breaking** for resource calls).

### Bug fixes

- Fixed adding received shares from notifications when a cross-pod access probe fails.
- Fixed ACP grants to write the implied ACL modes, matching WAC behaviour.
- 401 retry handling now distinguishes a DPoP-nonce rotation from an expired token and never returns
  an expired or post-failed-refresh token to callers.

---

## v0.4.1 — 12th May 2026

Namespace migration release. No new features; everything moves under `com.erfangholami.androidsolidservices`.

### Maven coordinates

The three published libraries now share one groupId with short artifact ids:

| Previous coordinates                                       | New coordinates                                |
|------------------------------------------------------------|------------------------------------------------|
| `com.pondersource.solidandroidclient:solidandroidclient`   | `com.erfangholami.androidsolidservices:client` |
| `com.pondersource.solidandroidapi:solidandroidapi`         | `com.erfangholami.androidsolidservices:api`    |
| `com.pondersource.shared:shared`                           | `com.erfangholami.androidsolidservices:shared` |

Update the dependency lines in your module-level `build.gradle.kts`.

### Gradle modules and Kotlin packages

The local Gradle module folders (`SolidAndroidApi/` → `api/`, `SolidAndroidClient/` → `client/`) and source packages (`com.pondersource.*` → `com.erfangholami.androidsolidservices.*`) were renamed to match the new coordinates. If you consume the libraries via Maven Central, this affects only your `import` statements; if you build this project from source, update local module paths in scripts and CI.

### App-level changes (Android Solid Services host app)

- `applicationId` is now `com.erfangholami.androidsolidservices`. The host app on existing devices cannot auto-upgrade — users have to uninstall the old build and install 0.4.1 fresh.
- The AccountManager `accountType` changed to match. Existing Solid accounts on user devices will be orphaned and must be re-added.
- The AppAuth redirect scheme changed. If you registered the OAuth callback with a specific Solid identity provider, update the redirect URI provider-side.

---

## v0.4.0 — 3rd May 2026

### New API — `SolidResourceManager`

- **`head(webid, uri)`** — HTTP HEAD returns a `SolidMetadata` object (ETag, Content-Type, Content-Length, WAC-Allow, ACL link, Accept-Patch/Post, Last-Modified, and more) without transferring the resource body. Ideal for caching checks and permission discovery before a full read.
- **`patch(webid, uri, patch)` / `patchRaw(webid, uri, n3Body)`** — N3 Patch support for atomic partial updates to RDF resources. The typed overload accepts an `N3Patch` value; the raw overload accepts a pre-serialised `text/n3` string.
- **`update()` now accepts `ifMatch`** — pass the ETag from a prior `head` or `read` call for optimistic-concurrency protection (server returns 412 on version mismatch).
- **`delete(webid, resourceUri: URI)`** — delete a resource by URI directly, without reading it first.

### New Type — `N3Patch`

Type-safe DSL and diff-based factory for building [Solid N3 Patch](https://solidproject.org/TR/protocol#n3-patch) documents:

```kotlin
// DSL builder
val patch = N3Patch.build {
    where(contactUri, VCARD.FN, variable = "oldName")
    deleteVar(contactUri, VCARD.FN, variable = "oldName")
    insertLiteral(contactUri, VCARD.FN, "Alice")
}

// Auto-diff from two resource states
val patch = N3Patch.fromDiff(originalResource, modifiedResource)
```

### Authentication

- **DPoP algorithm negotiation** — the DPoP generator now reads the `WWW-Authenticate` response header and selects the best algorithm the server supports; improves compatibility with different pod implementations.
- **ID token verification** — new `IdTokenVerifier` validates claims in the received ID token.
- **DPoP nonce conflict fix** — token refresh no longer races with an in-flight nonce update.
- **WebID parsing fix** — correctly extracts the WebID string from the token response.
- **Crash fixes** — multiple crash points removed from the token exchange and session handling paths.

### Multi-account in `client`

Third-party apps must now pass the target WebID on each resource and contacts call. This enables per-account IPC routing when the user has multiple Solid accounts logged in.

### Resource Operations

- **ETag on writes** — PUT requests now include `ETag` headers for optimistic concurrency.
- **Special characters in URIs** — resource URIs containing spaces and other characters are now percent-encoded correctly.
- **Unified delete** — `delete` and `deleteContainer` paths merged; redundant network round-trips removed.

### Architecture

- **Removed Inrupt Java Client library** — replaced with a custom `SolidHttpClient`; significantly leaner dependency footprint.
- **API binary compatibility enforcement** — API Validator plugin added to all modules; the public surface is tracked via `.api` files to prevent accidental breakage.
- **AIDL consolidated in `Shared`** — all parcelable definitions moved to the `Shared` module; no more duplication across modules.
- **ProGuard + minification** — release builds of the ASS app are now minified.
- **Extended vocabulary** — new RDF vocabulary constants added to the `Shared` module for broader developer use.

### Bug Fixes

- Fixed wrong Dublin Core namespace in the Contacts data module; old data using the incorrect namespace is still readable.
- Fixed `ETag` header name casing.
- Fixed granted apps not persisting across process restarts.

### UI

- ASS now shows a dialog prompting users to grant the overlay draw permission when it is missing.
- Updated "Sign in with Solid" login screen.
- UI strings moved to Android string resources.

### Dependencies

- Updated several library versions across all modules.

---

## v0.3.1 — 14th April 2026

- Fix saving accounts bug.

---

## v0.3.0 — 13th April 2026

- **Multi-account support** — log in with multiple Solid accounts and switch between them from the Settings page.
- **Suspend functions** — all resource and contacts data module methods are now Kotlin `suspend` functions instead of callback-based, for cleaner coroutine integration.
- **Unified result types** — resource operations return `SolidNetworkResponse<T>` (sealed: `Success`, `Error`, `Exception`); contacts operations return `DataModuleResult<T>`.
- **Structured exceptions** — new `SolidException` sealed class hierarchy with typed subclasses (`SolidAppNotFoundException`, `SolidServiceConnectionException`, `SolidNotLoggedInException`, `SolidResourceException`, etc.).
- **DPoP authentication** — proper DPoP (Demonstration of Proof-of-Possession) token support for all authenticated pod requests.
- **kotlinx.serialization** — replaced Gson for better Kotlin compatibility.
- **JVM 17** — upgraded project JVM target from 11 to 17.
- **Compile SDK 36** — updated compile SDK from 35 to 36.
- **CI/CD** — GitHub Actions workflows for publishing libraries and the application.
- **Internal API encapsulation** — implementation classes are now `internal`, exposing only the public SDK surface.

---

---

## v0.2.1 — 19th December 2024

- Remove SolidCommunity.net from the login provider list (simplify provider options).
- Add app screenshots to documentation.

---

## v0.2.0 — 17th December 2024

- **Contacts data module** — full contacts management over IPC: create, read, rename, and delete address books, contacts, and groups stored on the pod.
- **Suspend functions for contacts** — all contacts data module methods converted from callbacks to `suspend` functions.
- **Service connection flow** — added `Flow<Boolean>` connection state for all IPC services so apps can react to connect/disconnect events.
- **Container deletion** — recursive deletion of LDP containers and their contents.
- **Private and public type indexes** — support for Solid type index registration.
- **Disconnect from Solid** — apps can now programmatically revoke their own access grant.
- **Shared module** — extracted common types (resource model, AIDL parcelables, contacts types) into a standalone `Shared` library.
- **Bug fixes** — token saving on network error, app relogin flow, authentication edge cases, reading RDF resources as NonRDF.

---

## v0.1 — 20th March 2024

Initial public release.

- **Authentication** — OpenID Connect login with Inrupt and SolidCommunity.net identity providers; browser-based auth flow via AppAuth.
- **DPoP** — DPoP authentication headers on all pod requests.
- **Resource CRUD** — read, create, update, and delete resources on a Solid pod over IPC (AIDL).
- **SolidContainer** — support for listing and navigating pod containers (directories).
- **WebID** — `getWebId()` exposed as an IPC service.
- **Access grants** — permission dialog in ASS when a third-party app requests access; grants tracked in the Settings page.
- **ASS app** — initial Jetpack-based UI with login, settings, and access grant management.
- **Client library** — first version of `client` connecting to ASS over AIDL.

---

!!! question "Found a bug or want to request a feature?"
    Please [open an issue on GitHub](https://github.com/erfangholami/Android-Solid-Services/issues). Include your device/emulator Android version, library version, and any relevant error output.
