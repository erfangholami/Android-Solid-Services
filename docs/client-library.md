# Android Solid Services Client Library

The **Android Solid Services Client** library lets third-party Android apps plug into the Solid ecosystem by talking to the Android Solid Services host app over IPC. Your app never handles tokens or pod credentials — ASS does that for you.

**Use this when:**

- You're building an app that works alongside the Android Solid Services host app
- You want the user's Solid session managed centrally (single sign-in)

**Otherwise, see the [Android Solid Services API](api-library.md)** if you need direct Solid server access.

## Installation

```kotlin
// build.gradle.kts (module level)
android {
    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "YOUR_APP_PACKAGE_NAME"
    }
}
dependencies {
    implementation("com.erfangholami.androidsolidservices:client:0.6.0")
}
```

[View on Maven Central](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client)

---

## Entry Point: `Solid`

All clients are obtained from the `Solid` companion object. Each is a process-scoped singleton.

```kotlin
val signInClient        = Solid.getSignInClient(context)
val resourceClient      = Solid.getResourceClient(context)
val contactsModule      = Solid.getContactsDataModule(context)
val sharingClient       = Solid.getSharingClient(context)        // since 0.5.0
val notificationsClient = Solid.getNotificationsClient(context)  // since 0.5.0
```

---

## SolidSignInClient

Manages authorization between your app and ASS. Obtain via `Solid.getSignInClient(context)`.

### Connection state

All IPC services expose a `Flow<Boolean>` that emits `true` once the bound service connects and `false` if it drops. Always wait for `true` before calling any methods.

```kotlin
signInClient.authServiceConnectionState().collect { connected ->
    if (connected) {
        // safe to call sign-in methods
    }
}
```

### Methods

```kotlin
// Emits connection state of the auth IPC service
fun authServiceConnectionState(): Flow<Boolean>

// Returns a SolidSignInAccount if this app is already authorized, null if not.
// Throws SolidException if ASS is not installed, not connected, or no user is logged in.
fun getAccount(): SolidSignInAccount?

// Deprecated and no longer functional — always fails. Launch the AuthorizeWithSolid
// contract from your Activity instead (see "Full auth example" below).
fun requestLogin(callBack: (String?, SolidException?) -> Unit)

// Revokes this app's access grant.
fun disconnectFromSolid(callBack: (Boolean) -> Unit)
```

### Full auth example

Sign-in is an activity result: your app launches the picker, so no permission is involved.

```kotlin
class MainActivity : ComponentActivity() {

    private val signInClient = Solid.getSignInClient(this)

    // Register the contract while the activity is being created, not later.
    private val authorize = registerForActivityResult(AuthorizeWithSolid()) { result ->
        when (result) {
            is SolidSignInResult.Authorized -> proceedToApp(result.webId)
            SolidSignInResult.Dismissed     -> showDeniedMessage()
            is SolidSignInResult.Failed     -> showError(result.exception)
        }
    }

    private suspend fun signIn() {
        signInClient.authServiceConnectionState().collect { connected ->
            if (!connected) return@collect
            val account = signInClient.getAccount()
            if (account == null) authorize.launch(Unit) else proceedToApp(account.webId)
        }
    }
}
```

---

## SolidResourceClient

Reads, creates, updates, and deletes resources on the user's Solid pod via IPC. Obtain via `Solid.getResourceClient(context)`.

All methods are `suspend` functions. They throw a subclass of `SolidResourceException` on failure.

!!! note "Multi-account"
    Since v0.4.0, methods that operate on pod resources pass through the WebID of the target
    account. Save the WebID from `getAccount()` after login and pass it to each call.

