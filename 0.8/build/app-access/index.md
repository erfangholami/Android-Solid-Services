# App access

On the `client` path the user decides what your app may do with their pod. They decide it on Solid Share's consent screen, from the request your app sends, and they can narrow or revoke it later. Every call is checked against what they approved.

The `api` path has none of this: your app holds the credentials, so it can do whatever the account can do.

Ask for the least you need

A request for the whole pod at Full access is easy to write and easy to refuse. A request for one folder at Edit tells the user what your app is for.

## The four levels

Each level includes the one before it.

| Level           | What it allows                                                                                 |
| --------------- | ---------------------------------------------------------------------------------------------- |
| **View**        | Read a resource, list a container, read a data module                                          |
| **Add**         | Create new members inside a container, without touching what is there                          |
| **Edit**        | Write, replace, patch and delete                                                               |
| **Full access** | Everything Edit allows, plus sharing the resource with other people and sending from the inbox |

`Add` exists because it is the level an upload feature needs. A photo picker that only ever adds files to one folder never needs to be able to change or delete what is already in it.

## The three targets

A grant is a set of entries, and each entry is a target at a level. Your request names targets the same way, except that paths are relative to the account's storage — your app does not know the pod root until after the user picks the account.

```kotlin
AccessRequest(
    level = AccessLevel.EDIT,
    targets = listOf(RequestedTarget.Pod),
)
```

Everything in the account's storage. This is the default when you pass no request, and it is what a file manager or a backup tool genuinely needs.

```kotlin
AccessRequest(
    level = AccessLevel.EDIT,
    targets = listOf(RequestedTarget.Path("notes/"), RequestedTarget.Path("notes-archive/")),
)
```

Storage-relative, resolved against whichever account the user picks. A path that ends in `/` is a container and covers everything under it; one that does not is a single resource.

```kotlin
AccessRequest(
    level = AccessLevel.ADD,
    targets = listOf(RequestedTarget.Module(DataModuleId.CONTACTS)),
)
```

Contacts or tickets, wherever the pod keeps them. The host resolves the module's containers from the type index, so a grant follows the data if it moves.

## Asking for it

The request travels with the sign-in contract, so the user sees it while they are choosing the account:

```kotlin
private val authorize = registerForActivityResult(
    AuthorizeWithSolid(
        AccessRequest(
            level = AccessLevel.EDIT,
            targets = listOf(RequestedTarget.Path("notes/")),
            reason = "Your notes are kept in your pod under notes/.",   // (1)!
        ),
    ),
) { result ->
    when (result) {
        is SolidSignInResult.Authorized -> onSignedIn(result.webId, result.grant)   // (2)!
        SolidSignInResult.Dismissed -> Unit
        is SolidSignInResult.Failed -> show(result.exception)
    }
}
```

1. One sentence, shown under the request. Say what the app does with the data, not that it needs permission.
1. What the user approved, which may be narrower or wider than what you asked for. Read it rather than assuming.

Pass no request and the app asks for the whole pod at Edit — the pre-0.8 behaviour, and the right default only for an app that really does work across the pod.

## Reading the grant back

The grant is live: the user can narrow it in Solid Share at any time, so read it rather than remembering what you asked for.

```kotlin
val account = Solid.getSignInClient(context).getAccount(webId) ?: return startSignIn()
val grant = account.grant

grant.podLevel()                            // the level on the whole pod, or null
grant.levelOnModule(DataModuleId.CONTACTS)  // the level on a data module, or null

grant.levelOn("https://alice.pod.example/notes/") { emptyList() }   // (1)!
```

1. The level on one resource, or `null`. The second argument answers where a data module lives on the pod, which only the host knows — pass `{ emptyList() }` from an app and the answer covers pod and resource entries, which is what an app's own check is about.

Use it to hide what the app cannot do, rather than letting the call fail. A feature that needs Full access is better greyed out with an explanation than shown and refused.

## What each verb needs

The check runs in the host, on every verb of every service.

