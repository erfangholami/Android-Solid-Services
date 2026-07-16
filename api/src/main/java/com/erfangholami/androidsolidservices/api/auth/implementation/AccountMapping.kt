package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount

/**
 * Projects the internal [Profile] to the public, AppAuth-free [SolidAccount] — dropping the
 * OAuth/DPoP [Profile.authState] and the internal DPoP key id so no token plumbing crosses the
 * SDK boundary. Only the session's health survives the projection: whether it is still
 * authorized, and the OAuth error AppAuth recorded when a token refresh failed terminally.
 */
internal fun Profile.toAccount(): SolidAccount = SolidAccount(
    userInfo = userInfo,
    webId = webId,
    isAuthorized = authState.isAuthorized,
    sessionError = authState.authorizationException?.let { ex ->
        listOfNotNull(ex.error, ex.errorDescription).joinToString(": ").ifEmpty { null }
    },
)
