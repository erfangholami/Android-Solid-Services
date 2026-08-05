# Using a Solid-OIDC Client ID Document

By default the SDK registers a client **dynamically** with each OpenID Provider it talks to. That works with zero setup, but a dynamic registration can expire or be garbage-collected by the server — after which token refresh fails and the user is forced to sign in again.

Solid-OIDC offers a sturdier alternative: a **Client Identifier**. You host a small JSON-LD document (a *Client ID Document*) at a stable HTTPS URL, and that URL becomes your `client_id`. It never expires, requires no client secret, and lets the consent screen show your app's real name and logo.

This is **opt-in**. If you don't supply a `client_id`, nothing changes — the SDK keeps registering dynamically.

## 1. Write the document

Start from [`example-client-id.jsonld`](https://androidsolidservices.erfangholami.com/0.7/reference/example-client-id.jsonld) and fill in your own values:

```json
{
  "@context": ["https://www.w3.org/ns/solid/oidc-context.jsonld"],
  "client_id": "https://example.org/solid/client-id.jsonld",
  "client_name": "My Solid App",
  "redirect_uris": ["com.example.myapp:/oauth2redirect"],
  "scope": "openid webid offline_access",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "token_endpoint_auth_method": "none",
  "application_type": "native"
}
```

Rules that matter (the spec makes these MUST/normative — see [Solid-OIDC §Client Identifiers](https://solidproject.org/TR/oidc#clientids)):

| Field                        | Requirement                                                                                                |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `@context`                   | Must be exactly `https://www.w3.org/ns/solid/oidc-context.jsonld`.                                         |
| `client_id`                  | Must equal the HTTPS URL the document is hosted at.                                                        |
| `redirect_uris`              | Must contain the **exact** `redirectUri` you pass to the SDK, or the provider rejects the login.           |
| `grant_types`                | **Must include `refresh_token`** or you get no refresh token (and the re-login problem returns).           |
| `scope`                      | **Must include `offline_access`** for the same reason.                                                     |
| `token_endpoint_auth_method` | `none` — this is a public client; PKCE (plus DPoP when the provider supports it) replaces a client secret. |
| `application_type`           | `native` — signals custom-scheme/loopback redirects are expected.                                          |

Only the terms defined in the [Solid-OIDC context](https://www.w3.org/ns/solid/oidc-context.jsonld) survive JSON-LD processing; anything else is dropped without an error. `post_logout_redirect_uris` is **not** among them, so a logout redirect can't be declared this way — providers that need one still want a dynamic registration.

## 2. Host it

- Serve it at a **stable, public HTTPS URL** — that URL is your `client_id` forever, so don't change it.
- Serve it with **`Content-Type: application/ld+json`** (a spec MUST). Plain `application/json` or `text/plain` will be rejected by strict providers.
- Any static host works (your site, a CDN, an object store). GitHub Pages maps the `.jsonld` extension to `application/ld+json` on its own, which is how this project hosts [its own document](https://androidsolidservices.erfangholami.com/client.jsonld) — a live example you can copy from.

Whatever you host on, verify the header before you ship an app that depends on it:

```sh
curl -sI https://example.org/solid/client-id.jsonld | grep -i content-type
# content-type: application/ld+json
```

The document has to be reachable **before** the first login that names it: providers dereference `client_id` during the authorization request, so a URL that 404s fails every login.

## 3. Use it from the SDK

Pass the document URL as `clientId` when starting the login flow:

```kotlin
authenticator.createAuthenticationIntent(
    oidcIssuer = issuer,
    appName = "My Solid App",        // ignored when clientId is set; the document provides the name
    redirectUri = "com.example.myapp:/oauth2redirect",
    clientId = "https://example.org/solid/client-id.jsonld",
)
```

That's the only code change — token exchange and refresh use the hosted identity automatically.

## A note on native redirect URIs

The Solid-OIDC examples use `https` redirects (built for web apps). On Android you have two options:

- **Custom scheme** (`com.example.myapp:/...`, the AppAuth default) — simplest. The spec doesn't forbid it and `application_type: "native"` signals it, but **some providers reject non-`https` redirect URIs in a Client ID Document**, so test against your target servers (CSS, Inrupt).
- **HTTPS App Link** (`https://example.org/callback`) — the most broadly accepted, but you must also host `/.well-known/assetlinks.json` and verify the domain.

If a provider refuses your custom-scheme document, switch that provider to dynamic registration (omit `clientId`) or move to an App Link redirect.
