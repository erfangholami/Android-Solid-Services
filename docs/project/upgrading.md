---
title: Upgrade guide
description: The route from any older version to 0.8.0, for both the client and the api library.
---

# Upgrade guide

This page is the route. [Releases](releases.md) is the detail: each version there lists what
changed and why. Find the version you are on below, then work down the page — every section above
yours applies to you as well.

The project is pre-1.0 and unstable. Versions do not carry deprecated members: a removal happens in
the release that replaces it, and consumers update. See the
[contributing guide](contributing.md) for the policy.

!!! tip "Which library are you on?"
    `client` reaches the pod through Solid Share, so its upgrades also involve the host app.
    `api` holds the credentials itself and talks to the pod directly, so nothing about the host
    applies to it. [Client or API?](../start/client-or-api.md) explains the split.

## Build requirements

Every jump to 0.8.0 needs these, whichever library you use.

| Setting | Value | If you get it wrong |
|---|---|---|
| `compileSdk` | 37 | The build stops: *"requires libraries and applications that depend on it to compile against version 37 or later"* |
| `minSdk` | 26 | The manifest merger fails |
| `targetSdk` | yours | — |
| Java/Kotlin target | 17 or newer | Class-file version errors |

`compileSdk` decides which APIs you may call, not which devices you reach, so raising it costs you
no users.

If you use Hilt, or any other KSP or kapt processor that reads Kotlin metadata, update it too. Hilt
below 2.60 stops with *"Provided Metadata instance has version 2.4.0, while maximum supported
version is 2.3.0"* against classes built by Kotlin 2.3. Use Hilt 2.60.1 and `androidx.hilt` 1.4.0 or
newer.

## `client` and the host move together

The IPC contract is versioned by what is on both sides of the binder, not by your app alone. An app
and the host it talks to must be a matched pair.

| `client` | Host app | Works |
|---|---|---|
| ≤ 0.7.2 | Android Solid Services 0.7.2 | yes |
| ≤ 0.7.2 | Solid Share | no |
| 0.8.0 | Solid Share ≥ 0.5.0 | yes |
| 0.8.0 | Android Solid Services 0.7.2 | no — `SolidAppNotFoundException` |

!!! warning "The Android Solid Services app stopped at 0.7.2"
    That is its last release. From 0.8.0 the app that holds the accounts and hosts the services is
    [Solid Share](https://solidshare.app). Tell your users to install it. See
    [Install Solid Share](../start/install-app.md).

## From 0.7.x

One release to cross, and it is the largest one for a `client` app: the host changes and grants
gain a scope. `api` consumers cross it with a version bump and the build requirements above.

Follow the seven steps in [Migrating from 0.7](releases.md#migrating-from-07). In short:

- Ask for a scope. `AuthorizeWithSolid` takes an `AccessRequest`; ask for the least you need, and
  read what the user approved from `SolidSignInResult.Authorized.grant`.
- Handle a refusal. `NotPermissionException` now means "outside the granted scope", and its message
  names what the app holds and what the call needs.
- `getAccount` and `disconnectFromSolid` are `suspend`; `getInstance(context, hasInstalled…)` is
  `getInstance(context)`.
- `requestLogin` is gone, with the overlay-permission error that went with it.

[App access](../build/app-access.md) is the reference for what every verb requires.

## From 0.6.x

Add the 0.7.0 rewrite. It is source- **and** wire-breaking, so your app and the host had to be
updated as a pair even then.

- **Contacts is three role stores.** `contacts.books`, `contacts.contacts` and `contacts.groups`
  replace the flat surface. `NewContact` and `FullContact` are gone: build a `ContactData` with
  `contactData { }`, derive one with `buildUpon { }`, and read a `SolidContact` whose fields live
  under `.data`.
- **`ContactStore.update` replaces, it does not merge.** Properties absent from the snapshot are
  removed, so derive from the stored contact rather than building a fresh one.
- **`getAccount(webId)`** takes the WebID it asks about.
- **`ExceptionsErrorCode` moved** from `shared.error` to `shared.result`.
- **`SettingTypeIndex` lost its address-book helpers**, and `SharingManager.getShareDeepLink`
  changed signature.
- **Data modules allocate under `{storage}datamodule/`.** Existing pods are not relocated:
  discovery follows the type-index registration, so anything registered under the older root keeps
  working where it is.

## From 0.5.x

Add the 0.6.0 reshape of the result and URI model. Both are source-breaking and both touch every
call site that talks to the library.

- **One result type.** Every API returns `SolidResult<T>` carrying a typed `SolidError` with a
  machine-readable code, in place of six historical error idioms.
- **String IRIs replace `java.net.URI`** across the public API. Pass plain identifier strings and
  let the library encode at its own boundary; do not wrap a URL before a call.
- `Profile` became `SolidAccount`, tickets arrived behind a `TicketStore` facade, and
  `ContactStore.getAll` became `list`.

## From 0.4.x and earlier

Add the rename in 0.4.1. The artifacts and the packages both changed.

| Old coordinate | New coordinate |
|---|---|
| `com.pondersource.solidandroidclient:solidandroidclient` | `com.erfangholami.androidsolidservices:client` |
| `com.pondersource.solidandroidapi:solidandroidapi` | `com.erfangholami.androidsolidservices:api` |
| `com.pondersource.shared:shared` | `com.erfangholami.androidsolidservices:shared` |

Source packages moved the same way — `com.pondersource.solidandroidclient.*` becomes
`com.erfangholami.androidsolidservices.client.*`, and `com.pondersource.shared.*` becomes
`com.erfangholami.androidsolidservices.shared.*`. If you consume the libraries from Maven Central,
this changes your `import` lines only. If you build this project from source, the module folders
moved too (`SolidAndroidApi/` → `api/`, `SolidAndroidClient/` → `client/`).

A jump this long is best done in two commits: raise `compileSdk` and your annotation processors
first, which the old artifact still tolerates, then swap the coordinate and fix the call sites.
That keeps each commit buildable.

## What your users see

Grants do not carry across from the Android Solid Services app, because the old ones had no scope
to carry. Every user installs Solid Share, signs in, and approves your app again the first time it
asks. Plan for that first launch: check the host is there, explain what you need, and ask.

```kotlin
if (!Solid.isHostInstalled(context)) {
    startActivity(Solid.hostInstallIntent(context))   // the store, or solidshare.app
}
```

The Client ID Document at `client.jsonld` stays hosted, so an installed 0.7.2 app keeps signing in
until its users move.

## Checklist

- [ ] `compileSdk = 37`, `minSdk = 26`
- [ ] Annotation processors updated (Hilt 2.60.1+)
- [ ] Coordinate is `com.erfangholami.androidsolidservices:client` (or `api`, or `host`) at `0.8.0`
- [ ] `Solid.isHostInstalled` checked before sign-in
- [ ] `AuthorizeWithSolid` carries an `AccessRequest` for the least you need
- [ ] `NotPermissionException` handled as a scope refusal, not as a failure
- [ ] Suspend `getAccount` / `disconnectFromSolid` call sites updated
- [ ] Your release build tested against Solid Share from the store, not a local build

If something on this page does not match what you see,
[open an issue](https://github.com/erfangholami/Android-Solid-Services/issues).
