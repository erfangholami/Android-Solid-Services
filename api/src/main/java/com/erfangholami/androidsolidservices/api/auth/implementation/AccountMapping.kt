package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.api.auth.Profile
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount

internal fun Profile.toAccount(): SolidAccount = SolidAccount(
    userInfo = userInfo,
    webId = webId,
    isAuthorized = authState.isAuthorized,
    sessionError = authState.authorizationException?.let { ex ->
        listOfNotNull(ex.error, ex.errorDescription).joinToString(": ").ifEmpty { null }
    },
    hasRefreshToken = authState.refreshToken != null,
)
