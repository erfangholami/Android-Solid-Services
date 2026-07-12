package com.erfangholami.androidsolidservices.api.auth.implementation

import com.erfangholami.androidsolidservices.shared.model.profile.Profile
import com.erfangholami.androidsolidservices.shared.model.profile.SolidAccount

/**
 * Projects the internal [Profile] to the public, AppAuth-free [SolidAccount] — dropping the
 * OAuth/DPoP [Profile.authState] and the internal DPoP key id so no token plumbing crosses the
 * SDK boundary.
 */
internal fun Profile.toAccount(): SolidAccount = SolidAccount(userInfo = userInfo, webId = webId)
