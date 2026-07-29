# Monitoring

Android Solid Services reports crashes and performance data through Firebase Crashlytics and
Firebase Performance Monitoring. The app carries the Firebase dependency; the three published
libraries do not.

## Why the libraries stay Firebase-free

`api`, `client` and `shared` are published to Maven Central and are compiled into third-party
applications. Making them depend on Firebase would force every consumer to add a Firebase project
and a `google-services.json`, and would route SDK telemetry into *their* Firebase project — a poor
fit for an SDK whose whole purpose is keeping personal data under the user's control.

Instead the libraries emit through a small interface in `shared.telemetry`:

```
libraries  ──emit──▶  Telemetry (facade)  ──▶  TelemetrySink
                                                    │
                          no sink installed ────────┤──▶ no-op, nothing collected
                          app installs bridge ──────┴──▶ FirebaseTelemetrySink
```

Nothing is collected or transmitted until a host application calls `Telemetry.install(...)`. The
libraries add no monitoring dependency of any kind.

## What is reported

| Signal | Source | Destination |
| --- | --- | --- |
| Uncaught crashes | Whole app process, incl. binder threads | Crashlytics |
| Swallowed data-module exceptions | `AidlDispatch.dispatchDataModule` | Crashlytics (non-fatal) |
| Non-transport request faults | `SolidHttpClient.solidFailure` | Crashlytics (non-fatal) |
| Transport failures (offline, reset) | `SolidHttpClient.solidFailure` | Crashlytics breadcrumb only |
| Terminal token-refresh failures | `TokenRefreshCoordinator` | Crashlytics (non-fatal) + `solid_auth_refresh` trace |
| HTTP request timing | `SolidHttpClient.send` | Performance (network span) |
| IPC bind latency and binder deaths | `client` `ServiceConnector` | Performance + breadcrumbs |
| App start, screen rendering | Firebase SDK | Performance |

A dropped connection is a fact of mobile life, not a defect, so `IOException` is recorded as a
breadcrumb rather than a non-fatal. That keeps a device going offline from generating hundreds of
identical Crashlytics reports while still leaving the context attached to whatever is reported next.

## Attributing failures to the integrating app

Every report carries a `calling_app` custom key naming the application whose IPC call was being
serviced, or `self` when the work started in the ASS UI. `CallerAttribution.currentCaller()` reads
`Binder.getCallingUid()` and resolves it through `PackageManager`, caching successful lookups.

The read happens in `beginAttributedCall()` **before** `launch`, because a caller's identity is only
visible while the binder transaction is still on the stack — by the time the coroutine runs,
`getCallingUid()` reports the ASS process itself. `dispatchDataModule` carries the resolved name into
its closure and attaches it to the report directly.

Note that Crashlytics custom keys are process-global, so under genuinely concurrent calls from two
different apps the `calling_app` key on a deep `api` non-fatal reflects the most recent caller rather
than the one that failed. The per-report attribute on `dispatchDataModule` is exact.

## Privacy

A Solid pod URL's path names the user's containers and resources, so no path ever reaches Firebase.

- **Manual network spans report the origin only** — `scheme://host[:port]`, via
  `URI.telemetryOrigin()` in `api/http/TelemetryOrigin.kt`. This is enough to break latency down per
  pod provider without describing what the user stores there.
- **Automatic Performance instrumentation is disabled.** The Firebase Performance Gradle plugin
  rewrites bytecode to trace every OkHttp call with its complete URL, which would defeat the above.
  `firebasePerformanceInstrumentationEnabled=false` in `gradle.properties` turns that off. App-start
  and screen-render traces are SDK-side and unaffected; `@AddTrace` is also disabled by this flag.
- **No Firebase Analytics.** Crashlytics breadcrumb-from-Analytics integration is not wired up.
- WebIDs, pod resource paths, tokens and refresh-token fingerprints are never sent as attributes.

## Release only

Collection is a **release-build feature**. Debug builds initialise Firebase but gather nothing:

| | `debug` | `release` |
| --- | --- | --- |
| `firebase_crashlytics_collection_enabled` (manifest) | `false` | `true` |
| `firebase_performance_collection_enabled` (manifest) | `false` | `true` |
| `BuildConfig.TELEMETRY_ENABLED` | `false` | `true` |
| `Telemetry` sink installed | no — stays no-op | `FirebaseTelemetrySink` |

The manifest flags are the Firebase SDKs' own switches, so nothing is gathered in the window before
`Application.onCreate` runs. `installFirebaseTelemetry` then re-asserts both settings from
`TELEMETRY_ENABLED` — they persist across launches and the two build types share an `applicationId`,
so it sets them explicitly rather than trusting what a previous install left behind — and installs
the sink only when enabled.

To collect from a debug build temporarily, flip the `debug` block in `app/build.gradle.kts`:

```kotlin
debug {
    manifestPlaceholders["crashlyticsEnabled"] = true
    manifestPlaceholders["performanceEnabled"] = true
    buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
}
```

## Setting up Firebase

`app/google-services.json` must be present and must register an Android app with package name
`com.erfangholami.androidsolidservices` — the `google-services`, `crashlytics` and `firebase-perf`
plugins are applied unconditionally and the build fails without it. Download it from the
[Firebase console](https://console.firebase.google.com/) into `app/`.

### Release builds upload the R8 mapping

`assembleRelease` runs `uploadCrashlyticsMappingFileRelease`, which sends `mapping.txt` to Firebase
so release stack traces deobfuscate. It needs network access. To build a release without uploading:

```sh
./gradlew :app:assembleRelease -x uploadCrashlyticsMappingFileRelease
```

`app/proguard-rules.pro` keeps `SourceFile,LineNumberTable` and exception class names, which
Crashlytics needs to symbolicate and group reports.

## Plugging in your own backend

Third-party apps embedding the `client` SDK can capture the same signals without Firebase by
implementing `TelemetrySink`:

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

`Telemetry` isolates sink failures: an exception thrown by a sink is swallowed rather than
propagated into SDK code, so a broken backend can never break a pod operation.

### What the SDK author can and cannot see

| Integration | Where the code runs | Visible in the ASS Firebase console |
| --- | --- | --- |
| `client` + ASS app installed | `client` in the integrator's process; **`api` in the ASS process** | Yes for everything `api` does, tagged with `calling_app`. No for the integrator's own process. |
| `api` embedded directly | Entirely in the integrator's process | **Nothing.** |

There is no supported way to change the second row: `FirebaseCrashlytics.getInstance()` takes no
`FirebaseApp`, so a library cannot report to a Crashlytics project other than its host app's, and
installing an uncaught-exception handler from a library would break the host's own crash reporting.
A library embedded in someone else's app can only report where that app tells it to — which is what
`TelemetrySink` is for.

## Making collection user-consented

Collection currently follows the build type. To put it behind a user opt-in instead, replace the
`BuildConfig.TELEMETRY_ENABLED` reads in `installFirebaseTelemetry` with the stored preference and
re-run it when the preference changes:

```kotlin
crashlytics.setCrashlyticsCollectionEnabled(userOptedIn)
performance.isPerformanceCollectionEnabled = userOptedIn
if (userOptedIn) Telemetry.install(FirebaseTelemetrySink(crashlytics, performance))
else Telemetry.uninstall()
```

`Telemetry.uninstall()` restores the no-op sink, so the libraries go quiet immediately.
