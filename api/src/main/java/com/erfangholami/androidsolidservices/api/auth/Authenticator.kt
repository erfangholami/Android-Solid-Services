package com.erfangholami.androidsolidservices.api.auth

import android.content.Context
import android.content.Intent
import com.erfangholami.androidsolidservices.api.auth.implementation.AuthenticatorImplementation
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages OpenID Connect authentication with Solid pods on behalf of one or more users.
 *
 * Each user is identified by their WebID. Multiple users can be signed in simultaneously;
 * the "active" user is the one whose session is currently selected.
 *
 * Obtain an instance via [Authenticator.getInstance].
 */
public interface Authenticator {

    public companion object {
        /**
         * Returns the application-scoped singleton [Authenticator].
         * @param context Any [Context]; the application context is used internally.
         */
        public fun getInstance(context: Context): Authenticator =
            AuthenticatorImplementation.getInstance(context)
    }

    /** Emits the currently active [SolidAccount], or `null` if no user is active. */
    public val activeProfileFlow: StateFlow<SolidAccount?>
    /** Emits the list of all currently signed-in accounts (excluding expired sessions). */
    public val loggedInProfilesFlow: StateFlow<List<SolidAccount>>
    /**
     * Emits accounts that were signed in but whose session has terminally expired — the provider
     * rejected the refresh token (e.g. `invalid_grant` after a server-side session timeout), so
     * they no longer appear in [loggedInProfilesFlow]. Their local state is retained: signing in
     * again with the same WebID restores the account in place. [SolidAccount.sessionError]
     * carries the recorded reason, and [SolidAccount.isAuthorized] is `false`.
     */
    public val expiredProfilesFlow: StateFlow<List<SolidAccount>>
    /** Emits `true` when at least one user is fully authorized. */
    public val isAuthorizedFlow: StateFlow<Boolean>
    /** Emits the WebID of the active user, or `null` if no user is active. */
    public val activeWebIdFlow: StateFlow<String?>

    /**
     * Builds an [Intent] that launches the Solid OIDC login flow.
     *
     * @param webId Optional WebID to pre-fill; pass `null` to let the user enter it.
     * @param oidcIssuer Optional OIDC issuer URL to pre-fill; pass `null` to discover from [webId].
     * @param appName The display name shown to the user on the authorization page. Ignored when
     *   [clientId] is supplied (the hosted Client ID Document provides the name instead).
     * @param redirectUri The URI the OIDC provider redirects to after authorization. When [clientId]
     *   is supplied it must appear in that document's `redirect_uris`.
     * @param clientId Optional Solid-OIDC Client Identifier: the HTTPS URL of a hosted
     *   [Client ID Document](https://solidproject.org/TR/oidc#clientids). When provided, that stable
     *   public-client identity is used directly and dynamic client registration is skipped; when
     *   `null`, a client is registered dynamically with the OpenID provider.
     * @return A pair of (intent, error): if successful, the intent is non-null; otherwise
     *   the error string describes what went wrong.
     */
    public suspend fun createAuthenticationIntent(
        webId: String? = null,
        oidcIssuer: String? = null,
        appName: String,
        redirectUri: String,
        clientId: String? = null,
    ): Pair<Intent?, String?>

    /**
     * Completes the authorization flow after the OIDC provider redirects back to the app.
     *
     * @param responseData The result [Intent] delivered to the redirect Activity (the `data` from
     *   its Activity Result / `onActivityResult` callback) — that is, the result of launching the
     *   [Intent] returned by [createAuthenticationIntent]. Pass `null` if the flow was cancelled.
     * @return The authorized WebID on success, or `null` if authorization was denied, cancelled, or
     *   could not be completed.
     */
    public suspend fun submitAuthorizationResponse(responseData: Intent?): String?

    /**
     * Returns an [Intent] that launches the Solid OIDC logout / session termination flow.
     *
     * @param webId The WebID of the user to sign out.
     * @param logoutRedirectUrl The URI the OIDC provider redirects to after logout.
     * @return A pair of (intent, error).
     */
    public suspend fun getTerminationSessionIntent(
        webId: String,
        logoutRedirectUrl: String,
    ): Pair<Intent?, String?>

    /** Returns `true` if at least one user is fully authorized. */
    public fun isUserAuthorized(): Boolean
    /** Returns all currently signed-in accounts. */
    public fun getAllLoggedInProfiles(): List<SolidAccount>
    /** Returns the account for [webId]. Throws if not found. */
    public fun getProfile(webId: String): SolidAccount
    /** Returns the currently active account. Throws if no user is active. */
    public fun getActiveProfile(): SolidAccount

    /**
     * Re-fetches the WebID profile document for [webId] from the pod and
     * persists the refreshed [com.erfangholami.androidsolidservices.shared.model.profile.WebId]
     * into the stored account, leaving its auth state and user info untouched.
     *
     * Use this after writing changes to a user's own WebID document (e.g. an
     * in-app profile edit) so the cached account — and every flow derived from
     * it ([activeProfileFlow], [loggedInProfilesFlow]) — reflects the new data
     * without requiring the user to sign out and back in.
     *
     * @param webId The WebID of an authorized, signed-in user.
     * @return The updated [SolidAccount].
     */
    public suspend fun reloadProfile(webId: String): SolidAccount

    /** Returns the WebID of the active user, or `null` if no user is active. */
    public suspend fun getActiveWebId(): String?
    /** Sets the active user to [webId]. */
    public suspend fun setActiveWebId(webId: String)
    /** Removes the profile for [webId] and signs that user out. */
    public suspend fun removeProfile(webId: String)
    /** Signs out all users and removes all stored profiles. */
    public suspend fun removeAllProfiles()
}
