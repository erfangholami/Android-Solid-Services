# Client or API?

There are two libraries, and picking between them is the only architectural decision this SDK asks of you. Everything else in these docs works the same way once you have chosen.

- **`client` — through Android Solid Services**

  ______________________________________________________________________

  Your app talks to the Android Solid Services app over IPC. That app owns the login, the tokens and the keys; yours receives results and never touches a credential.

  **Choose this** for almost every app.

- **`api` — straight to the pod**

  ______________________________________________________________________

  Your app is its own Solid client: it runs the OIDC flow, mints DPoP proofs, and stores the tokens itself.

  **Choose this** when your app must work without Android Solid Services installed.

## What actually differs

|                      | `client`                                | `api`                                            |
| -------------------- | --------------------------------------- | ------------------------------------------------ |
| Who holds the tokens | Android Solid Services                  | your app                                         |
| Sign-in UI           | the ASS account picker                  | your own, via AppAuth                            |
| User signs in        | once, for every Solid app on the device | once per app                                     |
| Needs ASS installed  | yes                                     | no                                               |
| Auth code you write  | none                                    | the OIDC and DPoP lifecycle                      |
| Call results         | `T?` — `null` when there is no result   | `SolidResult<T>` — `getOrThrow()`, `getOrNull()` |
| Failures             | throws `SolidException`                 | carried in `SolidResult`                         |

Beyond that the surfaces are deliberately the same. Contacts is `books`, `contacts` and `groups` in both; resources are the same verbs. Porting from one to the other is mostly a change of result handling, which is why every code example on this site is shown in both flavours.

The tabs remember your choice

Every capability page shows its code under **Client** and **API** tabs. Picking one applies to every page on this site for the rest of your visit, so you never read the wrong flavour by accident.

## The trade you are making

`client` exists because an Android device should ask its owner to sign in to Solid **once**, not once per app. It also means no app but Android Solid Services ever holds a refresh token, so a bug in your app cannot leak one. The cost is a dependency on another installed app.

`api` removes that dependency and gives you a self-contained app. The cost is that you now own the parts of Solid that are genuinely hard to get right: DPoP proofs, per-origin nonces, token refresh, keystore handling, and issuer validation against the WebID.

If you are unsure, start with `client`. Moving to `api` later changes your auth layer and your result handling, not your data code.

## Add the dependency

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:client:0.7.2")
}
```

`client` needs no manifest configuration: sign-in launches from your own activity through the `AuthorizeWithSolid` contract.

build.gradle.kts

```kotlin
dependencies {
    implementation("com.erfangholami.androidsolidservices:api:0.7.2")
}
```

`api` runs the browser leg of the OIDC flow through AppAuth, which needs a redirect scheme:

build.gradle.kts

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["appAuthRedirectScheme"] = "com.example.yourapp"
    }
}
```

Both are on Maven Central: [client](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/client) · [api](https://central.sonatype.com/artifact/com.erfangholami.androidsolidservices/api).

Next: **[Quickstart](https://androidsolidservices.erfangholami.com/0.7/start/quickstart/index.md)** signs a user in and writes to their pod.
