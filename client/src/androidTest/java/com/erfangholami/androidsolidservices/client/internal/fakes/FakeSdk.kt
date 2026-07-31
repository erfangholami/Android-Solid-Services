package com.erfangholami.androidsolidservices.client.internal.fakes

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.erfangholami.androidsolidservices.client.internal.ANDROID_SOLID_SERVICES_PACKAGE_NAME
import com.erfangholami.androidsolidservices.client.internal.SdkTarget
import com.erfangholami.androidsolidservices.client.sdk.SolidContactsDataModule
import com.erfangholami.androidsolidservices.client.sdk.SolidNotificationsClient
import com.erfangholami.androidsolidservices.client.sdk.SolidResourceClient
import com.erfangholami.androidsolidservices.client.sdk.SolidSharingClient
import com.erfangholami.androidsolidservices.client.sdk.SolidSignInClient
import com.erfangholami.androidsolidservices.client.sdk.SolidTicketsDataModule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Points the SDK's own entry points at the fakes for the duration of a test.
 *
 * Without this, `Solid.getResourceClient(...)` binds to the installed ASS app — which is either
 * absent (every call times out) or real (the tests would write to a live pod). Redirecting the
 * package is enough because the fakes answer to the production service class names.
 *
 * The reset on both sides matters: the clients are process-wide singletons that capture their
 * binding at construction, so a stale instance would outlive the redirect and quietly reconnect
 * the next test class to nothing.
 */
class FakeSdk : TestWatcher() {

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    override fun starting(description: Description) {
        SdkTarget.servicePackageName = context.packageName
        resetClients()
        CallLog.clear(context)
    }

    override fun finished(description: Description) {
        resetClients()
        SdkTarget.servicePackageName = ANDROID_SOLID_SERVICES_PACKAGE_NAME
    }

    /** The last recorded call to [method], with the arguments the fake actually received. */
    internal fun recorded(method: String): CallLog.Entry = CallLog.await(context, method)

    private fun resetClients() {
        SolidSignInClient.resetForTests()
        SolidResourceClient.resetForTests()
        SolidContactsDataModule.resetForTests()
        SolidTicketsDataModule.resetForTests()
        SolidSharingClient.resetForTests()
        SolidNotificationsClient.resetForTests()
    }
}
