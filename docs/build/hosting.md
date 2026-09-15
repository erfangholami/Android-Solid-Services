---
title: Hosting the services
description: The host library — how an app serves other apps' client calls, checks their grants, and shows a consent screen.
---

# Hosting the services

Every `client` call arrives somewhere. That somewhere is a **host app**: it holds the accounts, the
tokens and the keys, exports five bound services, and decides what each calling app may do.
[Solid Share](https://solidshare.app) is the host, and the `host` library is what it is built on.

!!! warning "You probably do not need this page"
    Installing Solid Share is the supported answer. A second host splits the user's accounts across
    two apps, and a device with two hosts asks people to understand a distinction they should never
    have to. Read on if you are forking the ecosystem, shipping into an environment Solid Share
    cannot reach, or working on Solid Share itself.

```kotlin title="build.gradle.kts"
dependencies {
    implementation("com.erfangholami.androidsolidservices:host:0.8.0")
    implementation("com.erfangholami.androidsolidservices:api:0.8.0")
}
```

`host` depends on `api`, because it serves calls by making them: your process is the one that talks
to the pod.

## The five services

Each service is a few lines. Extend `HostService`, build the binder from what you inject, and let
the base class own the dispatch scope and caller attribution.

```kotlin
@AndroidEntryPoint
class MyResourceService : HostService() {

    @Inject lateinit var resourceManager: SolidResourceManager
    @Inject lateinit var session: HostSession
    @Inject lateinit var guard: AccessGuard

    override fun createBinder(): IBinder = ResourceBinder(
        resourceManager = resourceManager,
        session = session,
        guard = guard,
        scope = serviceScope,          // (1)!
        spoolDirectory = cacheDir,     // (2)!
    )
}
```

1. `HostService` cancels this in `onDestroy`, so no pod call outlives its service.
2. Where a streamed upload is spooled before it is sent, because the request may legitimately be
   re-sent.

Declare each one against its action from `SolidHostContract`. The SDK binds by **action inside your
package**, never by class name, so these classes stay yours to rename.

```xml
<service android:name=".ipc.MyResourceService" android:exported="true">
    <intent-filter>
        <action android:name="com.erfangholami.androidsolidservices.action.RESOURCES" />
    </intent-filter>
</service>
```

| Binder | Action | Wraps |
|---|---|---|
| `AuthenticatorBinder` | `ACTION_AUTHENTICATOR` | session state and the caller's own grant |
| `ResourceBinder` | `ACTION_RESOURCES` | `SolidResourceManager` |
| `DataModulesBinder` | `ACTION_DATA_MODULES` | the contacts and tickets modules |
| `SharingBinder` | `ACTION_SHARING` | `SharingManager` |
| `NotificationsBinder` | `ACTION_NOTIFICATIONS` | `NotificationsManager` |

!!! danger "Do not add `android:permission`"
    The grant is the guard. A custom permission adds nothing, because any app can declare one, and
    it would only stop apps that had already been refused.

## The guard

`AccessGuard` is the three gates every verb passes: the caller resolves to a package, the WebID has
a live session, and the grant covers the verb. Build one and share it across the binders.

```kotlin
val grants: AppGrantStore = DataStoreAppGrantStore(preferencesDataStore)
val moduleRoots: ModuleRootResolver = CachingModuleRootResolver(
    DataModuleRootResolver(contactsDataModule, ticketsDataModule),
)
val guard = AccessGuard(
    session = AuthenticatorHostSession(authenticator),
    policy = ScopedAccessPolicy(grants, moduleRoots),
)
```

`ScopedAccessPolicy` implements the rules the [App access](app-access.md) page documents, and
`VerbAccess` is that verb table in code. `ModuleRootResolver` answers where a data module lives on
a given pod, so a grant on Contacts can cover a raw resource call that names one of its containers;
only the host can know that, which is why it is an interface you supply.

The caller's package comes from `Binder.getCallingUid()`, which is trustworthy — but it is readable
only while the binder transaction is on the stack. `AccessGuard.gate(...)` captures it for you at
the right moment; if you write your own dispatch, read it before you `launch`.

## The consent screen

`AuthorizeProtocol` is the host's half of the sign-in contract. Your activity reads the request,
lets the user decide, and finishes with one of three results.

```kotlin
class MyAuthorizeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val caller = CallerIdentity.of(this) ?: return finishWith(     // (1)!
            AuthorizeProtocol.errorResult(ExceptionsErrorCode.UNKNOWN, "Start me for a result."),
        )
        val request = AuthorizeProtocol.requestFrom(intent)            // (2)!

        // …show the caller, the accounts, the level and the targets…

        val entries = AuthorizeProtocol.entriesFor(request, storageRootsOf(webId))   // (3)!
        val grant = AppGrant(caller.packageName, webId, caller.label, entries, Instant.now().toString())
        grants.put(grant)
        finishWith(AuthorizeProtocol.grantedResult(webId, grant))
    }
}
```

1. `null` when the activity was not started for a result. Refuse then: Android names the calling
   package only in that case, and a grant recorded against a caller you cannot name is a grant for
   whoever asks next.
2. `AccessRequest.DEFAULT` when the app sent none — the whole pod at Edit.
3. Requested paths are storage-relative, because at request time the app does not know the WebID.
   This resolves each against every storage root of the chosen account.

Declare the activity with `ACTION_AUTHORIZE` **and the `DEFAULT` category**. The SDK sends an Intent
carrying an action and a package but no component, and activity resolution only matches filters
that declare `DEFAULT`.

```xml
<activity android:name=".MyAuthorizeActivity" android:exported="true">
    <intent-filter>
        <action android:name="com.erfangholami.androidsolidservices.action.AUTHORIZE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

## Storing grants

`AppGrantStore` keeps one grant per `(package, WebID)`. `DataStoreAppGrantStore` is the
implementation over a Preferences `DataStore`; a value it cannot parse reads as no grants, which
refuses every call rather than allowing one.

Two lifecycle hooks matter, and forgetting either is a security bug rather than a rough edge:

- **Signing an account out** must call `revokeAll(webId)`. A grant that outlives its session comes
  back to life when the user signs in again.
- **An app being uninstalled** should call `revokePackage(packageName)`, from a receiver for
  `ACTION_PACKAGE_FULLY_REMOVED`. Ignore the replacing case, or an ordinary update drops access.

Grants are **local to the device by design**. Nothing about them is written to the pod; the pod
authenticates the user, and has no way to see which app on their phone made a request. The
reasoning, and why this is not Web Access Control or Access Control Policy, is in Solid Share's own
`APP_ACCESS.md`.

## The client's side

A caller reaches you only if the SDK resolves your package as the host **and** your build carries
a signing key it accepts. `HostResolver` holds that list, and today it holds one entry. Adding a
second is a change to the SDK, not something a host can arrange for itself — which is the point:
binding by action alone would let any app declare the action and receive the user's data.

The key check matters because a package name is not an identity. Android will not let a second
app claim the host's name while the real one is installed, but on a device where it is absent, an
app sideloaded under that name would otherwise be bound to. The SDK compares the SHA-256 of the
installed host's signing certificate against the digests it ships, and refuses a mismatch with a
message that says so rather than a bare "not installed".

Verification is skipped when the **calling** app is a debug build, so you can run against a host
you built locally. The flag is read from the caller, which an attacker cannot set on somebody
else's release build, rather than from the host, which they could.

See [App access](app-access.md) for what each verb requires, and
[Architecture](../project/architecture.md) for how the four libraries fit together.
