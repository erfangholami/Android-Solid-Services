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

    /**
     * The `android.accounts` account type under which Android Solid Services registers every
     * signed-in Solid profile (account name = WebID). Third-party apps can offer the **system**
     * account chooser over it — `AccountManager.newChooseAccountIntent` filtered to this type,
     * or the SDK's `ChooseSolidAccount` contract. Picking an account there makes it visible to
     * the picking app, mediated by the OS; it does not by itself grant pod access, which is what
     * the authorize flow is for. Accounts of this type carry no tokens: DPoP tokens are bound to
     * keys that never leave Android Solid Services.
     */
    public const val ACCOUNT_TYPE: String = "com.erfangholami.androidsolidservices"

    public const val EXTRA_WEB_ID: String =
        "com.erfangholami.androidsolidservices.extra.WEB_ID"

    public const val EXTRA_ERROR_CODE: String =
        "com.erfangholami.androidsolidservices.extra.ERROR_CODE"

    public const val EXTRA_ERROR_MESSAGE: String =
        "com.erfangholami.androidsolidservices.extra.ERROR_MESSAGE"

    /** `Activity.RESULT_FIRST_USER` — distinct from both OK and CANCELED. */
    public const val RESULT_ERROR: Int = 1
}
