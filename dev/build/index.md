# Build with it

One page per capability. Each opens with what you can build and the code to build it, and keeps the pod-level detail folded away at the bottom for when something surprises you.

Every page shows its code under **Client** and **API** tabs. Pick one and the whole site follows — see [Client or API?](https://androidsolidservices.erfangholami.com/dev/start/client-or-api/index.md) if you have not chosen yet.

- **[Sign in & accounts](https://androidsolidservices.erfangholami.com/dev/build/auth/index.md)**

  ______________________________________________________________________

  Getting a WebID to call with, staying signed in, and handling several accounts on one device.

- **[Resources & containers](https://androidsolidservices.erfangholami.com/dev/build/resources/index.md)**

  ______________________________________________________________________

  Read, write, patch, list, copy and stream. The verbs everything else is built on.

- **[Contacts](https://androidsolidservices.erfangholami.com/dev/build/contacts/index.md)**

  ______________________________________________________________________

  Address books, contacts and groups as vCard RDF — interoperable with other Solid contact apps.

- **[Tickets](https://androidsolidservices.erfangholami.com/dev/build/tickets/index.md)**

  ______________________________________________________________________

  A wallet on the pod: passes, their artifacts and images.

- **[Sharing](https://androidsolidservices.erfangholami.com/dev/build/sharing/index.md)**

  ______________________________________________________________________

  Give someone access to a resource, track what you have given and received, share by link.

- **[Notifications](https://androidsolidservices.erfangholami.com/dev/build/notifications/index.md)**

  ______________________________________________________________________

  The Linked Data Notifications inbox — offers, withdrawals and access requests.

- **[Access control](https://androidsolidservices.erfangholami.com/dev/build/access-control/index.md)**

  ______________________________________________________________________

  What WAC and ACP actually enforce, and which one your pod server speaks.

- **[Type index](https://androidsolidservices.erfangholami.com/dev/build/type-index/index.md)**

  ______________________________________________________________________

  How data is found on a pod by type instead of by path.

- **[Telemetry](https://androidsolidservices.erfangholami.com/dev/build/telemetry/index.md)**

  ______________________________________________________________________

  The pluggable sink, what is reported, and what never is.

## Two things that apply everywhere

**Every call takes a `webId` first.** A device can hold several signed-in Solid identities at once, so the WebID chooses which session signs the request. Keep the one you got at sign-in.

**Wait for the connection on the `client` path.** Each client exposes a `Flow<Boolean>` that emits `true` once its bound service is connected. Collect it before your first call, or you will race the binding.

```kotlin
resources.resourceServiceConnectionState().first { connected -> connected }
```

The `api` path has no such step — there is no service to bind to.

## Not finding it here?

- The generated [API reference](https://androidsolidservices.erfangholami.com/dev/api/index.md) has every signature.
- [Adding a data module](https://androidsolidservices.erfangholami.com/dev/project/adding-a-data-module/index.md) covers extending the library with a new collection type of your own.
- The [client sample app](https://github.com/erfangholami/Android-Solid-Service_client-sample) runs every call against a live pod beside the code that made it.
