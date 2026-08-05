# Troubleshooting

Common errors and how to fix them. If your issue isn't listed here, [open an issue on GitHub](https://github.com/erfangholami/Android-Solid-Services/issues) — include your Android version, library version, and the full exception message.

---

## Installation & Connection

### `SolidAppNotFoundException`

**Cause:** The Android Solid Services host app is not installed on the device.

**Fix:** Have the user install ASS from the [GitHub Releases page](https://github.com/erfangholami/Android-Solid-Services/releases) before your app makes any IPC call.

```kotlin
try {
    signInClient.getAccount(webId)
} catch (e: SolidAppNotFoundException) {
    // redirect user to the ASS install page
}
```

---

### `SolidServiceConnectionException`

**Cause:** The IPC service bound successfully at the OS level but then disconnected unexpectedly, or you called a method before the `Flow<Boolean>` connection state emitted `true`.

**Fix:** Always gate calls behind the connection state flow:

```kotlin
resourceClient.resourceServiceConnectionState().collect { connected ->
    if (connected) {
        // safe to call resource methods here
    }
}
```

Do not call methods immediately after obtaining the client object — binding is asynchronous.

---

### `SolidServicesDrawPermissionDeniedException`

**Cause:** You called the deprecated `SolidSignInClient.requestLogin`. It drew the account picker over your app from a background service, which Android permits only with the `SYSTEM_ALERT_WINDOW` (overlay draw) permission. ASS no longer requests that permission, so the call now always fails with this exception instead of leaving you waiting for a callback that cannot arrive.

**Fix:** Launch the `AuthorizeWithSolid` contract from your Activity — the picker opens in your own foreground, the chosen WebID comes back as an activity result, and no permission is involved. See [Getting Started](../start/quickstart.md).

---

## Authentication

### Login browser opens but redirect never returns to ASS

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

---

### `SolidNotLoggedInException`

**Cause:** No user is logged in to ASS, or the stored session has been fully invalidated (refresh token expired or revoked by the pod server).

**Fix:** In your app, check `signInClient.getAccount(webId)` — if it returns `null`, launch the `AuthorizeWithSolid` contract again to start a new auth flow.

---

### Token refresh fails silently / requests return 401

**Cause:** The authorization server requires a `DPoP-Nonce` on the **token endpoint** (e.g. Inrupt ESS) and an older client couldn't complete the nonce challenge during refresh — so the access token expired and the user was forced to sign in again. Or the refresh token was revoked.

**Fix:** Upgrade to `0.5.0` or later. Nonce handling and token refresh are now fully internal and nonce-aware: the library reads the `DPoP-Nonce`, retries the token request once on a `use_dpop_nonce` challenge ([RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449) §8), tracks nonces **per origin**, and no longer invalidates the session on a *recoverable* failure (nonce/network/5xx).

You no longer call `updateDPoPNonce` or `getLastTokenResponse` — both were **removed** from the public `Authenticator` in 0.5.0. Access tokens and DPoP headers are attached for you; go through the resource / sharing / contacts managers (or the `client` SDK). If the refresh token is genuinely revoked (the session reports unauthorized), re-authenticate with `requestLogin()` / `createAuthenticationIntent()`.

---

### "unsupported_algorithm" error during login

**Cause:** The pod server only supports certain DPoP signing algorithms and the client proposed one it doesn't accept.

**Fix:** This is handled automatically since v0.4.0 — the DPoP generator reads the server's `WWW-Authenticate` response and negotiates the best supported algorithm. Upgrade to `0.4.0` or later if you see this on an older version.

---

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

---

### `409 Conflict` on `create()`

**Cause:** A resource already exists at the target URI. `create()` uses a conditional `PUT` with `If-None-Match: *`, which the server rejects if the URI is taken.

**Fix:** Use `update()` to overwrite, or choose a different URI. If you're generating URIs dynamically (e.g. using a UUID), the collision probability is negligible.

---

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

`probeAccess` wraps this in a typed result if you would rather not read header sets — see
[Access control](../build/access-control.md).

If access should be granted, check the ACL/ACP policy on the pod server side.

---

### Resource URI with spaces returns 404 or 400

**Cause:** On versions before `0.4.0`, resource URIs were not percent-encoded, causing requests for URIs with spaces or special characters to fail.

**Fix:** Upgrade to `0.4.0` or later. URIs are now automatically percent-encoded. If you stored broken URIs in your app, re-derive them with `URI(rawUri).toASCIIString()`.

---

## Contacts Data Module

### Contact names appear empty after upgrade from an older version

**Cause:** Versions before `0.4.0` used the wrong Dublin Core (`dc:`) namespace for some contact fields. Old data used the incorrect IRI.

**Fix:** The `0.4.0` data module reads both the correct and legacy namespace, so existing data is still visible without migration.

---

### `getAddressBooks()` returns an empty list despite having address books on the pod

**Cause:** The type index on the pod may not have been updated when address books were created by another client.

**Fix:** Ensure the pod's type index is populated. Some Solid servers require the creating client to register resources in the type index. Check the pod's type index resource manually if needed.

---

## Sharing & Notifications

### A share is created but the receiver never gets a notification

**Cause:** Writing the share onto the resource's access control and notifying the receiver are separate steps — delivery is **best-effort** and never fails the share. The receiver may advertise no LDN inbox, or their inbox rejected the POST.

**Fix:** The receiver's WebID must advertise a writable, public-append `ldp:inbox`. Notifications are also **pull-only**: the receiving app polls `listNotifications()` (e.g. a 15-minute background worker) — there is no push. The share itself still took effect on the resource ACL regardless of delivery.

---

### Sharing failures on `createShare` / `revokeShare`

These now arrive as `SolidResult.Failure` with a typed `SolidError`, rather than a thrown
`SharingException`. Common cases:

- **`NoInbox`** — the target WebID advertises no `ldp:inbox`; the share succeeded but no notification was sent.
- **`InboxUnauthorized` / `InboxForbidden`** — the receiver's inbox rejected the notification POST (401 / 403).
- **`AccessDenied`** — you don't hold Control on the resource, so its ACL/ACR can't be written.
- **`StaleAcl`** — the resource's ACL changed concurrently (412 Precondition Failed); retry.
- **`UnsupportedAuthBackend`** — the pod's access-control system isn't supported.
- **`AccessIndeterminate`** — a transient or cross-pod proof error; treated as "unknown", not a definitive denial.

---

### A share added from a link shows the wrong owner, or won't add (cross-pod)

**Cause:** Cross-pod access probes can be blocked — some servers reject foreign-issuer tokens with 401 — and a receiver IRI may be a bare profile-document URL rather than a fragment WebID.

**Fix:** Upgrade to `0.5.0`+. The library reads actor profiles **anonymously**, resolves profile-document URLs to the real fragment WebID before granting, and trusts the notification's declared mode/owner when a live probe is indeterminate. When adding via QR/link, pass the owner hint the share link carries.

---

## Build & Gradle

### `NullPointerException` during Gradle configuration for `api`

**Cause:** The `key_generator_alias` property is missing from `gradle.properties`.

**Fix:** Add it to your project's `gradle.properties`:

```properties
key_generator_alias="AndroidSolidServicesApiKeyGenAlias"
```

---

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

---

!!! question "Still stuck?"
    [Open an issue](https://github.com/erfangholami/Android-Solid-Services/issues) with your Android version, library version (`0.x.x`), the full stack trace, and steps to reproduce. The more detail you include, the faster we can help.
