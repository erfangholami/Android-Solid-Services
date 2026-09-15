---
title: Client or API?
description: The one decision to make before you start — go through the Android Solid Services app, or talk to the pod directly.
---

# Client or API?

There are two libraries, and picking between them is the only architectural decision this SDK
asks of you. Everything else in these docs works the same way once you have chosen.

<div class="grid cards" markdown>

-   :material-account-key: **`client` — through Solid Share**

    ---

    Your app talks to Solid Share over IPC. That app owns the login, the tokens and the keys;
    yours receives results and never touches a credential, within a scope the user approved.

    **Choose this** for almost every app.

-   :material-server-network: **`api` — straight to the pod**

    ---

    Your app is its own Solid client: it runs the OIDC flow, mints DPoP proofs, and stores the
    tokens itself.

    **Choose this** when your app must work without Solid Share installed.

</div>

## What actually differs

| | `client` | `api` |
|---|---|---|
| Who holds the tokens | Solid Share | your app |
| Sign-in UI | Solid Share's consent screen | your own, via AppAuth |
| User signs in | once, for every Solid app on the device | once per app |
| Needs Solid Share installed | yes | no |
| What the app may do | what the user granted: a level on the whole pod, on folders, or on a data module | everything the account can do |
| Auth code you write | none | the OIDC and DPoP lifecycle |
| Call results | `T?` — `null` when there is no result | `SolidResult<T>` — `getOrThrow()`, `getOrNull()` |
| Failures | throws `SolidException` | carried in `SolidResult` |

Beyond that the surfaces are deliberately the same. Contacts is `books`, `contacts` and `groups`
in both; resources are the same verbs. Porting from one to the other is mostly a change of result
handling, which is why every code example on this site is shown in both flavours.

!!! tip "The tabs remember your choice"
    Every capability page shows its code under **Client** and **API** tabs. Picking one applies
    to every page on this site for the rest of your visit, so you never read the wrong flavour by
    accident.

## The trade you are making

??? question "And the third library, `host`?"
    `host` is for writing the app on the *other* end of `client` — the one that holds the
    accounts and serves everybody else. Solid Share is that app. You want this only if you are
    forking the ecosystem or working on Solid Share itself; see
    [Hosting the services](../build/hosting.md).

`client` exists because an Android device should ask its owner to sign in to Solid **once**, not
once per app. It also means no app but Solid Share ever holds a refresh token, so a bug in your
app cannot leak one, and the user decides how much of the pod each app may reach. The cost is a
dependency on another installed app.

`api` removes that dependency and gives you a self-contained app. The cost is that you now own
the parts of Solid that are genuinely hard to get right: DPoP proofs, per-origin nonces, token
refresh, keystore handling, and issuer validation against the WebID.

If you are unsure, start with `client`. Moving to `api` later changes your auth layer and your
result handling, not your data code.

## Add the dependency

=== "Client (via Solid Share)"

    --8<-- "dependency-client.md"

    `client` needs no manifest configuration: sign-in launches from your own activity through
    the `AuthorizeWithSolid` contract.

=== "API (direct to the pod)"

    --8<-- "dependency-api.md"

    `api` runs the browser leg of the OIDC flow through AppAuth, which needs a redirect scheme:

    ```kotlin title="build.gradle.kts"
    android {
        defaultConfig {
            manifestPlaceholders["appAuthRedirectScheme"] = "com.example.yourapp"
        }
    }
    ```

Both are on Maven Central:
[client](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client) ·
[api](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api).

Next: **[Quickstart](quickstart.md)** signs a user in and writes to their pod.
