package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Base64
import com.erfangholami.androidsolidservices.shared.model.profile.UserInfo
import org.json.JSONObject

/**
 * Reads claims from an OIDC ID token's payload segment.
 *
 * This decodes the (already-trusted) token body only — signature verification is the job of
 * [IdTokenVerifier]. Kept separate from the auth flow so the claim parsing is independently testable.
 */
internal object IdTokenClaims {

    /** The caller's WebID: the `webid` claim, falling back to `sub`. */
    fun webId(idToken: String): String =
        try {
            val claims = payload(idToken)
            claims.optString("webid").takeIf { it.isNotEmpty() } ?: claims.getString("sub")
        } catch (ex: Exception) {
            throw IllegalStateException("Unable to parse ID token", ex)
        }

    /** The WebID wrapped as [UserInfo]. */
    fun userInfo(idToken: String): UserInfo = UserInfo(webId(idToken))

    /** The token issuer (`iss`), or `null` if absent or unparseable. */
    fun issuer(idToken: String): String? =
        try {
            payload(idToken).optString("iss").takeIf { it.isNotEmpty() }
        } catch (ex: Exception) {
            null
        }

    private fun payload(idToken: String): JSONObject {
        val decoded = Base64.decode(idToken.split(".")[1], Base64.URL_SAFE or Base64.NO_PADDING)
        return JSONObject(String(decoded))
    }
}
