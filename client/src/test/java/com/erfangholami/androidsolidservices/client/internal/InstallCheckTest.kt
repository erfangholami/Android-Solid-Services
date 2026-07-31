package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Every SDK entry point gates on this check, and it is the difference between a caller getting a
 * clear "install Android Solid Services" and a ten-second bind timeout. Its whole job is to turn
 * one checked exception into `false` without swallowing anything else.
 */
@RunWith(RobolectricTestRunner::class)
class InstallCheckTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `absent package reports false`() {
        assertFalse(hasInstalledAndroidSolidServices(context))
    }

    @Test
    fun `installed package reports true`() {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { packageName = ANDROID_SOLID_SERVICES_PACKAGE_NAME },
        )
        assertTrue(hasInstalledAndroidSolidServices(context))
    }

    @Test
    fun `a similarly named package does not count as installed`() {
        // Guards against a prefix or suffix match creeping in: a third-party package that merely
        // starts with the ASS package name must not satisfy the check.
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { packageName = "$ANDROID_SOLID_SERVICES_PACKAGE_NAME.clone" },
        )
        assertFalse(hasInstalledAndroidSolidServices(context))
    }

    @Test
    fun `the production target is the ASS application id`() {
        // SdkTarget is mutable so instrumented tests can redirect it; nothing else may.
        assertTrue(
            "SdkTarget was left pointing at ${SdkTarget.servicePackageName}",
            SdkTarget.servicePackageName == ANDROID_SOLID_SERVICES_PACKAGE_NAME,
        )
    }
}
