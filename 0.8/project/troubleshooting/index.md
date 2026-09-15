# Troubleshooting

Common errors and how to fix them. If your issue isn't listed here, [open an issue on GitHub](https://github.com/erfangholami/Android-Solid-Services/issues) — include your Android version, library version, and the full exception message.

______________________________________________________________________

## Installation & Connection

### `SolidAppNotFoundException`

**Cause:** Solid Share, the host app, is not installed on the device. The message says so, or says that only the retired Android Solid Services app is installed — that app stopped at 0.7.2 and the 0.8.0 SDK never binds to it.

**Fix:** Check before you call, and send the user to install it:

```kotlin
if (!Solid.isHostInstalled(context)) {
    startActivity(Solid.hostInstallIntent(context))   // the store, or solidshare.app
}
```

______________________________________________________________________

### The host is installed, but the SDK says it is not the real one

**Cause:** An app holds Solid Share's package name but is not signed with Solid Share's key. The SDK compares the SHA-256 of the installed host's signing certificate against the digest it ships, and refuses a mismatch. You see this most often after you replace the store copy with a build of Solid Share you made yourself: your build carries your own key. (Android will not install one over the other, so this follows an uninstall.)

**Fix:** Install Solid Share from Google Play, F-Droid or the project's GitHub Releases. To keep working against a host you built, build **your own app** as a debug build — the SDK skips the check for a debuggable caller, and reads that flag from the caller, never from the host. A release build of your app always checks.

______________________________________________________________________

### `SolidServiceConnectionException`

**Cause:** The binding to Solid Share dropped and did not come back within the bind timeout — the host was being updated, or was stopped mid-call.

**Fix:** Retry. Every `client` call waits for the binding by itself and the connector rebinds after a death, so there is nothing to gate; the connection-state flow exists for UI, not for correctness.

______________________________________________________________________

### `NotPermissionException` on a call that used to work

**Cause:** The user narrowed your app's grant in Solid Share's Apps tab, or revoked it. The message names what the app holds and what the call needs, for example `Granted VIEW on https://…/notes/; this call needs EDIT`.