| Verb                                                                                                           | Needs                                                  |
| -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| `read`, `readStream`, `head`, `exists`, `listContainer`, `readContainer`, `probeAccess`                        | **View** on the resource                               |
| `createInContainer`, `post`                                                                                    | **Add** on the container                               |
| `create`, `update`, `patch`, `putRaw`, `writeStream`, `delete`, `deleteContainer`, `ensureContainer`, `rename` | **Edit** on the resource                               |
| `copy`                                                                                                         | **View** on the source and **Edit** on the destination |
| `move`                                                                                                         | **Edit** on both                                       |
| `readPublic`, `headPublic`                                                                                     | nothing — these are unauthenticated reads              |
| `getWebId`                                                                                                     | **View** on anything the app holds                     |

| Verb                                                                                                                                      | Needs                  |
| ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------- |
| `list`, `get`, `findByWebId`, `getPhoto`, `getTicket`, `getTicketArtifact`                                                                | **View** on the module |
| `create`, `createTicket`                                                                                                                  | **Add** on the module  |
| `update`, `rename`, `delete`, `setPhoto`, `removePhoto`, `addMember`, `removeMember`, `updateTicket`, `deleteTicket`, `putTicketArtifact` | **Edit** on the module |

A verb that names a container explicitly is checked against that container as a resource too, so a module grant does not become a way to write anywhere.

| Verb                                                                                                       | Needs                            |
| ---------------------------------------------------------------------------------------------------------- | -------------------------------- |
| `createShare`, `updateShare`, `revokeShare`, `makePrivate`, `repairOwnerControl`, `purgeGivenShares`       | **Full access** on the resource  |
| the given / received indexes, the catalogue, `getAccessGrants`, `acceptShareRequest`, `rejectShareRequest` | **Full access** on the whole pod |

Sharing hands access to somebody else, which is why nothing below Full access reaches it.

| Verb                                                                                                                                | Needs                            |
| ----------------------------------------------------------------------------------------------------------------------------------- | -------------------------------- |
| `listNotifications`, `listRequests`, `ensureInbox`                                                                                  | **View** on the whole pod        |
| `deleteNotification`, `compactInbox`                                                                                                | **Edit** on the whole pod        |
| `sendOffer`, `sendUpdate`, `sendUndo`, `sendRequest`, `sendAccept`, `sendReject`, `recordDecisionGranted`, `recordDecisionRejected` | **Full access** on the whole pod |

The inbox belongs to the account rather than to any one resource, so these are pod-level checks. Sending speaks as the user, which is why it needs Full access.

## When a call is outside the grant

It fails with `NotPermissionException`, and the message names what the app holds and what the call needed:

```text
Granted VIEW on https://alice.pod.example/notes/; this call needs EDIT.
```

Treat it as "ask again", not as an error to report:

```kotlin
try {
    resources.update(webId, resource)
} catch (e: SolidException.SolidResourceException.NotPermissionException) {
    explainAndRelaunchAuthorize()   // with an AccessRequest for what the feature needs
}
```

Launching `AuthorizeWithSolid` again with a wider request is the supported way to widen a grant. The user sees what they already approved and what is now being asked for.

## Revoking

Either side can end it.

- **The user**, from the Apps tab in Solid Share. They can narrow a grant there too, one entry at a time.
- **Your app**, with `signIn.disconnectFromSolid(webId)`. It revokes your app's own grant and nothing else; the user stays signed in, and their other apps are untouched.

Revocation takes effect on the next call. Nothing is cached in your process, so there is no window in which a revoked app still works.

## Where a grant lives

On the device, in the host app, and nowhere else. A grant is not written to the pod.

That has one consequence you must design for: a grant does not follow the user to a second device. The same person, with the same WebID, on a tablet as well as a phone, approves your app on each of them. Treat "no grant yet" as a normal first-run state on every device, not as an error — check with `signIn.getAccount(webId)?.grant` and launch `AuthorizeWithSolid` when it is `null`.

Keeping grants off the pod is deliberate. A grant describes one app on one device, it is worth nothing to another device, and writing it to the pod would publish the list of apps a person uses to anyone who can read that container.
