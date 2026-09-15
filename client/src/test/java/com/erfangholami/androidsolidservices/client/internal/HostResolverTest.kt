package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.client.sdk.SolidException
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Every SDK entry point gates on this resolver, and it is the difference between a caller getting
 * a clear "install Solid Share" and a ten-second bind timeout. It must also tell apart a device
 * that still carries the retired host, whose owner needs a different sentence.
 */
@RunWith(RobolectricTestRunner::class)
class HostResolverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun install(packageName: String) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { this.packageName = packageName })
    }

    @After
    fun tearDown() {
        HostResolver.overrideHostPackage = null
        HostResolver.forceAbsent = false
    }

    @Test
    fun `no host installed resolves to nothing and names Solid Share`() {
        assertNull(HostResolver.installedHost(context))

        val thrown = runCatching { HostResolver.requireHost(context) }.exceptionOrNull()

        assertTrue(thrown is SolidException.SolidAppNotFoundException)
        assertEquals(HostResolver.MESSAGE_NO_HOST, thrown?.message)
    }

    @Test
    fun `Solid Share installed resolves to its package`() {
        install(SolidHostContract.HOST_PACKAGE_NAME)

        assertEquals(SolidHostContract.HOST_PACKAGE_NAME, HostResolver.installedHost(context)?.packageName)
        assertEquals(SolidHostContract.HOST_PACKAGE_NAME, HostResolver.requireHost(context).packageName)
    }

    @Test
    fun `the retired host alone is not a host, and the message says so`() {
        install(LEGACY_HOST_PACKAGE_NAME)

        assertNull(HostResolver.installedHost(context))
        assertTrue(HostResolver.deprecatedHostPresent(context))
        assertEquals(HostResolver.MESSAGE_DEPRECATED_HOST, HostResolver.missingHostMessage(context))
        assertTrue(HostResolver.MESSAGE_DEPRECATED_HOST.contains("Solid Share"))
    }

    @Test
    fun `a similarly named package does not count as installed`() {
        install("${SolidHostContract.HOST_PACKAGE_NAME}.clone")

        assertNull(HostResolver.installedHost(context))
    }

    @Test
    fun `the override points every lookup at another package`() {
        HostResolver.overrideHostPackage = "com.example.fakes"

        assertEquals("com.example.fakes", HostResolver.installedHost(context)?.packageName)
    }

    @Test
    fun `forcing absence hides an installed host and the retired one`() {
        install(SolidHostContract.HOST_PACKAGE_NAME)
        install(LEGACY_HOST_PACKAGE_NAME)
        HostResolver.forceAbsent = true

        assertNull(HostResolver.installedHost(context))
        assertEquals(HostResolver.MESSAGE_NO_HOST, HostResolver.missingHostMessage(context))
    }

    @Test
    fun `a host target builds package-qualified intents`() {
        val target = HostTarget("com.example.host")

        val service = target.serviceIntent(SolidHostContract.ACTION_RESOURCE_SERVICE)
        assertEquals(SolidHostContract.ACTION_RESOURCE_SERVICE, service.action)
        assertEquals("com.example.host", service.`package`)

        val authorize = target.authorizeIntent()
        assertEquals(SolidHostContract.ACTION_AUTHORIZE, authorize.action)
        assertEquals("com.example.host", authorize.`package`)
    }
}
