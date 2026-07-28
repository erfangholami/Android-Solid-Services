package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Authenticator
import net.openid.appauth.TokenResponse

internal interface AuthSession {

    suspend fun getLastTokenResponse(webId: String, forceRefresh: Boolean = false): TokenResponse?

    suspend fun getAuthHeaders(webId: String, httpMethod: String, uri: String): Map<String, String>

    fun updateDPoPNonce(webId: String, resourceUri: String, nonce: String)
}

internal fun Authenticator.asSession(): AuthSession = this as? AuthSession
    ?: error("Authenticator must be obtained from Authenticator.getInstance(context).")
