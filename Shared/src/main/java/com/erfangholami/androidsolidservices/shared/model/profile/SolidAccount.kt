package com.erfangholami.androidsolidservices.shared.model.profile

/**
 * A signed-in Solid account, as seen by SDK consumers.
 *
 * This is the AppAuth-free public view of a logged-in user: its OIDC [userInfo] and parsed
 * WebID profile document ([webId]). The OAuth/DPoP token state that authenticates pod requests
 * is deliberately **not** exposed here — that plumbing is the library's responsibility and stays
 * on the internal `Profile`. Consumers receive [SolidAccount] from [com.erfangholami.androidsolidservices.api.auth.Authenticator]
 * and never handle access tokens or `AuthState`.
 *
 * @property userInfo The OIDC `userinfo` response for this account, or `null` if not yet fetched.
 * @property webId    The parsed WebID profile document, or `null` if not yet fetched.
 */
public data class SolidAccount(
    val userInfo: UserInfo? = null,
    val webId: WebId? = null,
)
