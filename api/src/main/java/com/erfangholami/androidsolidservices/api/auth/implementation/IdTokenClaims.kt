package com.erfangholami.androidsolidservices.api.auth.implementation

import android.util.Base64
import com.erfangholami.androidsolidservices.shared.model.profile.UserInfo
import org.json.JSONObject

internal object IdTokenClaims {

    fun webId(idToken: String): String =
        try {
            webIdFrom(payload(idToken))
        } catch (ex: Exception) {
            throw IllegalStateException("Unable to parse ID token", ex)
        }

    internal fun webIdFrom(claims: JSONObject): String =
        claims.optString("webid").takeIf { it.isNotEmpty() } ?: claims.getString("sub")

    fun userInfo(idToken: String): UserInfo = UserInfo(webId(idToken))

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
