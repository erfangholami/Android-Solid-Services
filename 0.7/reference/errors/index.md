# Error codes

How failure is reported depends on which library you use:

Calls **throw** a subtype of `SolidException`. The hierarchy is sealed, so a `when` over it is exhaustive.

Calls return `SolidResult<T>`. Unwrap with `getOrThrow()` to get the same exceptions, or match on `SolidResult.Failure` to handle them without throwing.

## The hierarchy

```text
SolidException
├── SolidAppNotFoundException                    — Android Solid Services is not installed
├── SolidServiceConnectionException              — the IPC binding failed or dropped
├── SolidNotLoggedInException                    — no usable session for that WebID
├── SolidServicesDrawPermissionDeniedException   — legacy; only the removed requestLogin path
├── SolidResourceException
│   ├── NotSupportedClassException               — class does not extend RDFResource/NonRDFResource
│   ├── NotPermissionException                   — your app has no grant for this account
│   ├── NullWebIdException                       — no WebID supplied for the call
│   └── UnknownException                         — unexpected server or protocol error
└── SolidSharingException
    ├── AccessDeniedException                    — the pod refused the access change
    ├── NoInboxException                         — the receiver advertises no inbox
    ├── InboxUnauthorizedException               — their inbox needs credentials you do not have
    ├── InboxForbiddenException                  — their inbox refused your post
    ├── NotificationDeliveryException            — the notification could not be delivered
    ├── ImpersonationDetectedException           — a notification claimed a sender it cannot prove
    ├── StaleAclException                        — the ACL changed under a conditional write
    └── UnsupportedAuthBackendException          — the server speaks neither WAC nor ACP
```

## What to do about each

### Setup and connection

| Exception                         | Cause                                                                                                                                                                              | Fix                                                                                                                                  |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `SolidAppNotFoundException`       | the host app is not on the device                                                                                                                                                  | prompt to [install it](https://androidsolidservices.erfangholami.com/0.7/start/install-app/index.md), or switch to the `api` library |
| `SolidServiceConnectionException` | the binding failed, or the host app was stopped or updated                                                                                                                         | the connector rebinds itself; collect the connection-state flow and retry once it emits `true`                                       |
| `SolidNotLoggedInException`       | no usable session for that WebID — signed out, removed in Settings, or expired; the request is refused before it reaches the network (`api` returns `SolidError.NotAuthenticated`) | send the user back through sign-in                                                                                                   |
| `NullWebIdException`              | a call was made with no WebID                                                                                                                                                      | keep the WebID you got at sign-in and pass it to every call                                                                          |

### Permission

| Exception                 | Cause                                                    | Fix                                                                                                                 |
| ------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `NotPermissionException`  | the user has not granted your app access to this account | ask again; the grant dialog is shown by the host app, and the user can revoke at any time                           |
| `AccessDeniedException`   | the pod refused the access change you asked for          | usually you are not the owner, or the server enforces a policy you cannot override                                  |
| A bare `403` from a write | signed in, but without write access there                | see [Sharing](https://androidsolidservices.erfangholami.com/0.7/build/sharing/index.md) — the owner has to grant it |

A failure is not a denial

`probeAccess` throws on anything indeterminate — a 401 blip, a 5xx, a dropped connection — rather than returning `Denied`. Treating those as "no access" hides controls the user is actually allowed to use.

### Writes and concurrency

| Symptom                        | Cause                                               | Fix                                                                  |
| ------------------------------ | --------------------------------------------------- | -------------------------------------------------------------------- |
| `412 Precondition Failed`      | someone else wrote between your read and your write | re-read, merge or ask the user, then write again                     |
| `StaleAclException`            | the ACL moved under a conditional access change     | re-read the current shares and reapply                               |
| `404` on a write               | the parent container does not exist                 | call `ensureContainer` first                                         |
| `TransactionTooLargeException` | a body over roughly 1 MB crossed Binder             | use `writeStream`/`readStream`, which pass a file descriptor instead |

### Sharing and the inbox

| Exception                                                | Cause                                                | Fix                                                              |
| -------------------------------------------------------- | ---------------------------------------------------- | ---------------------------------------------------------------- |
| `NoInboxException`                                       | the receiver's profile advertises no inbox           | the share still works; only the notification cannot be delivered |
| `InboxUnauthorizedException` / `InboxForbiddenException` | their inbox rejected the post                        | nothing to fix on your side — treat delivery as best-effort      |
| `NotificationDeliveryException`                          | delivery failed in transit                           | the access change already happened; retry the notification only  |
| `ImpersonationDetectedException`                         | an inbox message claimed a sender it could not prove | drop it — this is the anti-spoofing gate doing its job           |
| `UnsupportedAuthBackendException`                        | the server speaks neither WAC nor ACP                | sharing is not available against that server                     |

### Content and protocol

| Exception                            | Cause                                                         | Fix                                                                                                                                                   |
| ------------------------------------ | ------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NotSupportedClassException`         | the class passed to `read`/`create` extends neither base type | extend `RDFResource` or `NonRDFResource`                                                                                                              |
| `UnsupportedRdfContentTypeException` | the server answered Turtle                                    | reads negotiate for JSON-LD or N-Triples; see the notes under [Resources](https://androidsolidservices.erfangholami.com/0.7/build/resources/index.md) |
| `UnknownException`                   | anything unclassified                                         | check the message — it carries the server's response                                                                                                  |

## Server-specific behaviour

Some failures are a particular pod server rather than your code. The ones already handled for you, and the ones that are not, are in [Troubleshooting](https://androidsolidservices.erfangholami.com/0.7/project/troubleshooting/index.md).
