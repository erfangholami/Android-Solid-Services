# Android Solid Services API Library

The **Android Solid Services API** library lets your app communicate with a Solid pod server directly — no Android Solid Services host app required. It handles OpenID Connect authentication (using DPoP when the provider supports it, otherwise Bearer tokens) and exposes interfaces for resource management and the Contacts data module.

**Use this when:**

- You want to embed full Solid support inside your own app
- The Android Solid Services host app is unavailable or not applicable
- You need fine-grained control over auth flows and token management

**Otherwise, prefer the [Android Solid Services Client](client-library.md)** — it offloads auth and IPC to the host app.

## Installation

```kotlin
// build.gradle.kts (module level)
android {
    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "YOUR_APP_PACKAGE_NAME"
    }
}
dependencies {
    implementation("com.erfangholami.androidsolidservices:api:0.6.0")
}
```

[View on Maven Central](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api)

---

## Authenticator

Manages OpenID Connect sessions — DPoP (Demonstration of Proof-of-Possession) when the provider supports it, Bearer tokens otherwise — including multi-account handling.

```kotlin
val authenticator = Authenticator.getInstance(context)
```

### State flows

| Property               | Type                       | Description                           |
|------------------------|----------------------------|---------------------------------------|
| `activeProfileFlow`    | `StateFlow<Profile?>`      | The currently active user profile     |
| `loggedInProfilesFlow` | `StateFlow<List<Profile>>` | All signed-in profiles                |
| `isAuthorizedFlow`     | `StateFlow<Boolean>`       | Whether the active user is authorized |
| `activeWebIdFlow`      | `StateFlow<String?>`       | The active user's WebID               |

### Authentication flow

```kotlin
// 1. Build the intent that opens the browser for login
val (intent, error) = authenticator.createAuthenticationIntent(
    webId = "https://yourpod.example/profile/card#me",  // optional
    oidcIssuer = null,         // optional; derived from webId if omitted
    appName = "My App",
    redirectUri = "myapp://callback",
    clientId = null,           // optional; supply a hosted Client ID Document URL (see below)
)
launcher.launch(intent)        // an ActivityResultLauncher for the redirect Activity

// 2. Handle the redirect result — pass the result Intent straight through
val webId = authenticator.submitAuthorizationResponse(result.data)
// webId is the authorized WebID on success, or null if denied/cancelled
```

### All methods

```kotlin
// Create intent for browser-based login.
// Pass clientId — a hosted Solid-OIDC Client ID Document URL — to use a stable client identity
// instead of dynamic registration; appName is ignored when clientId is set.  (clientId since 0.5.0)
suspend fun createAuthenticationIntent(
    webId: String? = null,
    oidcIssuer: String? = null,
    appName: String,
    redirectUri: String,
    clientId: String? = null,
): Pair<Intent?, String?>

// Complete login: pass the result Intent delivered to your redirect Activity.
// Returns the authorized WebID, or null if denied/cancelled.  (signature changed in 0.5.0)
suspend fun submitAuthorizationResponse(responseData: Intent?): String?

// Build logout intent for browser-based session termination
suspend fun getTerminationSessionIntent(
    webId: String,
    logoutRedirectUrl: String,
): Pair<Intent?, String?>

// Re-fetch a signed-in user's WebID document and refresh the cached profile (e.g. after an
// in-app profile edit). Leaves auth state untouched.  (since 0.5.0)
suspend fun reloadProfile(webId: String): Profile

// Synchronous profile accessors
fun isUserAuthorized(): Boolean
fun getAllLoggedInProfiles(): List<Profile>
fun getProfile(webId: String): Profile
fun getActiveProfile(): Profile

// Account management (suspend)
suspend fun getActiveWebId(): String?
suspend fun setActiveWebId(webId: String)
suspend fun removeProfile(webId: String)
suspend fun removeAllProfiles()
```

