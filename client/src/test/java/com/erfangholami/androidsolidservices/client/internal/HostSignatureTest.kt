package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.shared.host.SolidHostContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.security.MessageDigest

/**
 * The package name says which app to bind to; the signing key says whether it is the right one.
 * Without this check an app sideloaded under the host's name, on a device where the real host was
 * never installed, would be handed the user's pod calls.
 */
@RunWith(RobolectricTestRunner::class)
class HostSignatureTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Bytes whose SHA-256 is one of the digests the SDK ships. */
    private val trustedCertificate: ByteArray = byteArrayOf(1, 2, 3)

    private val impostorCertificate: ByteArray = byteArrayOf(9, 9, 9)

    private fun digestOf(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate)
            .joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun install(packageName: String, certificate: ByteArray?) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                if (certificate != null) signatures = arrayOf(Signature(certificate))
            },
        )
    }

    private fun releaseBuild() {
        context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    private fun debugBuild() {
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
    }

    @Before
    fun setUp() {
        releaseBuild()
        HostSignatures.acceptedOverride = setOf(digestOf(trustedCertificate))
    }

    @After
    fun tearDown() {
        HostSignatures.acceptedOverride = null
        HostResolver.overrideHostPackage = null
        HostResolver.forceAbsent = false
    }

    @Test
    fun `a host signed with an accepted key resolves`() {
        install(SolidHostContract.HOST_PACKAGE_NAME, trustedCertificate)

        assertTrue(HostSignatures.isTrusted(context, SolidHostContract.HOST_PACKAGE_NAME))
        assertEquals(SolidHostContract.HOST_PACKAGE_NAME, HostResolver.installedHost(context)?.packageName)
        assertFalse(HostResolver.impostorPresent(context))
    }

    @Test
    fun `an app holding the host's name but another key is refused, and named as such`() {
        install(SolidHostContract.HOST_PACKAGE_NAME, impostorCertificate)

        assertFalse(HostSignatures.isTrusted(context, SolidHostContract.HOST_PACKAGE_NAME))
        assertNull(HostResolver.installedHost(context))
        assertTrue(HostResolver.impostorPresent(context))
        assertEquals(HostResolver.MESSAGE_UNTRUSTED_HOST, HostResolver.missingHostMessage(context))
    }

    @Test
    fun `an unsigned package is refused`() {
        install(SolidHostContract.HOST_PACKAGE_NAME, certificate = null)

        assertFalse(HostSignatures.isTrusted(context, SolidHostContract.HOST_PACKAGE_NAME))
        assertNull(HostResolver.installedHost(context))
    }

    @Test
    fun `a debug build of the calling app skips the check, so a local host still works`() {
        install(SolidHostContract.HOST_PACKAGE_NAME, impostorCertificate)
        debugBuild()

        assertTrue(HostSignatures.isTrusted(context, SolidHostContract.HOST_PACKAGE_NAME))
        assertEquals(SolidHostContract.HOST_PACKAGE_NAME, HostResolver.installedHost(context)?.packageName)
    }

    @Test
    fun `no host at all still reads as not installed rather than untrusted`() {
        assertNull(HostResolver.installedHost(context))
        assertFalse(HostResolver.impostorPresent(context))
        assertEquals(HostResolver.MESSAGE_NO_HOST, HostResolver.missingHostMessage(context))
    }

    @Test
    fun `the test override bypasses the check, because the fakes carry the test APK's own key`() {
        HostResolver.overrideHostPackage = "com.example.fakes"

        assertEquals("com.example.fakes", HostResolver.installedHost(context)?.packageName)
        assertFalse(HostResolver.impostorPresent(context))
    }

    @Test
    fun `the shipped digests are lowercase hex of the right length`() {
        HostSignatures.acceptedOverride = null

        HostSignatures.ACCEPTED.forEach { digest ->
            assertEquals("a SHA-256 digest is 64 hex characters: $digest", 64, digest.length)
            assertTrue("digests are lowercase hex: $digest", digest.matches(Regex("[0-9a-f]{64}")))
        }
    }
}
