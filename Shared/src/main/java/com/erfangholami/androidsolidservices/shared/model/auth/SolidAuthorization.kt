package com.erfangholami.androidsolidservices.shared.model.auth

/**
 * The Intent protocol between the client SDK's authorization contract and the Android Solid
 * Services authorize activity.
 *
 * The activity is launched **for result** from the calling app's own foreground — that is the
 * point of the flow: no UI is ever drawn from a background service, so no overlay permission is
 * involved, and `getCallingPackage()` gives the authorize screen a caller identity it can trust.
 *
 * Results:
 *  - `RESULT_OK` with [EXTRA_WEB_ID] — the user picked an account and the grant was recorded.
 *  - `RESULT_CANCELED` — the user dismissed without granting.
 *  - [RESULT_ERROR] with [EXTRA_ERROR_CODE] / [EXTRA_ERROR_MESSAGE] — the flow could not run;
 *    the code is an `ExceptionsErrorCode` value the SDK maps to its typed exceptions.
 */
public object SolidAuthorization {

    public const val EXTRA_WEB_ID: String =
        "com.erfangholami.androidsolidservices.extra.WEB_ID"

    public const val EXTRA_ERROR_CODE: String =
        "com.erfangholami.androidsolidservices.extra.ERROR_CODE"

    public const val EXTRA_ERROR_MESSAGE: String =
        "com.erfangholami.androidsolidservices.extra.ERROR_MESSAGE"

    /** `Activity.RESULT_FIRST_USER` — distinct from both OK and CANCELED. */
    public const val RESULT_ERROR: Int = 1
}