!!! info "Tokens are managed for you (0.5.0)"
    The transport-level methods `getLastTokenResponse`, `getAuthHeaders`, and `updateDPoPNonce` were
    **removed** from the public `Authenticator` and moved to an internal session seam. Access tokens,
    `Authorization`/`DPoP` headers, and DPoP nonces are handled inside the library — go through
    `SolidResourceManager`, `SharingManager`, and the other managers. The persisted token store is
    **encrypted at rest** (AES-256-GCM via an Android Keystore key), and each account uses its own
    DPoP keypair.

!!! tip "Stable client identity — Client ID Document"
    Supplying `clientId` lets the app authenticate with a stable, hosted `client_id` instead of
    per-device dynamic registration (which can expire and force a re-login). See
    [Using a Solid-OIDC Client ID Document](client-id-document/README.md).

---

## SolidResourceManager

Performs authenticated CRUD operations on Solid pod resources. All methods are `suspend` functions and return `SolidResult<T>`.

```kotlin
val resourceManager = SolidResourceManager.getInstance(context)
```

### SolidResult

Every API returns `SolidResult<T>` — nothing throws.

```kotlin
sealed class SolidResult<out T> {
    data class Success<out T>(val value: T)
    data class Failure(val error: SolidError)
}
```

`SolidError` is sealed, with a machine-readable `code: SolidErrorCode`, a `message`, an optional
`httpStatus`, a `retryable` flag, and an optional `cause`. Variants include `Unauthorized`,
`Forbidden`, `NotFound`, `Conflict`, `PreconditionFailed` and `RateLimited`, so you can branch on
the case rather than parse a status code.

Convenience members: `isSuccess` / `isFailure`, `getOrNull()`, `errorOrNull()`, `getOrThrow()`,
`getOrDefault(default)`, `getOrElse { }`, `map { }`, `flatMap { }`, `fold(onSuccess, onFailure)`,
`recover { }`, `onSuccess { }`, `onFailure { }`.

### Methods

```kotlin
// Fetch HTTP headers only (no body) — returns SolidMetadata with ETag, Content-Type,
// WAC-Allow, ACL link, Accept-Patch/Post, Last-Modified, and other Solid headers.
// Ideal for caching checks and permission discovery before a full read.
suspend fun head(
    webId: String,
    uri: String,
): SolidResult<SolidMetadata>

// Read a resource from the pod
suspend fun <T : Resource> read(
    webId: String,    // WebID of the authenticated user
    resource: String,    // full URI of the resource
    clazz: Class<T>,  // expected type (must extend RDFSource or NonRDFSource)
): SolidResult<T>

// Create a new resource on the pod (conditional PUT — fails if the URI already exists)
suspend fun <T : Resource> create(
    webId: String,
    resource: T,      // identifier on the resource determines the target URI
): SolidResult<T>

// Replace an existing resource. Pass ifMatch (an ETag from head/read) for
// optimistic-concurrency protection — the server returns 412 on version mismatch.
suspend fun <T : Resource> update(
    webId: String,
    newResource: T,
    ifMatch: String? = null,
): SolidResult<T>

// Apply an N3 Patch to an RDF resource — atomic partial update, no full read required.
// Use N3Patch.build { ... } or N3Patch.fromDiff(original, updated) to construct the patch.
suspend fun patch(
    webId: String,
    uri: String,
    patch: N3Patch,
    ifMatch: String? = null,
): SolidResult<Unit>

// Apply a pre-serialised text/n3 patch body (e.g. when the patch crossed an IPC boundary).
suspend fun patchRaw(
    webId: String,
    uri: String,
    n3Body: String,
    ifMatch: String? = null,
): SolidResult<Unit>

// Delete a resource by resource object (containers are deleted recursively)
suspend fun <T : Resource> delete(
    webId: String,
    resource: T,
): SolidResult<T>

// Delete a resource by URI directly — no prior read needed.
// URIs ending with '/' are treated as containers and deleted recursively.
suspend fun delete(
    webId: String,
    resourceUri: String,
): SolidResult<Boolean>

// Create a resource inside a container via POST (the server assigns the name, returned as the URI).
// Lets an add-only recipient (Append access) upload into a shared container without Write.  (0.5.0)
suspend fun <T : Resource> createInContainer(
    webId: String,
    containerUri: String,
    resource: T,
): SolidResult<String?>

// --- Helpers (0.6.0) ---

// Does the resource exist? (HEAD, no body)
suspend fun exists(webId: String, uri: String): SolidResult<Boolean>

// Create the container if it is missing; succeeds if it already exists.
suspend fun ensureContainer(webId: String, containerUri: String): SolidResult<Unit>

// What can this account do with the resource? Reads WAC-Allow / ACR without writing anything.
suspend fun probeAccess(webId: String, uri: String): SolidResult<AccessProbe>

// --- Streaming (0.6.0) ---

// Read without buffering the whole body in memory — for large files.
suspend fun readStream(webId: String, uri: String): SolidResult<StreamingResource>

// Write from a stream, with progress callbacks.
suspend fun writeStream(
    webId: String,
    uri: String,
    /* ... */
): SolidResult<Unit>
```