**Fix:** Read the current grant with `signIn.getAccount(webId)?.grant`, explain what the feature needs, and launch `AuthorizeWithSolid` again with an `AccessRequest` for it. See [App access](https://androidsolidservices.erfangholami.com/0.8/build/app-access/index.md).

______________________________________________________________________

## Authentication

### Login browser opens but the redirect never returns to your app (`api`)

**Cause:** The `appAuthRedirectScheme` manifest placeholder is missing or doesn't match your package name.

**Fix:** Add it to your module-level `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "YOUR_APP_PACKAGE_NAME"
    }
}
```

The value must exactly match your application ID (e.g. `com.example.myapp`).

______________________________________________________________________

### `SolidNotLoggedInException`

**Cause:** No account is signed in to Solid Share, or that account's session has expired (the refresh token ran out or was revoked by the provider).

**Fix:** Send the user to sign in again: launch the `AuthorizeWithSolid` contract, whose account list stays live while they sign in inside Solid Share.

______________________________________________________________________

### Token refresh fails silently / requests return 401

**Cause:** The authorization server requires a `DPoP-Nonce` on the **token endpoint** (e.g. Inrupt ESS) and an older client couldn't complete the nonce challenge during refresh — so the access token expired and the user was forced to sign in again. Or the refresh token was revoked.

**Fix:** Upgrade to `0.5.0` or later. Nonce handling and token refresh are now fully internal and nonce-aware: the library reads the `DPoP-Nonce`, retries the token request once on a `use_dpop_nonce` challenge ([RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449) §8), tracks nonces **per origin**, and no longer invalidates the session on a *recoverable* failure (nonce/network/5xx).

You no longer call `updateDPoPNonce` or `getLastTokenResponse` — both were **removed** from the public `Authenticator` in 0.5.0. Access tokens and DPoP headers are attached for you; go through the resource / sharing / contacts managers (or the `client` SDK). If the refresh token is genuinely revoked (the session reports unauthorized), re-authenticate through `AuthorizeWithSolid` (client) or `createAuthenticationIntent()` (api).

______________________________________________________________________

### "unsupported_algorithm" error during login

**Cause:** The pod server only supports certain DPoP signing algorithms and the client proposed one it doesn't accept.

**Fix:** This is handled automatically since v0.4.0 — the DPoP generator reads the server's `WWW-Authenticate` response and negotiates the best supported algorithm. Upgrade to `0.4.0` or later if you see this on an older version.

______________________________________________________________________

## Resource Operations

### `412 Precondition Failed`

**Cause:** You passed an `ifMatch` ETag to `update()` or `patch()`, but the resource was modified by someone else since you last read it — a lost-update was correctly prevented.

**Fix:** Re-read the resource to get the latest ETag and version, merge your changes, and retry:

```kotlin
val latest = resourceManager.read(webId, uri, MyNote::class.java).getOrThrow()
val merged = mergeChanges(latest, myChanges)

// The ETag lives on the response metadata, not on the resource — HEAD for the current one.
val etag = resourceManager.head(webId, uri).getOrThrow().etag
resourceManager.update(webId, merged, ifMatch = etag)
```

______________________________________________________________________

### `409 Conflict` on `create()`

**Cause:** A resource already exists at the target URI. `create()` uses a conditional `PUT` with `If-None-Match: *`, which the server rejects if the URI is taken.

**Fix:** Use `update()` to overwrite, or choose a different URI. If you're generating URIs dynamically (e.g. using a UUID), the collision probability is negligible.

______________________________________________________________________

### `403 Forbidden` on resource access

**Cause:** The authenticated user does not have the required WAC/ACP permission on the requested resource.

**Fix:** Use `head()` to inspect the `WAC-Allow` header before attempting a write:

```kotlin
val meta = resourceManager.head(webId, uri).getOrNull()

// WacAllow carries two sets of mode names — what this user may do, and what anyone may do.
val mine = meta?.wacAllow?.userModes.orEmpty()      // e.g. ["read", "append"]
val anyones = meta?.wacAllow?.publicModes.orEmpty()

if ("write" !in mine) showReadOnly()
```

`probeAccess` wraps this in a typed result if you would rather not read header sets — see [Access control](https://androidsolidservices.erfangholami.com/0.8/build/access-control/index.md).

If access should be granted, check the ACL/ACP policy on the pod server side.

______________________________________________________________________

### Profile edits fail with 401 / 403 / 405 on an Inrupt WebID

**Cause:** `updateProfile()` and `setAvatar()` used to patch the WebID document, which Inrupt serves read-only from `id.inrupt.com`.

**Fix:** Upgrade to `0.8.0`+. `writableProfileDocument(webId)` picks the document to edit — the WebID document when its `WAC-Allow` grants write, otherwise the linked extended profile on the user's storage — and both operations write there. The account's `WebId` also folds the extended profile in at sign-in and on `reloadProfile()`, so a name that lives only in the extended profile is no longer blank.

______________________________________________________________________

### Resource URI with spaces returns 404 or 400

**Cause:** On versions before `0.4.0`, resource URIs were not percent-encoded, causing requests for URIs with spaces or special characters to fail.

**Fix:** Upgrade to `0.4.0` or later. URIs are now automatically percent-encoded. If you stored broken URIs in your app, re-derive them with `URI(rawUri).toASCIIString()`.

______________________________________________________________________

### Reads fail with a network error after the first read on Community Solid Server

**Symptom:** the first read of a resource works, every later read of the same resource fails with `SolidError.Network` (`Content-Length (N) and stream length (0) disagree`), and an app built on the library reports that the pod cannot be reached while the device is online. Seen on `solid.redpencil.io`.

**Cause:** Community Solid Server answers a conditional `GET` for a resource that it serves in its stored format with `304 Not Modified` plus the `Content-Length` of the full `200` body, and no body. RFC 9110 allows that, but OkHttp treats a `304` with a `Content-Length` as a body-carrying response and fails when the body is missing. The library re-reads cached resources conditionally, so the second read is the one that fails.

**Fix:** handled since the version after 0.7.1 — the transport reads no body on `304`, `204` and `HEAD` responses. On an older version, the only workaround is `SolidHttpClient.cacheEnabled = false`, which disables conditional reads at the cost of re-downloading every resource.

______________________________________________________________________

## Contacts Data Module

### Contact names appear empty after upgrade from an older version

**Cause:** Versions before `0.4.0` used the wrong Dublin Core (`dc:`) namespace for some contact fields. Old data used the incorrect IRI.

**Fix:** The `0.4.0` data module reads both the correct and legacy namespace, so existing data is still visible without migration.

______________________________________________________________________

### `getAddressBooks()` returns an empty list despite having address books on the pod

**Cause:** The type index on the pod may not have been updated when address books were created by another client.

**Fix:** Ensure the pod's type index is populated. Some Solid servers require the creating client to register resources in the type index. Check the pod's type index resource manually if needed.

______________________________________________________________________

## Sharing & Notifications

### A share is created but the receiver never gets a notification

**Cause:** Writing the share onto the resource's access control and notifying the receiver are separate steps — delivery is **best-effort** and never fails the share. The receiver may advertise no LDN inbox, or their inbox rejected the POST.

**Fix:** The receiver's WebID must advertise a writable, public-append `ldp:inbox`. Notifications are also **pull-only**: the receiving app polls `listNotifications()` (e.g. a 15-minute background worker) — there is no push. The share itself still took effect on the resource ACL regardless of delivery.

______________________________________________________________________

### An Inrupt (PodSpaces) account receives no notifications, and nobody can request access from it

**Cause:** Inrupt serves the WebID document from `id.inrupt.com` read-only, so `ensureInbox()` can only write the `ldp:inbox` link into the extended profile on the pod (`{storage}profile`) — and that document is private by default. A sender reads the public WebID document, finds no inbox, cannot read the extended profile, and reports `NoInbox`. The inbox itself exists and accepts posts; it is simply undiscoverable.

**Fix:** Upgrade to `0.8.0`+. `ensureInbox()` now grants public read on the document that advertises the inbox when that document is not the WebID document itself, and a sender that finds no inbox falls back to the conventional `{storage}inbox/` taken from the public `pim:storage`. The fallback works at once; the public-read repair needs the receiving account to run `ensureInbox()` once more (Solid Share does so on every account activation). Making the extended profile public exposes the fields it holds — name, photo, organisation — which is what a WebID profile is for, but tell your users.

______________________________________________________________________

### Sharing failures on `createShare` / `revokeShare`

These now arrive as `SolidResult.Failure` with a typed `SolidError`, rather than a thrown `SharingException`. Common cases:

- **`NoInbox`** — the target WebID advertises no `ldp:inbox`; the share succeeded but no notification was sent.
- **`InboxUnauthorized` / `InboxForbidden`** — the receiver's inbox rejected the notification POST (401 / 403).
- **`AccessDenied`** — you don't hold Control on the resource, so its ACL/ACR can't be written.
- **`StaleAcl`** — the resource's ACL changed concurrently (412 Precondition Failed); retry.
- **`UnsupportedAuthBackend`** — the pod's access-control system isn't supported.
- **`AccessIndeterminate`** — a transient or cross-pod proof error; treated as "unknown", not a definitive denial.

______________________________________________________________________

### A share added from a link shows the wrong owner, or won't add (cross-pod)

**Cause:** Cross-pod access probes can be blocked — some servers reject foreign-issuer tokens with 401 — and a receiver IRI may be a bare profile-document URL rather than a fragment WebID.

**Fix:** Upgrade to `0.5.0`+. The library reads actor profiles **anonymously**, resolves profile-document URLs to the real fragment WebID before granting, and trusts the notification's declared mode/owner when a live probe is indeterminate. When adding via QR/link, pass the owner hint the share link carries.

______________________________________________________________________

## Build & Gradle

### The build fails with "requires ... to compile against version 37 or later"

**Cause:** Your `compileSdk` is lower than the libraries'. Every artifact here is built against SDK 37 and its AAR metadata demands the same of anything that depends on it.

**Fix:** Set `compileSdk = 37`. This changes which APIs you may call, not which devices you reach: leave `minSdk` at 26 and set `targetSdk` to whatever you already target.

build.gradle.kts

```kotlin
android {
    compileSdk = 37
}
```

______________________________________________________________________

### KSP fails with "Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0"

**Cause:** Your annotation processor reads Kotlin metadata with a `kotlin-metadata-jvm` older than the Kotlin that built these libraries. Hilt below 2.60 does this, and the message names Hilt.

**Fix:** Update the processor. For Hilt, use 2.60.1 or newer, and `androidx.hilt` 1.4.0 or newer. The same applies to any other KSP or kapt processor that reads metadata.

______________________________________________________________________

### `NullPointerException` during Gradle configuration for `api`

**Cause:** The `key_generator_alias` property is missing from `gradle.properties`.

**Fix:** Add it to your project's `gradle.properties`:

```properties
key_generator_alias="AndroidSolidServicesApiKeyGenAlias"
```

______________________________________________________________________

### `DuplicateClassException` at runtime involving `okhttp` or `kotlin-stdlib`

**Cause:** Multiple versions of OkHttp or the Kotlin stdlib are on the classpath.

**Fix:** Add an explicit resolution strategy to your root `build.gradle.kts`:

```kotlin
configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:5.3.2")
        force("org.jetbrains.kotlin:kotlin-stdlib:2.x.x")
    }
}
```

Run `./gradlew dependencies` to inspect the full dependency tree.

______________________________________________________________________

Still stuck?

[Open an issue](https://github.com/erfangholami/Android-Solid-Services/issues) with your Android version, library version (`0.x.x`), the full stack trace, and steps to reproduce. The more detail you include, the faster we can help.