```kotlin
// Emits connection state of the resource IPC service
fun resourceServiceConnectionState(): Flow<Boolean>

// Fetch a user's WebID document
suspend fun getWebId(webId: String): WebId

// Fetch HTTP headers only (no body) — returns SolidMetadata with ETag, Content-Type,
// Content-Length, WAC-Allow, Link relations (acl, describedby, type, storageDescription),
// Accept-Patch/Post/Put, and Last-Modified — with no body transfer.
suspend fun head(webId: String, resourceUrl: String): SolidMetadata

// Read a resource. clazz must extend RDFResource or NonRDFResource.
suspend fun <T : SolidResource> read(webId: String, resourceUrl: String, clazz: Class<T>): T

// Create a new resource on the pod
suspend fun <T : SolidResource> create(webId: String, resource: T): T

// Replace an existing resource. Pass ifMatch (an ETag from a prior read/head) for a
// conditional PUT that fails with 412 on concurrent modification.  (ifMatch since 0.5.0)
suspend fun <T : SolidResource> update(webId: String, resource: T, ifMatch: String? = null): T

// Apply an N3 Patch to an RDF resource — atomic partial update, no full read needed.
// Preferred over update() when only a subset of triples changes.  (since 0.5.0)
suspend fun patch(webId: String, uri: String, patch: N3Patch)

// Delete a resource
suspend fun <T : SolidResource> delete(webId: String, resource: T): T

// Read an LDP container; each contained resource is enriched with its own HEAD metadata
suspend fun readContainer(webId: String, containerUrl: String): SolidContainer

// Recursively delete a container and all of its contents (containerUri must end with '/')
suspend fun deleteContainer(webId: String, containerUri: String)
```

!!! tip "N3 Patch & ETags over IPC (0.5.0)"
    `head()`, `patch()`, and the `ifMatch` parameter on `update()` were added to the in-process
    `api` in 0.4.0; since 0.5.0 they are reachable over IPC too, so client apps get HEAD metadata,
    atomic partial RDF updates, and optimistic-concurrency writes without the host app.

### Resource types

| Base class     | Use for                                     |
|----------------|---------------------------------------------|
| `RDFSource`    | Structured RDF data (Turtle, JSON-LD, etc.) |
| `NonRDFSource` | Raw files — images, text, binary            |

### Example

```kotlin
val resourceClient = Solid.getResourceClient(context)
val webId = signInClient.getAccount()?.webId ?: return  // persisted after login

resourceClient.resourceServiceConnectionState().collect { connected ->
    if (connected) {
        try {
            val note = resourceClient.read(
                webId,
                "https://yourpod.example/notes/hello.ttl",
                MyNote::class.java
            )
            val updated = resourceClient.update(webId, note.copy(body = "updated text"))
        } catch (e: SolidException) {
            handleException(e)
        }
    }
}
```

---

## SolidContactsDataModule

Manages address books, contacts, and groups on the pod via IPC. Obtain via `Solid.getContactsDataModule(context)`.

All methods are `suspend` functions.

```kotlin
// Emits connection state of the contacts IPC service
fun contactsDataModuleServiceConnectionState(): Flow<Boolean>
```

### Address Books

```kotlin
suspend fun getAddressBooks(): AddressBookList?

suspend fun createAddressBook(
    title: String,
    isPrivate: Boolean = true,
    storage: String? = null,
    ownerWebId: String? = null,
    container: String? = null,
): AddressBook?

suspend fun getAddressBook(uri: String): AddressBook?

suspend fun deleteAddressBook(addressBookUri: String): AddressBook?
```

### Contacts

```kotlin
suspend fun createNewContact(
    addressBookUri: String,
    newContact: NewContact,
    groupUris: List<String> = emptyList(),
): FullContact?

suspend fun getContact(contactUri: String): FullContact?

suspend fun renameContact(contactUri: String, newName: String): FullContact?

suspend fun addNewPhoneNumber(contactUri: String, newPhoneNumber: String): FullContact?

suspend fun addNewEmailAddress(contactUri: String, newEmailAddress: String): FullContact?

suspend fun removePhoneNumber(contactUri: String, phoneNumber: String): FullContact?

suspend fun removeEmailAddress(contactUri: String, emailAddress: String): FullContact?

suspend fun deleteContact(addressBookUri: String, contactUri: String): FullContact?
```

