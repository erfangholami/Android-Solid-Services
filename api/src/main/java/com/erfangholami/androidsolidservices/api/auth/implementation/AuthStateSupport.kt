package com.erfangholami.androidsolidservices.api.auth.implementation

import net.openid.appauth.AuthState

internal fun deepCopyAuthState(authState: AuthState): AuthState =
    AuthState.jsonDeserialize(authState.jsonSerializeString())
