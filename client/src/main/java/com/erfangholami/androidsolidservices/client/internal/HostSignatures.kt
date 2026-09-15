package com.erfangholami.androidsolidservices.client.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Which signing keys a genuine host may carry.
 *
 * A package name is not an identity. Android will not let a second app claim
 * `com.erfangholami.solidshare` while the real one is installed, but on a device where it is
 * absent, an app sideloaded under that name would be bound to and handed the user's pod calls.
 * Checking the signing certificate closes that: the name says which app to look for, the key says
 * whether it is the right one.
 *
 * A digest is the SHA-256 of the signing certificate, lowercase hex, as
 * `apksigner verify --print-certs` prints it. Solid Share reaches users through Google Play and
 * F-Droid, and both carry the publisher's own key: F-Droid ships the reproducible build rather
 * than re-signing it, and Play App Signing holds that same key rather than one Google generated.
 * A set is kept rather than a single value so a key rotation, or a third channel, is one line.
 * A rotation also leaves the old certificate in the package's signing history, and an app
 * installed before it still presents that one, so the whole history counts as the same publisher.
 */
internal object HostSignatures {

    /** Solid Share's release key, on Google Play and on F-Droid alike. */
    private const val SOLID_SHARE_RELEASE = "6915b742f4022b0e1a0a05c0c5bac9639a260ef123953a4fdb0394bac06ff4d6"

    val ACCEPTED: Set<String> = setOf(SOLID_SHARE_RELEASE).filter { it.isNotBlank() }.toSet()

    /** The set the tests pin against, so they do not depend on the shipped digests. */
    @Volatile
    var acceptedOverride: Set<String>? = null

    private val accepted: Set<String> get() = acceptedOverride ?: ACCEPTED

    /**
     * Whether [packageName] is signed by one of [ACCEPTED].
     *
     * Verification is skipped for a debuggable caller, so a developer can run against a locally
     * built host. The flag is read from the *calling* app, which an attacker cannot set on
     * somebody else's release build, rather than from the host, which they could.
     */
    fun isTrusted(context: Context, packageName: String): Boolean {
        val trusted = accepted
        if (trusted.isEmpty()) return true
        if (isDebuggable(context)) return true
        return digestsOf(context.packageManager, packageName).any { it in trusted }
    }

    /** `true` when the app doing the checking is itself a debug build. */
    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun digestsOf(packageManager: PackageManager, packageName: String): List<String> =
        signaturesOf(packageManager, packageName).map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }

    private fun signaturesOf(packageManager: PackageManager, packageName: String): List<Signature> =
        modernSignatures(packageManager, packageName).ifEmpty { legacySignatures(packageManager, packageName) }

    private fun modernSignatures(packageManager: PackageManager, packageName: String): List<Signature> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
        return runCatching {
            val signing = packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo ?: return emptyList()
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners.orEmpty().toList()
            } else {
                signing.signingCertificateHistory.orEmpty().toList()
            }
        }.getOrDefault(emptyList())
    }

    @Suppress("DEPRECATION")
    private fun legacySignatures(packageManager: PackageManager, packageName: String): List<Signature> =
        runCatching {
            packageManager
                .getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures.orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
}
