package com.erfangholami.androidsolidservices.api.auth.implementation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthWireConstantsTest {

    @Test
    fun webid_scope_is_lowercase() {
        assertEquals("webid", OidcConstants.AUTHORIZATION_REQUEST_SCOPE_WEBID)
        assertEquals("openid", OidcConstants.AUTHORIZATION_REQUEST_SCOPE_OPENID)
        assertEquals("offline_access", OidcConstants.AUTHORIZATION_REQUEST_SCOPE_OFFLINE_ACCESS)
    }

    @Test
    fun webId_is_read_from_the_lowercase_webid_claim() {
        val claims = JSONObject()
            .put("webid", "https://alice.example/profile/card#me")
            .put("sub", "opaque-subject-123")
        assertEquals("https://alice.example/profile/card#me", IdTokenClaims.webIdFrom(claims))
    }

    @Test
    fun webId_falls_back_to_sub_when_no_webid_claim() {
        val claims = JSONObject().put("sub", "https://bob.example/profile/card#me")
        assertEquals("https://bob.example/profile/card#me", IdTokenClaims.webIdFrom(claims))
    }

    @Test
    fun camelCase_webId_key_is_ignored_so_a_rename_cannot_resurface() {
        val claims = JSONObject()
            .put("webId", "https://wrong.example/#me")
            .put("sub", "https://right.example/#me")
        assertEquals("https://right.example/#me", IdTokenClaims.webIdFrom(claims))
    }
}
