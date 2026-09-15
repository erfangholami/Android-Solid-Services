package com.erfangholami.androidsolidservices.client.sdk

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.client.internal.HOST_WEBSITE
import com.erfangholami.androidsolidservices.client.internal.HostResolver
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The two helpers an app calls before it can do anything else: is the host there, and if not, where to get it. */
@RunWith(RobolectricTestRunner::class)
class SolidHostHelpersTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        HostResolver.forceAbsent = false
    }

    @Test
    fun `the host package is Solid Share`() {
        assertEquals(SolidHostContract.HOST_PACKAGE_NAME, Solid.HOST_PACKAGE_NAME)
    }

    @Test
    fun `isHostInstalled follows the package manager`() {
        assertFalse(Solid.isHostInstalled(context))

        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = Solid.HOST_PACKAGE_NAME })

        assertTrue(Solid.isHostInstalled(context))
    }

    @Test
    fun `the install intent falls back to the website when no store can open a market link`() {
        val intent = Solid.hostInstallIntent(context)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(HOST_WEBSITE), intent.data)
    }

    @Test
    fun `the install intent prefers the store when one answers the market link`() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Solid.HOST_PACKAGE_NAME}"))
        shadowOf(context.packageManager).addResolveInfoForIntent(
            market,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.android.vending"
                    name = "Store"
                }
            },
        )

        val intent = Solid.hostInstallIntent(context)

        assertEquals("market", intent.data?.scheme)
        assertEquals(Solid.HOST_PACKAGE_NAME, intent.data?.getQueryParameter("id"))
    }
}
