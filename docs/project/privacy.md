# Privacy

_Last updated: 14th September 2026_

This page is about the **libraries** in this repository — `client`, `api`, `host` and `shared`.
They are compiled into other people's applications and are not an app you can install.

The short version: **the libraries collect nothing and transmit nothing on their own.** Pod data
travels between the device and the pod provider the user chose. This project operates no servers
and holds no copy of it.

!!! info "Looking for the app's privacy policy?"
    The host app is [Solid Share](https://solidshare.app), and it has its own policy at
    [solidshare.app/privacy](https://solidshare.app/privacy). The Android Solid Services app, which
    this page used to cover, was discontinued at 0.7.2.

## What the libraries never collect

- **Pod contents** — contacts, resources, tickets, files or anything else stored in a pod.
- **WebIDs or pod addresses.**
- **Credentials.** Sign-in happens through the user's pod provider using Solid-OIDC. The password
  is entered on the provider's site.
- **Contacts, location, photos, or files** from the device.

Access tokens stay on the device, encrypted with AES-256-GCM under a key held in the Android
Keystore. They are sent to the pod provider to authorise requests, and to no one else.

## Diagnostics are the host's decision

The libraries emit through `Telemetry`, a small interface in `shared.telemetry`. Until a host
application calls `Telemetry.install(...)`, the sink is a no-op and nothing is gathered, recorded
or sent. The libraries carry no monitoring dependency of any kind — no Crashlytics, no Analytics,
no third-party SDK.

If you are building an app on these libraries, whatever you install is yours: reports go to your
backend, under your privacy policy, and nothing reaches this project. [Telemetry](../build/telemetry.md)
covers what is emitted and how to install a sink.

Where a host does collect, two rules are built into what the libraries emit:

- **Network spans report the origin only** — `scheme://host[:port]`. A pod URL's path names the
  user's containers and resources, so paths and query strings never leave the process.
- **No WebIDs, tokens or refresh-token fingerprints** are ever attached as attributes.

## What an app grant means for privacy

On the `client` path, an app reaches the pod only within the grant its user approved: a level on
the whole pod, on named folders, or on a data module. The host checks every call against it, and
the user can narrow or revoke it at any time. See [App access](../build/app-access.md).

On the `api` path there is no such boundary — the app holds the credentials itself, and can do
whatever the account can do.

## Who else is involved

- **The user's pod provider**, whom they choose. Their data lives there, under that provider's
  privacy policy.
- **The host application**, whoever publishes it, for anything it collects.

This project sells no data and shares none.

## Contact

Open an issue at
[github.com/erfangholami/Android-Solid-Services](https://github.com/erfangholami/Android-Solid-Services/issues),
or email <erfangholami76@gmail.com>.
