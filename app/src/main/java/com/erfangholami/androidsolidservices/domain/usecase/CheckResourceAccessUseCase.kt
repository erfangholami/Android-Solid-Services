package com.erfangholami.androidsolidservices.domain.usecase

import com.erfangholami.androidsolidservices.domain.repository.AccessGrantRepository
import com.erfangholami.androidsolidservices.domain.repository.AuthRepository
import com.erfangholami.androidsolidservices.domain.repository.ResourceAccessRepository
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import javax.inject.Inject

sealed interface AccessCheck {
    data object Allowed : AccessCheck
    data class Denied(val code: Int, val message: String) : AccessCheck
}

class CheckResourceAccessUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val accessGrantRepository: AccessGrantRepository,
    private val resourceAccessRepository: ResourceAccessRepository,
) {

    operator fun invoke(callerPackageName: String?, webId: String): AccessCheck {
        if (callerPackageName == null) {
            return AccessCheck.Denied(
                ExceptionsErrorCode.UNKNOWN,
                "Unable to resolve the calling package.",
            )
        }
        if (!authRepository.isUserAuthorized()) {
            return AccessCheck.Denied(
                ExceptionsErrorCode.SOLID_NOT_LOGGED_IN,
                "Solid app has not logged in.",
            )
        }
        if (!accessGrantRepository.hasAccessGrant(callerPackageName, webId)) {
            return AccessCheck.Denied(
                ExceptionsErrorCode.NOT_PERMISSION,
                "App is not authorized for this account.",
            )
        }
        if (!resourceAccessRepository.hasAccess(webId, callerPackageName)) {
            return AccessCheck.Denied(
                ExceptionsErrorCode.NOT_PERMISSION,
                "App does not have permission to access the resource.",
            )
        }
        return AccessCheck.Allowed
    }
}
