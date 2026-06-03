package com.erfangholami.androidsolidservices.api.exceptions

import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode

/**
 * Maps a [SharingException] (or any [Throwable]) to the
 * [ExceptionsErrorCode] used for IPC transport. The bound services call
 * this when converting an exception result into the (code, message) pair
 * the AIDL callback expects.
 */
public fun Throwable.toSharingErrorCode(): Int = when (this) {
    is SharingException.AccessDenied -> ExceptionsErrorCode.ACCESS_DENIED
    is SharingException.NoInbox -> ExceptionsErrorCode.NO_INBOX
    is SharingException.InboxUnauthorized -> ExceptionsErrorCode.INBOX_UNAUTHORIZED
    is SharingException.InboxForbidden -> ExceptionsErrorCode.INBOX_FORBIDDEN
    is SharingException.NotificationDelivery -> ExceptionsErrorCode.NOTIFICATION_DELIVERY_FAILED
    is SharingException.ImpersonationDetected -> ExceptionsErrorCode.IMPERSONATION_DETECTED
    is SharingException.StaleAcl -> ExceptionsErrorCode.STALE_ACL
    is SharingException.UnsupportedAuthBackend -> ExceptionsErrorCode.UNSUPPORTED_AUTH_BACKEND
    else -> ExceptionsErrorCode.UNKNOWN
}
