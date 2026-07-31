# Privacy Policy

_Last updated: 30th July 2026_

Android Solid Services (ASS) signs you in to [Solid](https://solidproject.org/) pods and lets other
apps on your device reach them on your behalf. This policy explains what the app does with your
data.

The short version: **your pod data never reaches us.** It travels between your device and the pod
provider you chose. We operate no servers and hold no copy of it.

## What we never collect

- **Pod contents** — contacts, resources, tickets, files or anything else stored in your pod.
- **Your WebID or pod address.**
- **Credentials.** Sign-in happens through your pod provider using Solid-OIDC. Your password is
  entered on their site, never in this app.
- **Contacts, location, photos, or files** from your device.

Access tokens are stored on your device only, encrypted with AES-256-GCM using a key held in the
Android Keystore. They are sent to your pod provider to authorise requests, and to no one else.

## What we do collect

Only in the **Google Play build**, and only in release form. See
[Builds that collect nothing](#builds-that-collect-nothing) below.

**Crash reports** — via Firebase Crashlytics, when the app crashes or handles an unexpected error:

- the stack trace and exception type
- device model, Android version, and app version
- a Firebase installation identifier
- the package name of the app whose request was being serviced, so a fault can be traced to the
  integration that triggered it

**Performance data** — via Firebase Performance Monitoring:

- app start-up and screen rendering times
- timings for network requests

Network timings record the **origin only** — `scheme://host[:port]`, for example
`https://pod.example`. A pod URL's path names your containers and resources, so paths and query
strings are removed before anything is reported, and Firebase's automatic network instrumentation
is switched off because it would capture complete URLs.

We use this to find crashes and slow paths. It is not used to profile you, and there is no
advertising or analytics SDK in the app — **Firebase Analytics is not included**.

## Builds that collect nothing

- **The F-Droid build** contains no Firebase or Google Play Services code at all. Nothing is
  collected, and there is nothing to switch off.
- **Debug builds** collect nothing.

If you would rather no diagnostics were sent, install the F-Droid build.

## Who else is involved

- **Your pod provider**, whom you choose. Your data lives with them, under their privacy policy.
- **Google**, as the processor for Crashlytics and Performance Monitoring in the Play build, under
  the [Firebase data processing terms](https://firebase.google.com/support/privacy). Retention of
  crash and performance data follows Firebase's own schedule.

We do not sell data, and we share none of it with anyone else.

## Your choices

- Uninstalling the app ends all collection.
- Signing out removes the stored tokens for that account.
- Installing the F-Droid build avoids diagnostics entirely.
- To ask what diagnostic data is associated with your installation, or to have it deleted, contact
  us below. Because reports carry no account identifier, we may need the Firebase installation ID
  from your device to locate them.

## Children

The app is not directed at children and we do not knowingly collect data from them.

## Changes

Material changes will be published on this page with a new date above, and noted in the release
notes.

## Contact

Open an issue at
[github.com/erfangholami/Android-Solid-Services](https://github.com/erfangholami/Android-Solid-Services/issues),
or email <erfangholami76@gmail.com>.
