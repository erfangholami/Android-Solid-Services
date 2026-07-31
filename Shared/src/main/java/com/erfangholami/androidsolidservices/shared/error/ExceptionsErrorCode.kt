package com.erfangholami.androidsolidservices.shared.error

/**
 * Integer error codes used to classify exceptions transported over the AIDL IPC boundary.
 *
 * The `client` SDK maps these codes back to typed `SolidException` subtypes so callers
 * receive a structured exception rather than a bare error code.
 */
public object ExceptionsErrorCode {

    /**
     * The deprecated `requestLogin` flow was called. It drew its picker over the calling app and
     * so needed the Android `SYSTEM_ALERT_WINDOW` permission, which the app no longer requests;
     * callers launch the `AuthorizeWithSolid` contract instead. The code is kept because installed
     * apps still map it.
     */
    public const val DRAW_OVERLAY_NOT_PERMITTED: Int = 1

    /** No Solid account is signed in for the requested WebID. */
    public const val SOLID_NOT_LOGGED_IN: Int = 2

    /** The requested resource class is not supported by this operation. */
    public const val NOT_SUPPORTED_CLASS: Int = 100

    /** The caller does not have permission to perform the requested operation. */
    public const val NOT_PERMISSION: Int = 101

    /** A non-null WebID is required but was not provided. */
    public const val NULL_WEBID: Int = 102

    /** An unexpected error occurred that does not map to a more specific code. */
    public const val UNKNOWN: Int = 103

    /** The target resource or inbox denied access (HTTP 403). */
    public const val ACCESS_DENIED: Int = 200

    /** The recipient's WebID profile does not advertise an LDN inbox. */
    public const val NO_INBOX: Int = 201

    /** The recipient's inbox requires authentication that could not be satisfied (HTTP 401). */
    public const val INBOX_UNAUTHORIZED: Int = 202

    /** The recipient's inbox rejected the request with 403 Forbidden. */
    public const val INBOX_FORBIDDEN: Int = 203

    /** The notification could not be delivered to the recipient's inbox. */
    public const val NOTIFICATION_DELIVERY_FAILED: Int = 204

    /** A WebID mismatch was detected — the DPoP token subject does not match the claimed identity. */
    public const val IMPERSONATION_DETECTED: Int = 205

    /** The ACL resource is out of date and must be re-read before writing. */
    public const val STALE_ACL: Int = 206

    /** The pod's access-control system (WAC or ACP) is not supported for this operation. */
    public const val UNSUPPORTED_AUTH_BACKEND: Int = 207
}