### Groups

```kotlin
suspend fun createNewGroup(
    addressBookUri: String,
    title: String,
    contactUris: List<String> = emptyList(),
): FullGroup?

suspend fun getGroup(groupUri: String): FullGroup?

suspend fun deleteGroup(addressBookUri: String, groupUri: String): FullGroup?

suspend fun addContactToGroup(contactUri: String, groupUri: String): FullGroup?

suspend fun removeContactFromGroup(contactUri: String, groupUri: String): FullGroup?
```

---

## SolidSharingClient

!!! note "New in 0.5.0"

Creates, lists, and revokes shares of pod resources; tracks shares received from others; browses an
owner's catalog; and accepts or rejects access requests. Sharing is enforced by the pod's access
control — Web Access Control (WAC), or Access Control Policy (ACP) on servers that use it. Obtain via
`Solid.getSharingClient(context)`.

All methods are `suspend` functions and throw a subclass of `SolidException` on failure. Collect
`connectionState()` and wait for `true` before issuing calls.

### Share mode and receiver

```kotlin
// Access level granted to a receiver
enum class ShareMode { READ, APPEND, WRITE }  // surfaced as View / Add / Edit

// Who the share is for
sealed class ShareReceiver {
    data class WebIdReceiver(val webId: String)     // a single Solid user
    data class GroupReceiver(val groupUri: String)  // a vcard:Group; members inherit access
    data object Public                              // any agent (resource reachable by URL)
}
```

A grant writes the full set of implied WAC modes — **Add** = Read + Append, **Edit** = Read + Write —
and the index collapses them back to one logical mode per receiver.

### Methods

```kotlin
// Connection state of the sharing IPC service
fun connectionState(): Flow<Boolean>

// --- Shares you give ---

// Grant `receiver` access of `mode` on `resourceUri` (a container shares to its members too).
// When notifyReceiver is true and receiver is a WebID, an offer is delivered to their inbox.
suspend fun createShare(
    webId: String,
    resourceUri: String,
    mode: ShareMode,
    receiver: ShareReceiver,
    notifyReceiver: Boolean = true,
): GivenShare?

// Change the access mode of an existing share
suspend fun updateShare(webId: String, resourceUri: String, mode: ShareMode, receiver: ShareReceiver): GivenShare?

// Remove a receiver's access (sends a best-effort withdrawal notification for a WebID receiver)
suspend fun revokeShare(webId: String, resourceUri: String, receiver: ShareReceiver)

// Fast read from the on-pod index; re-validate against live ACLs with refreshGivenShares
suspend fun getStoredGivenShares(webId: String): List<GivenShare>
suspend fun refreshGivenShares(webId: String): List<GivenShare>

// Authoritative: read the shares straight from a resource's ACL
suspend fun getGivenSharesForResource(webId: String, resourceUri: String): List<GivenShare>

// Rebuild the index by walking the pod's ACLs (expensive — expose as an explicit user action)
suspend fun rebuildGivenIndex(webId: String): List<GivenShare>

// --- Shares you receive ---

suspend fun getStoredReceivedShares(webId: String): List<ReceivedShare>
suspend fun refreshReceivedShares(webId: String): List<ReceivedShare>

// Start tracking a resource shared with you (e.g. after scanning a QR / opening a share link)
suspend fun addReceivedShare(webId: String, resourceUri: String): ReceivedShare?
suspend fun removeReceivedShare(webId: String, resourceUri: String, ownerWebId: String)

// --- Access grants, requests, catalog ---

// Every observable access relationship (given, received, incoming requests, SAI grants), unified
suspend fun getAccessGrants(webId: String): List<AccessGrant>

// Decide on an incoming access request (from SolidNotificationsClient.listRequests)
suspend fun acceptShareRequest(webId: String, request: ShareRequest): GivenShare?
suspend fun rejectShareRequest(webId: String, request: ShareRequest, reason: String? = null)

// Public catalog of resources others may request access to (the resources stay private)
suspend fun publishCatalogEntry(webId: String, entry: CatalogEntry)
suspend fun removeCatalogEntry(webId: String, resourceUri: String)
suspend fun getOwnerCatalog(viewerWebId: String, ownerWebId: String): List<CatalogEntry>
```

