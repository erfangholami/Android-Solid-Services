package com.erfangholami.androidsolidservices.api.auth.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object KeystoreCipher {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "ass_profile_store"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LOAD_ATTEMPTS = 3
    private const val KEY_LOAD_RETRY_DELAY_MS = 150L

    @Volatile
    private var cachedKey: SecretKey? = null

    internal fun installKeyForTest(key: SecretKey) {
        cachedKey = key
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_LENGTH) { "Ciphertext too short to contain an IV" }
        val iv = data.copyOfRange(0, IV_LENGTH)
        val ciphertext = data.copyOfRange(IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        var lastFailure: Exception? = null
        repeat(KEY_LOAD_ATTEMPTS) { attempt ->
            try {
                (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
                    ?.let { return it.secretKey }
                if (!keyStore.containsAlias(KEY_ALIAS)) {
                    Telemetry.log("solid.auth profile-store key generated")
                    return generateKey()
                }
            } catch (e: Exception) {
                lastFailure = e
            }
            if (attempt < KEY_LOAD_ATTEMPTS - 1) Thread.sleep(KEY_LOAD_RETRY_DELAY_MS)
        }
        val failure = IllegalStateException(
            "The profile-store key exists in the Android Keystore but could not be loaded; " +
                "refusing to replace it because that would make every stored session unreadable.",
            lastFailure,
        )
        Telemetry.recordException(
            failure,
            TelemetryAttribute.OPERATION to "solid.auth.profile_store",
            "auth_error" to "store_key_unrecoverable",
        )
        throw failure
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
