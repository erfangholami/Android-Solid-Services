package com.erfangholami.androidsolidservices.client.internal

internal const val ANDROID_SOLID_SERVICES_PACKAGE_NAME: String = "com.erfangholami.androidsolidservices"
internal const val ANDROID_SOLID_SERVICES_AUTH_SERVICE: String =
    "com.erfangholami.androidsolidservices.services.ASSAuthenticatorService"
internal const val ANDROID_SOLID_SERVICES_CRUD_SERVICE: String =
    "com.erfangholami.androidsolidservices.services.ASSResourceService"
internal const val ANDROID_SOLID_SERVICES_DATA_MODULES_SERVICE: String =
    "com.erfangholami.androidsolidservices.services.SolidDataModulesService"
internal const val ANDROID_SOLID_SERVICES_SHARING_SERVICE: String =
    "com.erfangholami.androidsolidservices.services.ASSSharingService"
internal const val ANDROID_SOLID_SERVICES_NOTIFICATIONS_SERVICE: String =
    "com.erfangholami.androidsolidservices.services.ASSNotificationsService"
internal const val ANDROID_SOLID_SERVICES_AUTHORIZE_ACTIVITY: String =
    "com.erfangholami.androidsolidservices.ui.AuthorizeActivity"

/**
 * Where the SDK looks for the bound services.
 *
 * Always [ANDROID_SOLID_SERVICES_PACKAGE_NAME] in production — nothing outside the instrumentation
 * runner writes to it. The tests repoint it at the test APK, which hosts fake services in their own
 * process, so the SDK's own call sites can be driven across a real binder without the ASS app being
 * installed. Internal, so the published API is unchanged.
 */
internal object SdkTarget {

    @Volatile
    var servicePackageName: String = ANDROID_SOLID_SERVICES_PACKAGE_NAME
}
