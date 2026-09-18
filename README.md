[![client](https://img.shields.io/maven-central/v/com.erfangholami.androidsolidservices/client.svg?label=client)](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client)
[![api](https://img.shields.io/maven-central/v/com.erfangholami.androidsolidservices/api.svg?label=api)](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api)
[![host](https://img.shields.io/maven-central/v/com.erfangholami.androidsolidservices/host.svg?label=host)](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/host)
[![Docs](https://img.shields.io/badge/docs-site-blueviolet)](https://androidsolidservices.erfangholami.com)
[![License](https://img.shields.io/github/license/erfangholami/Android-Solid-Services)](LICENSE)

# Android Solid Services

The Solid SDK for Android. [Solid Share](https://solidshare.app) holds the user's pod accounts;
every other app on the device reaches those pods through it, within a scope the user approved and
without ever handling a credential.

📖 **[Full documentation](https://androidsolidservices.erfangholami.com)** — guides, API reference
and troubleshooting. The site is versioned: it opens on the newest release, and the selector in the
header switches to the docs for an older one. Before 1.0 the IPC contract changes between minors,
so read the version that matches the SDK you depend on.

## The problem

Solid gives people their own data, but on Android every app that wants to use a pod has to become
an identity client: run the OIDC flow, mint and rotate DPoP tokens, store them safely, and repeat
all of it in the next app. The user signs in again in each one, and each app's mistakes are theirs
alone to make.

## How this solves it

The host app owns the login. Tokens are DPoP-bound to keys generated in the Android Keystore and
never leave that app; other apps talk to it over AIDL and get results, never credentials. The user
signs in once and decides, for each app, what it may do and where — the whole pod, a few folders,
or one data module, at View, Add, Edit or Full access — and can narrow or revoke it at any time.
An app integrates with one dependency and no auth code at all.

```kotlin
// The whole of your authentication code.
private val authorize = registerForActivityResult(
    AuthorizeWithSolid(
        AccessRequest(level = AccessLevel.EDIT, targets = listOf(RequestedTarget.Path("notes/"))),
    ),
) { result ->
    if (result is SolidSignInResult.Authorized) onSignedIn(result.webId, result.grant)
}
```

## Features

- **One sign-in, many apps** — several accounts from different pod providers, active at once.
- **Scoped app grants** — a level on the whole pod, on named folders, or on a data module.
  Every verb of every service is checked against what the user approved.
- **Native consent screen** — sign-in launches from the calling app's own foreground, so no
  special permissions are involved.
- **Solid accounts in Android Settings**, alongside every other account on the device.
- **Full pod access over IPC** — resources (CRUD, containers, patches, streaming), sharing,
  Linked Data Notifications, and data modules for contacts and tickets.
- **Four libraries** — `client` for apps that go through Solid Share, `api` for apps that speak
  to pods directly, `host` for apps that want to host the services themselves, and `shared`
  underneath them all.

## Install

Users install **[Solid Share](https://solidshare.app)**, the host app:

<p>
  <a href="https://play.google.com/store/apps/details?id=com.erfangholami.solidshare"><img src="docs/assets/badges/google-play.png" alt="Get it on Google Play" height="60"></a>
  &nbsp;
  <a href="https://f-droid.org/packages/com.erfangholami.solidshare/"><img src="docs/assets/badges/f-droid.png" alt="Get it on F-Droid" height="60"></a>
</p>

> **The Android Solid Services app is discontinued.** 0.7.2 was its last release and it works
> with the 0.7.2 libraries only. From 0.8.0 the host is Solid Share, and `client` does not bind
> to the old app.

For your own app, one dependency:

```kotlin
implementation("com.erfangholami.androidsolidservices:client:0.8.1")
```

The libraries need `compileSdk = 37` and `minSdk = 26`.

Then follow the **[Quickstart](https://androidsolidservices.erfangholami.com/start/quickstart/)**.
Upgrading from an older version? Read the
**[upgrade guide](https://androidsolidservices.erfangholami.com/project/upgrading/)** first.
There is also a [sample app](https://github.com/erfangholami/Android-Solid-Service_client-sample)
that runs every SDK call against a live pod, shown next to the code that makes it.

## Build

Requires **JDK 17** (or JetBrains Runtime 17.0.9); set `JAVA_HOME` if the build complains.

```sh
./gradlew assembleDebug         # the four libraries
./gradlew test                  # unit tests, all modules
./gradlew spotlessApply detekt  # format, then static analysis
./gradlew publishToMavenLocal -PassVersion=0.8.0   # try an unreleased version in a consumer
```

Versions come from the git tag, so a working copy needs no version edits. The
[Architecture](https://androidsolidservices.erfangholami.com/project/architecture/) page explains
how the modules fit together, and the host app lives in its own repository.

## Contributing

Contributions are welcome — bug reports, fixes, docs and pod-server compatibility reports all
help.

- Branch from `dev` and open your pull request against it; CI runs style, static analysis, unit
  tests and the instrumented IPC suite.
- Run `./gradlew spotlessApply detekt test` before pushing.
- Cover behaviour with a test where you can. The client SDK's tests drive real calls across a
  process boundary, which is where most defects here have lived.
- Found something odd against a particular pod server? Say which server and how it responded —
  those reports have led to several fixes.

Please open an issue first for anything large, so the approach can be agreed before you spend time
on it.

## Acknowledgments

Thanks to funding
from [NLnet](https://nlnet.nl/) <img src="https://nlnet.nl/logo/banner.svg" style="width: 5%; margin: 0 1% 0 1%;">
/ <img src="https://nlnet.nl/image/logos/NGI0Entrust_tag.svg" style="width: 5%; margin: 0 1% 0 1%;">
