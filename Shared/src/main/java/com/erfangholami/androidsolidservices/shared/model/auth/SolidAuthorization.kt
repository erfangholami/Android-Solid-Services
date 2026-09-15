package com.erfangholami.androidsolidservices.shared.model.auth

/**
 * The Intent protocol between the client SDK's authorization contract and the host's consent
 * activity, which the SDK reaches through
 * [com.erfangholami.androidsolidservices.shared.host.SolidHostContract.ACTION_AUTHORIZE].
 *
 * The activity is launched **for result** from the calling app's own foreground — that is the
 * point of the flow: no UI is ever drawn from a background service, so no overlay permission is
 * involved, and `getCallingPackage()` gives the consent screen a caller identity it can trust.
 *
 * In: [EXTRA_ACCESS_REQUEST], an
 * [com.erfangholami.androidsolidservices.shared.model.grant.AccessRequest]; when absent the host
 * starts from `AccessRequest.DEFAULT`.
 *
 * Results:
 *  - `RESULT_OK` with [EXTRA_WEB_ID] — the account the user picked — and [EXTRA_GRANT], the
 *    [com.erfangholami.androidsolidservices.shared.model.grant.AppGrant] the user approved, which
 *    may be narrower or wider than the request.
 *  - `RESULT_CANCELED` — the user dismissed without granting.
 *  - [RESULT_ERROR] with [EXTRA_ERROR_CODE] / [EXTRA_ERROR_MESSAGE] — the flow could not run;
 *    the code is an `ExceptionsErrorCode` value the SDK maps to its typed exceptions.
 */
public object SolidAuthorization {

    public const val EXTRA_WEB_ID: String =
        "com.erfangholami.androidsolidservices.extra.WEB_ID"

    public const val EXTRA_ACCESS_REQUEST: String =
        "com.erfangholami.androidsolidservices.extra.ACCESS_REQUEST"

    public const val EXTRA_GRANT: String =
        "com.erfangholami.androidsolidservices.extra.GRANT"

    public const val EXTRA_ERROR_CODE: String =
        "com.erfangholami.androidsolidservices.extra.ERROR_CODE"

    public const val EXTRA_ERROR_MESSAGE: String =
        "com.erfangholami.androidsolidservices.extra.ERROR_MESSAGE"

    /** `Activity.RESULT_FIRST_USER` — distinct from both OK and CANCELED. */
    public const val RESULT_ERROR: Int = 1
}
