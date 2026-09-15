package com.erfangholami.androidsolidservices.shared.host

/**
 * The contract between the `client` SDK and the app that hosts the Solid services on a device.
 *
 * The host is Solid Share. It owns the signed-in accounts and the tokens, exports one bound
 * service per feature area, and shows the consent screen a third-party app launches for a
 * result. The SDK reaches each service with an explicit Intent that names [HOST_PACKAGE_NAME]
 * and one of the actions below, so the host is free to name and move its service classes.
 *
 * The Android Solid Services app stopped at 0.7.2 and does not implement this contract; the
 * SDK never binds to it.
 */
public object SolidHostContract {

    /** The `applicationId` of Solid Share, the one package the SDK binds to. */
    public const val HOST_PACKAGE_NAME: String = "com.erfangholami.solidshare"

    /**
     * The `android.accounts` account type under which the host registers every signed-in Solid
     * account (account name = WebID). The SDK's `ChooseSolidAccount` contract offers the system
     * account chooser over it. Picking an account there identifies it and makes it visible to the
     * picking app; it does not grant pod access, which is what [ACTION_AUTHORIZE] is for. Accounts
     * of this type carry no tokens.
     */
    public const val ACCOUNT_TYPE: String = "com.erfangholami.solidshare"

    /** The bound service implementing `IASSAuthenticatorService`. */
    public const val ACTION_AUTHENTICATOR_SERVICE: String =
        "com.erfangholami.androidsolidservices.action.AUTHENTICATOR"

    /** The bound service implementing `IASSResourceService`. */
    public const val ACTION_RESOURCE_SERVICE: String =
        "com.erfangholami.androidsolidservices.action.RESOURCES"

    /** The bound service implementing `IASSDataModulesService`. */
    public const val ACTION_DATA_MODULES_SERVICE: String =
        "com.erfangholami.androidsolidservices.action.DATA_MODULES"

    /** The bound service implementing `IASSharingService`. */
    public const val ACTION_SHARING_SERVICE: String =
        "com.erfangholami.androidsolidservices.action.SHARING"

    /** The bound service implementing `IASSNotificationsService`. */
    public const val ACTION_NOTIFICATIONS_SERVICE: String =
        "com.erfangholami.androidsolidservices.action.NOTIFICATIONS"

    /**
     * The consent activity, launched for a result with the extras named by
     * [com.erfangholami.androidsolidservices.shared.model.auth.SolidAuthorization]. The host
     * declares it with the `DEFAULT` category, because an Intent that carries a package but no
     * component is resolved through filters.
     */
    public const val ACTION_AUTHORIZE: String =
        "com.erfangholami.androidsolidservices.action.AUTHORIZE"
}