### Example

```kotlin
val sharingClient = Solid.getSharingClient(context)

sharingClient.connectionState().collect { connected ->
    if (connected) {
        try {
            // Share a note with Bob at Edit (read + write) and notify his inbox
            sharingClient.createShare(
                webId = myWebId,
                resourceUri = "https://mypod.example/notes/hello.ttl",
                mode = ShareMode.WRITE,
                receiver = ShareReceiver.WebIdReceiver("https://bob.example/profile/card#me"),
            )

            // List what I've shared, and act on incoming requests
            val given = sharingClient.getStoredGivenShares(myWebId)
            val grants = sharingClient.getAccessGrants(myWebId)
        } catch (e: SolidException) {
            handleException(e)
        }
    }
}
```

---

## SolidNotificationsClient

!!! note "New in 0.5.0"

Reads and sends share-notification inbox messages over [Linked Data Notifications](https://www.w3.org/TR/ldn/)
(LDN). Obtain via `Solid.getNotificationsClient(context)`.

This layer is **pull-only** — there is no push subscription. Call `listNotifications()` /
`listRequests()` from a periodic background worker (Android's effective minimum is 15 minutes) and on
user-initiated refresh. All methods are `suspend` functions and throw `SolidException` on failure.

```kotlin
// Connection state of the notifications IPC service
fun connectionState(): Flow<Boolean>

// Incoming share notifications (offers, accepts, withdrawals, rejections)
suspend fun listNotifications(webId: String): List<ShareNotification>

// Incoming access requests — others asking for access to this user's resources
suspend fun listRequests(webId: String): List<ShareRequest>

// Tell receiverWebId you granted / withdrew access (used internally by createShare/revokeShare)
suspend fun sendOffer(ownerWebId: String, receiverWebId: String, resourceUri: String, mode: ShareMode)
suspend fun sendUndo(ownerWebId: String, receiverWebId: String, resourceUri: String)

// Ask ownerWebId for access; decline a request
suspend fun sendRequest(requesterWebId: String, ownerWebId: String, resourceUri: String, requestedMode: ShareMode, summary: String? = null)
suspend fun sendReject(ownerWebId: String, requesterWebId: String, resourceUri: String, reason: String? = null)

// Garbage-collect the inbox: drop Offer/Undo pairs and (optionally) items older than an ISO instant
suspend fun compactInbox(webId: String, olderThanIso: String? = null)
```

To accept or reject a request returned by `listRequests()`, pass it to
`SolidSharingClient.acceptShareRequest()` / `rejectShareRequest()` — accepting creates the share and
notifies the requester.

---

## Exception Hierarchy

All client methods throw a subclass of `SolidException`:

```
SolidException
├── SolidAppNotFoundException           — ASS app is not installed
├── SolidServiceConnectionException     — IPC connection to ASS failed
├── SolidNotLoggedInException           — No user is logged in inside ASS
├── SolidServicesDrawPermissionDeniedException — the deprecated requestLogin was called
└── SolidResourceException
    ├── NotSupportedClassException      — resource class doesn't extend RDFSource/NonRDFSource
    ├── NotPermissionException          — app not authorized to access this resource
    ├── NullWebIdException              — no WebID available for the request
    └── UnknownException                — unexpected server or protocol error
```

---

## Example App

[Solid Contacts](https://github.com/pondersource/Solid-Contacts) is a full open-source app built on this library, demonstrating multi-account contacts management on a Solid pod.
