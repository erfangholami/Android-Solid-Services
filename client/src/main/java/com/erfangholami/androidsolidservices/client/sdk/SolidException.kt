package com.erfangholami.androidsolidservices.client.sdk

import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidNotLoggedInException
import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidResourceException
import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidServicesDrawPermissionDeniedException
import com.erfangholami.androidsolidservices.client.sdk.SolidException.SolidSharingException
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode

/**
 * Base type for every error surfaced by the client SDK.
 *
 * An SDK call either returns its result or fails with a subtype of this sealed
 * hierarchy: connection/installation problems ([SolidAppNotFoundException],
 * [SolidServiceConnectionException], [SolidNotLoggedInException]), resource
 * errors ([SolidResourceException]), and sharing/notification errors
 * ([SolidSharingException]).
 */
public sealed class SolidException(message: String) : Exception(message) {
    public class SolidServicesDrawPermissionDeniedException(message: String = "Android Solid Services doesn't have permission to draw overlay.") :
        SolidException(message)

    public class SolidServiceConnectionException(message: String = "Unable to connect to Android Solid Services.") :
        SolidException(message)

    public class SolidAppNotFoundException(message: String = "Android Solid Services has not been installed.") :
        SolidException(message)

    public class SolidNotLoggedInException(message: String = "User has not logged in.") :
        SolidException(message)

    public sealed class SolidResourceException(message: String) : SolidException(message) {
        public class NotSupportedClassException(message: String) : SolidResourceException(message)
        public class NotPermissionException(message: String) : SolidResourceException(message)
        public class NullWebIdException(message: String = "WebID is missing!") :
            SolidResourceException(message)

        public class UnknownException(message: String) : SolidResourceException(message)
    }

    /**
     * Errors raised by the sharing and notification operations.
     *
     * UI hints, in order of variant:
     *  - [AccessDeniedException] — prompt the user to send an access request.
     *  - [NoInboxException] — receiver isn't reachable via push; tell the user.
     *  - [InboxUnauthorizedException] — sender lacks auth on the receiver's inbox.
     *  - [InboxForbiddenException] — receiver's inbox ACL forbids the post.
     *  - [NotificationDeliveryException] — transient delivery problem; retry.
     *  - [ImpersonationDetectedException] — surfaces an inbox spam attempt.
     *  - [StaleAclException] — concurrent ACL write; the caller should retry.
     *  - [UnsupportedAuthBackendException] — server uses an auth backend the
     *    library can't drive yet (today: pure-ACP).
     */
    public sealed class SolidSharingException(message: String) : SolidException(message) {
        public class AccessDeniedException(message: String) : SolidSharingException(message)
        public class NoInboxException(message: String) : SolidSharingException(message)
        public class InboxUnauthorizedException(message: String) : SolidSharingException(message)
        public class InboxForbiddenException(message: String) : SolidSharingException(message)
        public class NotificationDeliveryException(message: String) : SolidSharingException(message)
        public class ImpersonationDetectedException(message: String) :
            SolidSharingException(message)

        public class StaleAclException(message: String) : SolidSharingException(message)
        public class UnsupportedAuthBackendException(message: String) :
            SolidSharingException(message)
    }
}

internal fun handleSolidException(errorCode: Int, errorMessage: String): SolidException {
    return when (errorCode) {
        ExceptionsErrorCode.DRAW_OVERLAY_NOT_PERMITTED -> SolidServicesDrawPermissionDeniedException(
            errorMessage
        )

        ExceptionsErrorCode.SOLID_NOT_LOGGED_IN -> SolidNotLoggedInException(errorMessage)
        ExceptionsErrorCode.NOT_SUPPORTED_CLASS -> SolidResourceException.NotSupportedClassException(
            errorMessage
        )

        ExceptionsErrorCode.NOT_PERMISSION -> SolidResourceException.NotPermissionException(
            errorMessage
        )

        ExceptionsErrorCode.NULL_WEBID -> SolidResourceException.NullWebIdException(errorMessage)

        ExceptionsErrorCode.ACCESS_DENIED ->
            SolidSharingException.AccessDeniedException(errorMessage)

        ExceptionsErrorCode.NO_INBOX ->
            SolidSharingException.NoInboxException(errorMessage)

        ExceptionsErrorCode.INBOX_UNAUTHORIZED ->
            SolidSharingException.InboxUnauthorizedException(errorMessage)

        ExceptionsErrorCode.INBOX_FORBIDDEN ->
            SolidSharingException.InboxForbiddenException(errorMessage)

        ExceptionsErrorCode.NOTIFICATION_DELIVERY_FAILED ->
            SolidSharingException.NotificationDeliveryException(errorMessage)

        ExceptionsErrorCode.IMPERSONATION_DETECTED ->
            SolidSharingException.ImpersonationDetectedException(errorMessage)

        ExceptionsErrorCode.STALE_ACL ->
            SolidSharingException.StaleAclException(errorMessage)

        ExceptionsErrorCode.UNSUPPORTED_AUTH_BACKEND ->
            SolidSharingException.UnsupportedAuthBackendException(errorMessage)

        else -> SolidResourceException.UnknownException(errorMessage)
    }
}
