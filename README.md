[![client](https://img.shields.io/maven-central/v/com.erfangholami.androidsolidservices/client.svg?label=client)](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client)
[![api](https://img.shields.io/maven-central/v/com.erfangholami.androidsolidservices/api.svg?label=api)](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api)
[![Docs](https://img.shields.io/badge/docs-site-blueviolet)](https://androidsolidservices.erfangholami.com)
[![License](https://img.shields.io/github/license/erfangholami/Android-Solid-Services)](LICENSE)

# Android Solid Services

Single sign-in to [Solid](https://solidproject.org/) for Android. One app holds the user's pod
accounts; every other app on the device reaches those pods through it, with the user's permission
and without ever handling a credential.

📖 **[Full documentation](https://androidsolidservices.erfangholami.com)** — guides, API reference
and troubleshooting.

## The problem

Solid gives people their own data, but on Android every app that wants to use a pod has to become
an identity client: run the OIDC flow, mint and rotate DPoP tokens, store them safely, and repeat
all of it in the next app. The user signs in again in each one, and each app's mistakes are theirs
alone to make.

## How this solves it

Android Solid Services owns the login. Tokens are DPoP-bound to keys generated in the Android
Keystore and never leave the app; other apps talk to it over AIDL and get results, never
credentials. The user signs in once, grants each app access explicitly, and can revoke it at any
time — and an app integrates with one dependency and no auth code at all.

|![Login](https://github.com/user-attachments/assets/9afe2f9d-f4a3-4e05-ae77-ab6e13febf84)|![Accounts](https://github.com/user-attachments/assets/543b2d9e-2f51-481f-b50d-934ece61172f)|![Access grants](https://github.com/user-attachments/assets/3deabd3a-d907-407a-9fbb-b27e26882206)|![Settings](https://github.com/user-attachments/assets/b6df9725-321d-4572-b9fa-07cf28de3e9a)|
|-|-|-|-|

## Features

- **One sign-in, many apps** — several accounts from different pod providers, active at once.
- **Native account picker** — sign-in launches from the calling app's own foreground, so no
  special permissions are involved.
- **Solid accounts in Android Settings**, alongside every other account on the device.
- **Full pod access over IPC** — resources (CRUD, containers, patches, streaming), sharing,
  Linked Data Notifications, and data modules for contacts and tickets.
- **Per-app grants** the user reviews and revokes.
- **Two libraries** — `client` for apps that go through Android Solid Services, `api` for apps
  that prefer to speak to pods directly.

## Install

The app is on [GitHub Releases](https://github.com/erfangholami/Android-Solid-Services/releases);
Google Play and F-Droid are in progress.

For your own app, one dependency:

```kotlin
implementation("com.erfangholami.androidsolidservices:client:0.7.2")
```

Then follow **[Getting Started](https://androidsolidservices.erfangholami.com/getting-started/)**.
There is also a [sample app](https://github.com/erfangholami/Android-Solid-Service_client-sample)
that runs every SDK call against a live pod, shown next to the code that makes it.

## Build

Requires **JDK 17** (or JetBrains Runtime 17.0.9); set `JAVA_HOME` if the build complains.

```sh
./gradlew assembleFossDebug     # the app, without Google services
./gradlew test                  # unit tests, all modules
./gradlew spotlessApply detekt  # format, then static analysis
```

The APK lands in `app/build/outputs/apk/foss/debug`. Versions come from the git tag, so a working
copy needs no version edits. The
[Architecture](https://androidsolidservices.erfangholami.com/architecture/) page explains how the
modules fit together.

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
