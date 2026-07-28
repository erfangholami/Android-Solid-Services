package com.erfangholami.androidsolidservices.api.auth.implementation

import net.openid.appauth.AuthState
import java.security.MessageDigest

internal fun deepCopyAuthState(authState: AuthState): AuthState =
    AuthState.jsonDeserialize(authState.jsonSerializeString())

internal fun tokenFp(token: String?): String {
    if (token == null) return "none"
    val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(8)
}
