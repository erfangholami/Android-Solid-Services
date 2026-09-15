# Architecture

## Modules and Integration Paths

Four published Gradle modules in a layered chain. `Shared` holds the common types; `api`, `client` and `host` all build on it. All four go to Maven Central, and a third-party app integrates along **one of two paths** — or, if it wants to be a host itself, adds the third.

- **Path 1 — embedded (`api`):** depend on `api` and reach the Solid pod **directly**, performing your own login and signing every request in-process. Self-contained — no host app needs to be installed, but your app manages its own credentials and keys, and nothing scopes what it may do.
- **Path 2 — single sign-in (`client`):** depend on `client` and delegate over **AIDL IPC** to the installed host app, [Solid Share](https://solidshare.app), which holds the credentials and talks to the pod for you. One shared login across every Solid app on the device, no token handling in your app, and the user decides what your app may reach.
- **Hosting (`host`):** depend on `host` to *be* the app on the other end of Path 2 — the five AIDL binders, the access policy, the grant store and the consent protocol. Solid Share is built on it; the library ships no app of its own.

```
graph TD
    %% ── Consumers: the two integration paths ──
    TPA1["Third-party app<br/><b>Path 1 · embedded</b><br/>self-hosted auth and keys"]
    TPA2["Third-party app<br/><b>Path 2 · single sign-in</b><br/>delegates to Solid Share"]

    subgraph CLIENT["client — IPC SDK (Maven Central)"]
        direction TB
        CL_ENTRY["Solid — get*Client()"]
        CL_PROXY["SignIn · Resource · Contacts · Tickets ·<br/>Sharing · Notifications clients"]
        CL_CONN["HostResolver · ServiceConnector · SolidException"]
    end

    subgraph HOST["host — host-side SDK (Maven Central)"]
        direction TB
        HO_BIND["5 AIDL binders<br/>Authenticator · Resource · DataModules ·<br/>Sharing · Notifications"]
        HO_POLICY["AccessGuard · ScopedAccessPolicy ·<br/>VerbAccess · ModuleRootResolver"]
        HO_STORE["AppGrantStore · AuthorizeProtocol ·<br/>CallerIdentity · HostService"]
    end

    subgraph API["api — direct Solid access (Maven Central)"]
        direction TB
        AP_AUTH["Authenticator — OIDC,<br/>DPoP or Bearer, multi-account"]
        AP_RES["SolidResourceManager"]
        AP_SHARE["SharingManager · NotificationsManager ·<br/>contacts and tickets data modules"]
        AP_HTTP["SolidHttpClient + cache ·<br/>WAC / ACP access backends"]
    end

    subgraph SHARED["Shared — common types · AIDL · RDF (Maven Central)"]
        direction TB
        SH_MODEL["model/ — resource · sharing ·<br/>contacts · profile · access · grant"]
        SH_HOST["host/ — SolidHostContract"]
        SH_RDF["rdf/ codecs · vocab/ · http/"]
        SH_AIDL["AIDL service + parcelable defs"]
    end

    APP["Solid Share<br/>(its own repository)"]
    POD["Solid Pod"]

    %% Path 1 — embed api, talk to the pod directly
    TPA1 -->|depends on| API
    API -->|"HTTPS · DPoP or Bearer"| POD

    %% Path 2 — use client, which IPCs into the host app
    TPA2 -->|depends on| CLIENT
    CLIENT -.->|"AIDL IPC, bound by action"| HO_BIND
    APP -->|depends on| HOST
    HOST --> API

    %% Foundation dependencies
    API --> SHARED
    CLIENT --> SHARED
    HOST --> SHARED

    %% Outlines only — fills and text follow the page theme, so this reads in light and dark.
    classDef consumer stroke:#4285f4,stroke-width:2px;
    classDef pod stroke:#ea4335,stroke-width:2px;
    class TPA1,TPA2 consumer
    class POD pod

    style CLIENT stroke:#34a853,stroke-width:2px
    style API stroke:#34a853,stroke-width:2px
    style SHARED stroke:#34a853,stroke-width:2px
    style HOST stroke:#34a853,stroke-width:2px
    style APP stroke:#f9ab00,stroke-width:2px
```

Both paths run the **same `api` engine** against the pod — the difference is *where* it runs: in the third-party app's own process (Path 1), or inside the host app's process behind AIDL (Path 2). `api`, `client` and `host` each depend on `Shared`; `host` also depends on `api`.

Package roots: `…api`, `…client`, `…host` and `…shared` under `com.erfangholami.androidsolidservices`.

The Android Solid Services app is discontinued

Until 0.7.2 this repository also built an `app` module — the host app of the same name. It was removed at 0.8.0, when [Solid Share](https://solidshare.app) took over hosting, and the `host` library is what it left behind. A 0.7.2 app and a 0.7.2 `client` still work together; neither talks to 0.8.0.

______________________________________________________________________

## IPC: How Apps Communicate

Apps that take the single-sign-in path (`client`) **never talk directly to the Solid pod**. They bind to one of the AIDL services in the host app, which uses the `api` module to reach the pod over authenticated HTTPS. (Apps on the embedded path link `api` and make these same calls in-process.)

Binding is **by intent action** — the six actions in `SolidHostContract` — inside the host's package, and the SDK binds only to Solid Share. That keeps the host free to name, move or merge its service classes without breaking installed apps.

```
graph LR
    subgraph "Third-party app process"
        C["client SDK<br/>Solid.get*Client()"]
    end
    subgraph "Solid Share process"
        SVC["host binders (AIDL)<br/>Authenticator · Resource · DataModules<br/>Sharing · Notifications"]
        GUARD["AccessGuard + ScopedAccessPolicy<br/>over the app's AppGrant"]
        API["api<br/>SolidHttpClient · DPoP/Bearer · response cache"]
        SVC --> GUARD
        GUARD --> API
    end
    POD["Solid Pod"]

    C -- "AIDL IPC" --> SVC
    API -- "HTTPS + DPoP/Bearer" --> POD
```

Each `client` entry point binds by its own action, and the host answers with the matching binder:

| `client` entry point                                       | `SolidHostContract` action | `host` binder         | Responsibility                   |
| ---------------------------------------------------------- | -------------------------- | --------------------- | -------------------------------- |
| `Solid.getSignInClient()`                                  | `ACTION_AUTHENTICATOR`     | `AuthenticatorBinder` | Session state, the app's grant   |
| `Solid.getResourceClient()`                                | `ACTION_RESOURCES`         | `ResourceBinder`      | CRUD on pod resources            |
| `Solid.getContactsDataModule()` / `getTicketsDataModule()` | `ACTION_DATA_MODULES`      | `DataModulesBinder`   | Contacts, address books, tickets |
| `Solid.getSharingClient()`                                 | `ACTION_SHARING`           | `SharingBinder`       | Shares, access grants            |
| `Solid.getNotificationsClient()`                           | `ACTION_NOTIFICATIONS`     | `NotificationsBinder` | LDN inbox                        |

There is a sixth action, `ACTION_AUTHORIZE`, which is an Activity rather than a service: it is what `AuthorizeWithSolid` launches for the consent screen.

Each service is an Android **bound service**. Every call waits for its binding, so nothing has to be collected first; the `Flow<Boolean>` connection state each client exposes is for UI.

AIDL interface definitions (both parcelable types and service contracts) live in `Shared/src/main/aidl/`.

______________________________________________________________________

## Authentication: OpenID Connect (DPoP or Bearer)

The auth flow uses the **AppAuth** library (`net.openid:appauth`) for the OpenID Connect code exchange. Token binding is **negotiated** from the provider's discovery document: if it advertises DPoP (Demonstration of Proof-of-Possession) support via `dpop_signing_alg_values_supported`, the SDK uses DPoP; otherwise it falls back to standard **Bearer** tokens.

1. User enters their WebID or selects an OpenID provider.
1. The SDK resolves the OIDC issuer from the WebID document.
1. A browser intent opens the provider's login page.
1. The provider redirects back with an authorization code.
1. The SDK exchanges the code for access + refresh tokens.
1. Every subsequent pod request carries an `Authorization: <DPoP|Bearer> <token>` header; when DPoP was negotiated, a freshly signed DPoP proof is attached as well — binding the token to the request and preventing replay.

When DPoP is in effect, each account has its **own DPoP key pair** in the Android Keystore. Multi-account state (profiles, tokens) is persisted with **DataStore**, **encrypted at rest** (AES-256-GCM via an Android Keystore key). Login can use either dynamic client registration or a hosted **Solid-OIDC Client ID Document** (`clientId`). Token refresh is nonce-aware (per-origin DPoP nonces, retry on `use_dpop_nonce`) and resilient — a recoverable failure no longer forces a re-login.

______________________________________________________________________

## Module Breakdown

### Shared (`com.erfangholami.androidsolidservices.shared`)

Common types shared across all modules. Published implicitly as a transitive dependency. Since 0.5.0 it is organized into intent-based packages (`model/`, `rdf/`, `http/`, `result/`, `error/`, `util/`, `vocab/`), and its public API exposes only plain types — a `String` content-type and a `SolidHeaders` value type rather than okhttp / titanium-json-ld types (the JSON-LD codec is an internal dependency).

| Area              | Contents                                                                                                                                                                           |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Resource model    | `Resource` → `RDFResource` / `NonRDFResource` → `SolidRDFResource` / `SolidNonRDFResource` / `SolidContainer` (with `size` / `createdTime` / `lastModified` accessors since 0.5.0) |
| Result types      | `SolidResult<T>` (sealed: `Success`, `Failure`) with typed `SolidError`, `SolidHeaders`, `HTTPConstants`                                                                           |
| Data module types | `AddressBook`, `AddressBookList`, `Contact`, `SolidContact`, `SolidContactList`, `ContactData`, `FullGroup`, `NewTicket`, `Ticket`, `TicketList`                                   |
| Sharing types     | `GivenShare`, `ReceivedShare`, `ShareMode`, `ShareReceiver`, `AccessGrant`, `CatalogEntry`, `ShareNotification`, `ShareRequest` (0.5.0)                                            |
| App-grant types   | `AccessLevel`, `GrantTarget`, `GrantEntry`, `AppGrant`, `AccessRequest`, `RequestedTarget`, `DataModuleId` (0.8.0)                                                                 |
| Host contract     | `SolidHostContract` — the host package, the account type and the six intent actions (0.8.0)                                                                                        |
| Patch type        | `N3Patch` — type-safe DSL and diff factory for [Solid N3 Patch](https://solidproject.org/TR/protocol#n3-patch) documents                                                           |
| Vocabulary        | `LDP`, `VCARD`, `ACL`, `ACP`, `OWL`, `DC`, `RDFS`, `Solid` constants                                                                                                               |
| AIDL parcelables  | Parcelable wrappers for cross-process data transfer (all definitions consolidated here)                                                                                            |

### api (`com.erfangholami.androidsolidservices.api`)

Direct Solid server communication. Used internally by the ASS app and available as a standalone library.

| Class                                                               | Role                                                                     |
| ------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `Authenticator` / `AuthenticatorImplementation`                     | OIDC auth (DPoP or Bearer), multi-account, Client ID Document            |
| `SolidResourceManager` / `SolidResourceManagerImplementation`       | CRUD on pod resources (+ `head`/`patch`/`createInContainer`)             |
| `SolidContactsDataModule` / `SolidContactsDataModuleImplementation` | Contacts data module                                                     |
| `SharingManager` / `SharingManagerImplementation`                   | Resource sharing — WAC/ACP grants, index, share links (0.5.0)            |
| `NotificationsManager` / `NotificationsManagerImplementation`       | LDN inbox — offers, requests, accept/reject (0.5.0)                      |
| `AccessBackend` (`WacBackend` / `AcpBackend`)                       | Internal access-control writers behind sharing (0.5.0)                   |
| `SolidHttpClient` / `SolidResponseCache`                            | OkHttp-based Solid HTTP client + in-memory response cache (0.5.0)        |
| `DPoPGenerator`                                                     | Signs DPoP proof JWTs when the provider supports DPoP (per-account keys) |

### client (`com.erfangholami.androidsolidservices.client`)

IPC client library. No direct pod access — all calls are proxied through the host app.

| Class                      | Role                                                                                                                                 |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `Solid`                    | Entry point: `getSignInClient()`, `getResourceClient()`, `getContactsDataModule()`, `getSharingClient()`, `getNotificationsClient()` |
| `SolidSignInClient`        | Auth IPC client                                                                                                                      |
| `SolidResourceClient`      | Resource CRUD IPC client (`head`/`patch`/`update(ifMatch)` since 0.5.0)                                                              |
| `SolidContactsDataModule`  | Contacts IPC client                                                                                                                  |
| `SolidSharingClient`       | Resource sharing IPC client (0.5.0)                                                                                                  |
| `SolidNotificationsClient` | LDN inbox IPC client (0.5.0)                                                                                                         |
| `ServiceConnector`         | Shared, self-healing AIDL bind/callback plumbing (0.5.0)                                                                             |
| `HostResolver`             | Finds the host, and fails fast with a typed message when it is missing (0.8.0)                                                       |
| `SolidException` hierarchy | Typed exceptions for all failure modes                                                                                               |

### host (`com.erfangholami.androidsolidservices.host`)

Host-side library, new in 0.8.0. Everything an app needs to be the other end of `client`. [Solid Share](https://solidshare.app) is the app built on it; this repository ships no app.

| Class                                                                                                | Role                                                               |
| ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `HostService`                                                                                        | `Service` base owning the dispatch scope and caller attribution    |
| `AuthenticatorBinder`, `ResourceBinder`, `DataModulesBinder`, `SharingBinder`, `NotificationsBinder` | The five AIDL implementations, one per service action              |
| `AccessGuard` / `AccessPolicy` / `ScopedAccessPolicy`                                                | The check every verb passes through, against the caller's grant    |
| `VerbAccess`                                                                                         | The verb table in code — which level each operation needs          |
| `AppGrantStore` / `DataStoreAppGrantStore`                                                           | Grants as JSON under one Preferences key, with revocation          |
| `ModuleRootResolver` / `DataModuleRootResolver`                                                      | Where a data module lives on a pod, cached                         |
| `AuthorizeProtocol` / `CallerIdentity`                                                               | The Intent protocol and caller identity a consent screen builds on |
| `HostSession` / `AuthenticatorHostSession`                                                           | Session state over the `api` `Authenticator`                       |

______________________________________________________________________

## Technology Summary

| Technology                     | Version / Notes                                                                                                                                                                                              |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Kotlin                         | Coroutines, Flow, serialization                                                                                                                                                                              |
| Jetpack Compose                | UI — no XML views                                                                                                                                                                                            |
| AIDL                           | Cross-process communication                                                                                                                                                                                  |
| AppAuth (`net.openid:appauth`) | OpenID Connect                                                                                                                                                                                               |
| `SolidHttpClient`              | Custom OkHttp-based Solid HTTP client (replaces Inrupt Java Client SDK); in-memory response cache since 0.5.0                                                                                                |
| Titanium JSON-LD               | RDF, JSON-LD parsing (internal `implementation` dependency — off the public API surface since 0.5.0); the Activity Streams 2.0 context ships in `Shared` since 0.7.1, so notifications parse with no network |
| DataStore                      | Local persistence (Preferences + a kotlinx.serialization JSON `Serializer`); token store encrypted at rest (AES-256-GCM, Android Keystore) since 0.5.0; `host` keeps app grants here too                     |
| kotlinx.serialization          | JSON serialization (replaced Gson in v0.3.0)                                                                                                                                                                 |
| Min SDK                        | 26 (Android 8.0)                                                                                                                                                                                             |
| Target / Compile SDK           | 36 / 37                                                                                                                                                                                                      |
| JVM target                     | 17                                                                                                                                                                                                           |

## Monitoring

The libraries report nothing by themselves. A host application installs a sink and decides where the reports go; Solid Share installs a Firebase one in its Play build and none in its F-Droid build.

### Why the libraries stay Firebase-free

All four libraries are published to Maven Central and compiled into third-party applications. Making them depend on Firebase would force every consumer to add a Firebase project and a `google-services.json`, and would route SDK telemetry into *their* Firebase project — a poor fit for an SDK whose whole purpose is keeping personal data under the user's control.

Instead the libraries emit through a small interface in `shared.telemetry`:

```text
libraries  ──emit──▶  Telemetry (facade)  ──▶  TelemetrySink
                                                    │
                          no sink installed ────────┤──▶ no-op, nothing collected
                          host installs a bridge ───┴──▶ the host's own backend
```

Nothing is collected or transmitted until a host application calls `Telemetry.install(...)`. The libraries add no monitoring dependency of any kind.

### What is reported

| Signal                                      | Source                                  | Destination                                          |
| ------------------------------------------- | --------------------------------------- | ---------------------------------------------------- |
| Uncaught crashes                            | Whole app process, incl. binder threads | Crashlytics                                          |
| Swallowed data-module exceptions            | `host` `AidlDispatch`                   | Crashlytics (non-fatal)                              |
| Non-transport request faults                | `SolidHttpClient.solidFailure`          | Crashlytics (non-fatal)                              |
| Transport failures (offline, reset)         | `SolidHttpClient.solidFailure`          | Crashlytics breadcrumb only                          |
| Requests for a WebID with no usable session | `SolidHttpClient.solidFailure`          | Crashlytics breadcrumb only                          |
| Terminal token-refresh failures             | `TokenRefreshCoordinator`               | Crashlytics (non-fatal) + `solid_auth_refresh` trace |
| HTTP request timing                         | `SolidHttpClient.send`                  | Performance (network span)                           |
| IPC bind latency and binder deaths          | `client` `ServiceConnector`             | Performance + breadcrumbs                            |
| App start, screen rendering                 | Firebase SDK                            | Performance                                          |

A dropped connection is a fact of mobile life, not a defect, so `IOException` is recorded as a breadcrumb rather than a non-fatal. That keeps a device going offline from generating hundreds of identical Crashlytics reports while still leaving the context attached to whatever is reported next. The same holds for a request made for a WebID whose session has expired or was never signed in: it fails as `SolidError.NotAuthenticated` before touching the network and leaves a breadcrumb, because the expiry itself was already reported once by the refresh coordinator.

### Attributing failures to the integrating app

Every report carries a `calling_app` custom key naming the application whose IPC call was being serviced, or `self` when the work started in the host's own UI. `CallerAttribution.currentCaller()` reads `Binder.getCallingUid()` and resolves it through `PackageManager`, caching successful lookups.

The read happens in `beginAttributedCall()` **before** `launch`, because a caller's identity is only visible while the binder transaction is still on the stack — by the time the coroutine runs, `getCallingUid()` reports the host process itself. `dispatchDataModule` carries the resolved name into its closure and attaches it to the report directly.

Note that Crashlytics custom keys are process-global, so under genuinely concurrent calls from two different apps the `calling_app` key on a deep `api` non-fatal reflects the most recent caller rather than the one that failed. The per-report attribute on `dispatchDataModule` is exact.

### Privacy

A Solid pod URL's path names the user's containers and resources, so no path ever reaches Firebase.

- **Manual network spans report the origin only** — `scheme://host[:port]`, via `URI.telemetryOrigin()` in `api/http/TelemetryOrigin.kt`. This is enough to break latency down per pod provider without describing what the user stores there.
- **Automatic Performance instrumentation is disabled** in the host. The Firebase Performance Gradle plugin rewrites bytecode to trace every OkHttp call with its complete URL, which would defeat the above.
- **No Firebase Analytics.** Crashlytics breadcrumb-from-Analytics integration is not wired up.
- WebIDs, pod resource paths, tokens and refresh-token fingerprints are never sent as attributes.

### Release only

In Solid Share, collection is a **release-build feature**: debug builds initialise Firebase but gather nothing, and the F-Droid flavour carries no Firebase at all. A host that installs a sink should gate it the same way, so development never ships noise to a dashboard.

### Plugging in your own backend

Any host — or any app embedding `api` — can capture the same signals without Firebase by implementing `TelemetrySink`:

```kotlin
class SentryTelemetrySink : TelemetrySink {
    override fun startSpan(name: String): TelemetrySpan = /* … */
    override fun startNetworkSpan(url: String, method: String): TelemetryNetworkSpan = /* … */
    override fun recordException(throwable: Throwable, attributes: Map<String, String>) {
        Sentry.captureException(throwable)
    }
    override fun log(message: String) = Sentry.addBreadcrumb(message)
    override fun setKey(key: String, value: String) = Sentry.setTag(key, value)
}

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.install(SentryTelemetrySink())
    }
}
```

`Telemetry` isolates sink failures: an exception thrown by a sink is swallowed rather than propagated into SDK code, so a broken backend can never break a pod operation.

#### What the SDK author can and cannot see

| Integration                       | Where the code runs                                                 | Visible in the host's console                                                                  |
| --------------------------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `client` + the host app installed | `client` in the integrator's process; **`api` in the host process** | Yes for everything `api` does, tagged with `calling_app`. No for the integrator's own process. |
| `api` embedded directly           | Entirely in the integrator's process                                | **Nothing.**                                                                                   |

There is no supported way to change the second row: `FirebaseCrashlytics.getInstance()` takes no `FirebaseApp`, so a library cannot report to a Crashlytics project other than its host app's, and installing an uncaught-exception handler from a library would break the host's own crash reporting. A library embedded in someone else's app can only report where that app tells it to — which is what `TelemetrySink` is for.

### Making collection user-consented

If collection follows the build type, put it behind a user opt-in instead by reading the stored preference where the build flag was, and re-running the installer when the preference changes:

```kotlin
crashlytics.setCrashlyticsCollectionEnabled(userOptedIn)
performance.isPerformanceCollectionEnabled = userOptedIn
if (userOptedIn) Telemetry.install(FirebaseTelemetrySink(crashlytics, performance))
else Telemetry.uninstall()
```

`Telemetry.uninstall()` restores the no-op sink, so the libraries go quiet immediately.
