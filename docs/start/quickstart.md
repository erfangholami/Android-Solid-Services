---
title: Quickstart
description: Sign a user in to their Solid pod and write your first resource, in about ten minutes.
---

# Quickstart

By the end of this page your app signs a user in to their Solid pod and writes a file to it.

If you have not picked a library yet, read [Client or API?](client-or-api.md) first — it takes a
minute and decides which tab you follow below. The choice sticks across every page on this site.

## 1. Before you start

=== "Client (via Android Solid Services)"

    Install [Android Solid Services](install-app.md) on your device or emulator and sign in to a
    pod. Your app talks to it, so it has to be there.

    No pod? [solidcommunity.net](https://solidcommunity.net) gives you one free.

=== "API (direct to the pod)"

    Nothing to install — but you need a pod to sign in to.
    [solidcommunity.net](https://solidcommunity.net) gives you one free.

## 2. Add the dependency

=== "Client (via Android Solid Services)"

    --8<-- "dependency-client.md"

=== "API (direct to the pod)"

    --8<-- "dependency-api.md"

    ```kotlin title="build.gradle.kts"
    android {
        defaultConfig {
            // The scheme your OIDC redirect comes back on.
            manifestPlaceholders["appAuthRedirectScheme"] = "com.example.yourapp"
        }
    }
    ```

Minimum SDK 26.

## 3. Sign in

=== "Client (via Android Solid Services)"

    Sign-in is an activity result. Your app launches the account picker from its own foreground,
    which is why no special permission is involved.

    ```{ .kotlin .annotate title="MainActivity.kt" }
    import androidx.activity.ComponentActivity
    import androidx.activity.result.contract.ActivityResultContracts
    import com.erfangholami.androidsolidservices.client.sdk.AuthorizeWithSolid
    import com.erfangholami.androidsolidservices.client.sdk.Solid
    import com.erfangholami.androidsolidservices.client.sdk.SolidSignInResult

    class MainActivity : ComponentActivity() {

        // Register the contract while the activity is being created — not in a click handler. (1)!
        private val authorize = registerForActivityResult(AuthorizeWithSolid()) { result ->
            when (result) {
                is SolidSignInResult.Authorized -> onSignedIn(result.webId)   // (2)!
                SolidSignInResult.Dismissed     -> showMessage("Sign-in cancelled")
                is SolidSignInResult.Failed     -> showError(result.exception)
            }
        }

        private fun startSignIn() = authorize.launch(Unit)
    }
    ```

    1. Android requires result contracts to be registered before the activity reaches `STARTED`.
       Registering later throws.
    2. Keep this WebID. Every pod call takes it as its first argument, which is how a device with
       several signed-in accounts routes your call to the right one.

=== "API (direct to the pod)"

    Your app runs the OIDC flow itself. Two steps: launch the intent, then hand the redirect back.

    ```{ .kotlin .annotate title="MainActivity.kt" }
    import androidx.activity.ComponentActivity
    import androidx.activity.result.contract.ActivityResultContracts
    import com.erfangholami.androidsolidservices.api.auth.Authenticator

    class MainActivity : ComponentActivity() {

        private val authenticator by lazy { Authenticator.getInstance(this) }

        private val login = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            lifecycleScope.launch {
                val webId = authenticator.submitAuthorizationResponse(result.data)  // (1)!
                if (webId != null) onSignedIn(webId) else showMessage("Sign-in cancelled")
            }
        }

        private fun startSignIn() = lifecycleScope.launch {
            val (intent, error) = authenticator.createAuthenticationIntent(
                oidcIssuer = "https://solidcommunity.net",
                appName = "Your App",
                redirectUri = "com.example.yourapp:/callback",
            )
            if (intent != null) login.launch(intent) else showMessage(error.orEmpty())
        }
    }
    ```

    1. This is where the authorization code is exchanged for DPoP-bound tokens, the ID token is
       validated, and the issuer is checked against the WebID. Returns the WebID, or `null` if the
       user backed out.

## 4. Find the pod root

A WebID document says where that person's storage lives, so you rarely hard-code a pod URL.

=== "Client (via Android Solid Services)"

    ```kotlin
    val resources = Solid.getResourceClient(context)

    val profile = resources.getWebId(webId)
    val storage = profile.getStorages().firstOrNull()
        ?: error("this WebID advertises no storage")
    ```

=== "API (direct to the pod)"

    The authenticator already parsed the profile during sign-in, so no extra request is needed.

    ```{ .kotlin .annotate }
    val resources = SolidResourceManager.getInstance(authenticator)   // (1)!

    val storage = authenticator.getProfile(webId).webId
        ?.getStorages()
        ?.firstOrNull()
        ?: error("this WebID advertises no storage")
    ```

    1. The manager is built from the authenticator, not from a `Context` — that is what gives it
       the tokens and DPoP keys to sign requests with.

## 5. Write something

=== "Client (via Android Solid Services)"

    ```{ .kotlin .annotate }
    val noteUri = "${storage}notes/hello.txt"

    resources.ensureContainer(webId, "${storage}notes/")        // (1)!
    resources.putRaw(
        webId = webId,
        uri = noteUri,
        contentType = "text/plain",
        body = "Hello from Android".toByteArray(),
    )
    ```

    1. Creates the container and any missing parents, bottom-up. Idempotent, so it is safe to call
       every time rather than checking first.

=== "API (direct to the pod)"

    ```kotlin
    val noteUri = "${storage}notes/hello.txt"

    resources.ensureContainer(webId, "${storage}notes/").getOrThrow()
    resources.putRaw(
        webId = webId,
        uri = noteUri,
        contentType = "text/plain",
        body = "Hello from Android".toByteArray(),
    ).getOrThrow()
    ```

## 6. Read it back

=== "Client (via Android Solid Services)"

    ```kotlin
    val text = resources.readStream(webId, noteUri).use { stream ->
        stream.stream().readBytes().decodeToString()
    }
    ```

=== "API (direct to the pod)"

    ```kotlin
    val text = resources.readStream(webId, noteUri).getOrThrow().use { stream ->
        stream.stream().readBytes().decodeToString()
    }
    ```

!!! warning "Close the stream"
    `readStream` hands back a live pipe. Use it inside `use { }` — leaking it holds a file
    descriptor open for the life of your process.

That is a full round trip: signed in, written, read back.

## Where next

<div class="grid cards" markdown>

-   :material-file-document-outline: **[Resources & containers](../build/resources.md)**

    ---

    The full set of verbs — conditional writes, patches, containers, copy and move, streaming
    large files.

-   :material-account-box-outline: **[Contacts](../build/contacts.md)**

    ---

    Address books, contacts and groups as standard vCard RDF, without writing any RDF.

-   :material-share-variant-outline: **[Sharing](../build/sharing.md)**

    ---

    Give another person access to a resource, and track what has been shared with you.

-   :material-cog-outline: **[How it works](how-it-works.md)**

    ---

    What happens between your call and the pod — the layers, the tokens, and the IPC hop.

</div>

!!! tip "A runnable tour"
    The [client sample app](https://github.com/erfangholami/Android-Solid-Service_client-sample)
    runs every SDK call against a live pod and shows each one beside the code that made it. It is
    the fastest way to see a call behave before you write it.
