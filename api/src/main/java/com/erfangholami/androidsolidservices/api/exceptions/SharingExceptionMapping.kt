package com.erfangholami.androidsolidservices.api.exceptions

import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidError

/**
 * Maps a [SharingException] to its typed [SolidError] domain variant, preserving
 * the actionable payload (resource / inbox / actor). Any other throwable is
 * classified by [SolidError.fromThrowable]. Used at the sharing/notifications
 * error boundary so a thrown domain exception surfaces as a typed result.
 */
public fun Throwable.toSolidError(): SolidError = when (this) {
    is SharingException.AccessDenied -> SolidError.AccessDenied(resourceUri, ownerWebId)
    is SharingException.AccessIndeterminate -> SolidError.AccessIndeterminate(resourceUri)
    is SharingException.NoInbox -> SolidError.NoInbox(targetWebId)
    is SharingException.InboxUnauthorized -> SolidError.InboxUnauthorized(inboxUri)
    is SharingException.InboxForbidden -> SolidError.InboxForbidden(inboxUri)
    is SharingException.NotificationDelivery -> SolidError.NotificationDelivery(inboxUri, statusCode)
    is SharingException.ImpersonationDetected ->
        SolidError.ImpersonationDetected(notificationUri, claimedActor, actualOwner)
    is SharingException.StaleAcl -> SolidError.StaleAcl(aclUri)
    is SharingException.UnsupportedAuthBackend -> SolidError.UnsupportedAuthBackend(resourceUri, backend)
    else -> SolidError.fromThrowable(this)
}

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
