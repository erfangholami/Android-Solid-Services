---
title: Build with it
description: Every capability the SDK gives you, one page each — what it's for, how to call it, and how it behaves on the pod.
---

# Build with it

One page per capability. Each opens with what you can build and the code to build it, and keeps
the pod-level detail folded away at the bottom for when something surprises you.

Every page shows its code under **Client** and **API** tabs. Pick one and the whole site follows —
see [Client or API?](../start/client-or-api.md) if you have not chosen yet.

<div class="grid cards" markdown>

-   :material-login-variant: **[Sign in & accounts](auth.md)**

    ---

    Getting a WebID to call with, staying signed in, and handling several accounts on one device.

-   :material-file-document-outline: **[Resources & containers](resources.md)**

    ---

    Read, write, patch, list, copy and stream. The verbs everything else is built on.

-   :material-account-box-outline: **[Contacts](contacts.md)**

    ---

    Address books, contacts and groups as vCard RDF — interoperable with other Solid contact apps.

-   :material-ticket-confirmation-outline: **[Tickets](tickets.md)**

    ---

    A wallet on the pod: passes, their artifacts and images.

-   :material-share-variant-outline: **[Sharing](sharing.md)**

    ---

    Give someone access to a resource, track what you have given and received, share by link.

-   :material-inbox-arrow-down-outline: **[Notifications](notifications.md)**

    ---

    The Linked Data Notifications inbox — offers, withdrawals and access requests.

-   :material-lock-outline: **[Access control](access-control.md)**

    ---

    What WAC and ACP actually enforce, and which one your pod server speaks.

-   :material-format-list-bulleted-type: **[Type index](type-index.md)**

    ---

    How data is found on a pod by type instead of by path.

-   :material-chart-line: **[Telemetry](telemetry.md)**

    ---

    The pluggable sink, what is reported, and what never is.

</div>

## Two things that apply everywhere

**Every call takes a `webId` first.** A device can hold several signed-in Solid identities at
once, so the WebID chooses which session signs the request. Keep the one you got at sign-in.

**Wait for the connection on the `client` path.** Each client exposes a `Flow<Boolean>` that
emits `true` once its bound service is connected. Collect it before your first call, or you will
race the binding.

```kotlin
resources.resourceServiceConnectionState().first { connected -> connected }
```

The `api` path has no such step — there is no service to bind to.

## Not finding it here?

- The generated [API reference](../api/index.html) has every signature.
- [Adding a data module](../project/adding-a-data-module.md) covers extending the library with a
  new collection type of your own.
- The [client sample app](https://github.com/erfangholami/Android-Solid-Service_client-sample)
  runs every call against a live pod beside the code that made it.
