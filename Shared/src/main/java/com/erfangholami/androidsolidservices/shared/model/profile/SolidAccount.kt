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
 * @property isAuthorized Whether this account currently holds a usable session. `false` for an
 *   account whose session has terminally expired — it is surfaced via
 *   [com.erfangholami.androidsolidservices.api.auth.Authenticator.expiredProfilesFlow] and needs a
 *   fresh sign-in with the same WebID to be restored.
 * @property sessionError The OAuth error recorded when the session terminally expired (e.g.
 *   `invalid_grant: token expired`), or `null` while the session is healthy.
 */
public data class SolidAccount(
    val userInfo: UserInfo? = null,
    val webId: WebId? = null,
    val isAuthorized: Boolean = true,
    val sessionError: String? = null,
)