!!! tip "Full API surface"
    This page is a guide to the main entry points. The complete, generated reference for every
    public declaration in `api`, `client` and `shared` lives in the
    [API Reference](api/index.html).

!!! note "Response cache (0.5.0)"
    The underlying `SolidHttpClient` now keeps an in-memory response cache — per-account keyed, with
    TTL freshness, ETag/`Last-Modified` revalidation, single-flight de-duplication of concurrent
    identical reads, and write-through invalidation. It is on by default and transparent to callers.

### N3Patch

Type-safe DSL and diff factory for building [Solid N3 Patch](https://solidproject.org/TR/protocol#n3-patch) documents:

```kotlin
// DSL builder — no raw N3 strings needed
val patch = N3Patch.build {
    // bind the old value, then swap it atomically
    where(contactUri, VCARD.FN, variable = "oldName")
    deleteVar(contactUri, VCARD.FN, variable = "oldName")
    insertLiteral(contactUri, VCARD.FN, "Alice")
}

// Diff factory — derive the patch automatically from two resource states
val patch = N3Patch.fromDiff(originalResource, modifiedResource)

// Convenience factories
val patch = N3Patch.insert("<$s> <$p> <$o> .")
val patch = N3Patch.delete("<$s> <$p> <$o> .")
val patch = N3Patch.replace(inserts = "...", where = "...")
```

### Resource types

Your data classes must extend one of:

| Class            | Use for                                     |
|------------------|---------------------------------------------|
| `RDFSource`      | Structured RDF data (Turtle, JSON-LD, etc.) |
| `NonRDFSource`   | Raw files — images, text, binary            |
| `SolidContainer` | LDP containers (directories on the pod)     |

### Example

```kotlin
val result = resourceManager.read(
    webId = "https://yourpod.example/profile/card#me",
    resource = "https://yourpod.example/data/note.ttl",
    clazz = MyNote::class.java
)

when (result) {
    is SolidResult.Success -> display(result.value)
    is SolidResult.Failure -> showError(result.error.code, result.error.message)
}

// or, without branching
result.fold(
    onSuccess = ::display,
    onFailure = { error -> if (error.retryable) retry() else showError(error.code, error.message) },
)
```

---

## SolidContactsDataModule

Manages address books, contacts, and groups on a Solid pod using the [Solid Contacts spec](https://www.w3.org/TR/vcard-rdf/). All methods are `suspend` and return `SolidResult<T>`.

```kotlin
val contactsModule = SolidContactsDataModule.getInstance(context)
```

### Address Books

```kotlin
suspend fun getAddressBooks(ownerWebId: String): SolidResult<AddressBookList>

suspend fun createAddressBook(
    ownerWebId: String,
    title: String,
    isPrivate: Boolean = true,
    storage: String,
    container: String? = null,
): SolidResult<AddressBook>

suspend fun getAddressBook(ownerWebId: String, addressBookUri: String): SolidResult<AddressBook>

suspend fun renameAddressBook(
    ownerWebId: String,
    addressBookUri: String,
    newName: String,
): SolidResult<AddressBook>

suspend fun deleteAddressBook(ownerWebId: String, addressBookUri: String): SolidResult<AddressBook>
```

### Contacts

```kotlin
suspend fun createNewContact(
    ownerWebId: String,
    addressBookString: String,
    newContact: NewContact,
    groupStrings: List<String> = emptyList(),
): SolidResult<FullContact>

suspend fun getContact(ownerWebId: String, contactString: String): SolidResult<FullContact>

suspend fun renameContact(ownerWebId: String, contactString: String, newName: String): SolidResult<FullContact>

suspend fun addNewPhoneNumber(ownerWebId: String, contactString: String, newPhoneNumber: String): SolidResult<FullContact>

suspend fun addNewEmailAddress(ownerWebId: String, contactString: String, newEmailAddress: String): SolidResult<FullContact>

suspend fun removePhoneNumber(ownerWebId: String, contactString: String, phoneNumber: String): SolidResult<FullContact>

suspend fun removeEmailAddress(ownerWebId: String, contactString: String, emailAddress: String): SolidResult<FullContact>

suspend fun deleteContact(ownerWebId: String, addressBookUri: String, contactUri: String): SolidResult<FullContact>
```

### Groups

```kotlin
suspend fun createNewGroup(
    ownerWebId: String,
    addressBookString: String,
    title: String,
    contactUris: List<String> = emptyList(),
): SolidResult<FullGroup>

suspend fun getGroup(ownerWebId: String, groupString: String): SolidResult<FullGroup>

suspend fun deleteGroup(ownerWebId: String, addressBookString: String, groupString: String): SolidResult<FullGroup>

suspend fun addContactToGroup(ownerWebId: String, contactString: String, groupString: String): SolidResult<FullGroup>

suspend fun removeContactFromGroup(ownerWebId: String, contactString: String, groupString: String): SolidResult<FullGroup>
```

---

## SharingManager

!!! note "New in 0.5.0"

Creates, lists, and revokes shares of pod resources. Built on Web Access Control (WAC); pods that use
Access Control Policy (ACP) are supported through a pluggable access-control backend selected from the
resource's advertised authorization links. A private index pair under `{podRoot}/solidshare/shares/`
lets the user see what they have shared and received without re-walking the pod. All methods are
`suspend` and return `SolidResult<T>`.

```kotlin
val sharingManager = SharingManager.getInstance(authenticator)
// or seed it with an existing resource manager, and/or a custom profile:
// SharingManager.getInstance(resourceManager, profile = SolidShareProfile)
```

The `ShareMode` (`READ` / `APPEND` / `WRITE`, surfaced as View / Add / Edit) and `ShareReceiver`
(`WebIdReceiver` / `GroupReceiver` / `Public`) types are described on the
[Client library page](client-library.md#share-mode-and-receiver).

### Methods

```kotlin
// --- Shares you give ---
suspend fun createShare(webId: String, resourceUri: String, mode: ShareMode, receiver: ShareReceiver, notifyReceiver: Boolean = true): SolidResult<GivenShare>
suspend fun updateShare(webId: String, resourceUri: String, mode: ShareMode, receiver: ShareReceiver, notifyReceiver: Boolean = false): SolidResult<GivenShare>
suspend fun revokeShare(webId: String, resourceUri: String, receiver: ShareReceiver): SolidResult<Unit>

suspend fun getStoredGivenShares(webId: String): SolidResult<List<GivenShare>>
suspend fun refreshGivenShares(webId: String): SolidResult<List<GivenShare>>
suspend fun getGivenSharesForResource(webId: String, resourceUri: String): SolidResult<List<GivenShare>>
suspend fun rebuildGivenIndex(webId: String): SolidResult<List<GivenShare>>   // expensive: walks the pod's ACLs

// Reset a resource to owner-only (make it private); re-assert owner Read/Write/Control after a lockout
suspend fun makePrivate(webId: String, resourceUri: String): SolidResult<Unit>
suspend fun repairOwnerControl(webId: String, resourceUri: String): SolidResult<Unit>

// --- Shares you receive ---
suspend fun getStoredReceivedShares(webId: String): SolidResult<List<ReceivedShare>>
suspend fun refreshReceivedShares(webId: String): SolidResult<List<ReceivedShare>>
suspend fun addReceivedShare(webId: String, resourceUri: String, ownerHint: String? = null): SolidResult<ReceivedShare?>
suspend fun removeReceivedShare(webId: String, resourceUri: String, ownerWebId: String): SolidResult<Unit>
// Reconcile received shares against a batch of notifications already read from the inbox
suspend fun syncReceivedShares(webId: String, notifications: List<ShareNotification>): SolidResult<List<ReceivedShare>>

// --- Access grants, requests, catalog ---
suspend fun getAccessGrants(webId: String): SolidResult<List<AccessGrant>>     // given + received + requests + SAI grants
suspend fun acceptShareRequest(webId: String, request: ShareRequest): SolidResult<GivenShare>
suspend fun rejectShareRequest(webId: String, request: ShareRequest, reason: String? = null): SolidResult<Unit>
suspend fun publishCatalogEntry(webId: String, entry: CatalogEntry): SolidResult<Unit>
suspend fun removeCatalogEntry(webId: String, resourceUri: String): SolidResult<Unit>
suspend fun getOwnerCatalog(viewerWebId: String, ownerWebId: String): SolidResult<List<CatalogEntry>>

// --- Share links ---
fun getShareDeepLink(resourceUri: String, ownerWebId: String? = null): String   // https://solidshare.app/s?resource=…&owner=… App Link
fun parseShareDeepLink(deepLink: String): ParsedShareLink?
fun getShareBareUrl(resourceUri: String): String                                // plain https URL for non-Solid scanners
```

---

## NotificationsManager

!!! note "New in 0.5.0"

The [Linked Data Notifications](https://www.w3.org/TR/ldn/) (LDN) inbox layer behind sharing. Lists
incoming offers and access requests, sends the matching outgoing notifications, and provisions the
user's inbox. **Pull-only** — there is no push subscription; refresh on demand or from a periodic
worker. `SharingManager` calls into this internally on `createShare` / `updateShare` / `revokeShare`,
so most apps only need the read and request methods directly. All methods return
`SolidResult<T>`.

```kotlin
val notifications = NotificationsManager.getInstance(authenticator)
// or NotificationsManager.getInstance(resourceManager, profile = SolidShareNotificationProfile)
```

```kotlin
// Read the inbox
suspend fun listNotifications(webId: String): SolidResult<List<ShareNotification>>  // syncs received_shares.ttl as a side effect
suspend fun listRequests(webId: String): SolidResult<List<ShareRequest>>

// Provision / maintain the inbox
suspend fun ensureInbox(webId: String): SolidResult<String>          // public append-but-not-read; idempotent
suspend fun compactInbox(webId: String, olderThanIso: String? = null): SolidResult<Int>
suspend fun deleteNotification(webId: String, notificationUri: String): SolidResult<Boolean>

// Outgoing notifications (offer / update / withdraw — used internally by SharingManager)
suspend fun sendOffer(ownerWebId: String, receiverWebId: String, resourceUri: String, mode: ShareMode): SolidResult<Unit>
suspend fun sendUpdate(ownerWebId: String, receiverWebId: String, resourceUri: String, mode: ShareMode): SolidResult<Unit>
suspend fun sendUndo(ownerWebId: String, receiverWebId: String, resourceUri: String): SolidResult<Unit>

// Request-to-share flow
suspend fun sendRequest(requesterWebId: String, ownerWebId: String, resourceUri: String, requestedMode: ShareMode, summary: String? = null): SolidResult<Unit>
suspend fun sendAccept(ownerWebId: String, requesterWebId: String, resourceUri: String, mode: ShareMode, requestUri: String? = null): SolidResult<Unit>
suspend fun sendReject(ownerWebId: String, requesterWebId: String, resourceUri: String, reason: String? = null): SolidResult<Unit>
```
