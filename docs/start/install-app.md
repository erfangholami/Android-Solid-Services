---
title: Install Solid Share
description: Install Solid Share on your phone and sign in to your Solid pod, so every Solid-aware app can use it.
---

# Install Solid Share

[Solid Share](https://solidshare.app) holds your Solid accounts so every Solid-aware app on your
phone can use them. You install it once.

!!! info "For app developers"
    You need this installed to develop against the `client` library, because that library talks to
    it. If you are building with `api` instead, you do not — see
    [Client or API?](client-or-api.md).

!!! warning "Not the Android Solid Services app"
    The Android Solid Services app was the host until 0.7.2 and is discontinued. The 0.8.0 SDK
    never binds to it, because it cannot scope what an app may do. Install Solid Share instead; a
    device that carries only the old app fails every call with `SolidAppNotFoundException`.

## Get it

<div class="store-badges" markdown>
[![Get it on Google Play](../assets/badges/google-play.png)](https://play.google.com/store/apps/details?id=com.erfangholami.solidshare)
[![Get it on F-Droid](../assets/badges/f-droid.png)](https://f-droid.org/packages/com.erfangholami.solidshare/)
</div>

The APKs are also on
[GitHub Releases](https://github.com/erfangholami/SolidShare/releases), if you install them
yourself.

Android 8.0 (API 26) or newer.

In your own app, check before you launch sign-in:

```kotlin
if (!Solid.isHostInstalled(context)) {
    startActivity(Solid.hostInstallIntent(context))   // the store, or solidshare.app
}
```

## Sign in

Open Solid Share, tap **Add account** and pick your pod provider, or type its URL if it is not
listed. You are taken to your provider's own sign-in page — your password is entered there, never
in Solid Share.

You can add several accounts from different providers and keep them all signed in. Apps ask for a
specific one by WebID.

??? question "Which provider do I use?"
    If you do not have a pod yet, [solidcommunity.net](https://solidcommunity.net) and
    [solidweb.org](https://solidweb.org) both hand out free ones. Inrupt's
    [PodSpaces](https://start.inrupt.com) is the commercial option. Any server implementing the
    Solid protocol works.

## Granting apps access

When another app first asks for your pod, Solid Share shows you which account it wants, what it
wants to do, and where. Nothing reaches your pod until you allow it.

An app asks for a **level** — View, Add, Edit or Full access — on the **whole pod**, on **specific
folders**, or on a **data module** such as your contacts. You can narrow what it asked for before
you approve it.

Review, narrow and revoke those grants at any time from the **Apps** tab on Solid Share's Share
page. Revoking is immediate: the app keeps running, but its calls start failing.

Your accounts also appear in Android's **Settings → Accounts**, alongside every other account on
the device. Removing one there signs it out here.

## What leaves your device

Your tokens and signing keys never do. They are generated in the Android Keystore, bound to this
device, and encrypted at rest. Other apps receive the *results* of pod calls, never a credential
they could reuse.

See Solid Share's [privacy policy](https://solidshare.app/privacy) for the full account, and
[Troubleshooting](../project/troubleshooting.md) if sign-in misbehaves.
