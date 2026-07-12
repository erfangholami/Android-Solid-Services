package com.erfangholami.androidsolidservices.api.auth.implementation

import net.openid.appauth.AuthState

/**
 * Returns an independent deep copy of [authState] via AppAuth's own JSON (de)serialization, so a
 * mutation (a token update, an authorization-response merge) can be applied to a throwaway copy and
 * only committed by writing the profile back — never mutating the live in-memory state in place.
 */
internal fun deepCopyAuthState(authState: AuthState): AuthState =
    AuthState.jsonDeserialize(authState.jsonSerializeString())
