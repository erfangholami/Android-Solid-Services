package com.erfangholami.androidsolidservices.api.auth.implementation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.erfangholami.androidsolidservices.api.BuildConfig
import com.erfangholami.androidsolidservices.api.auth.supportedDPopAlgorithms
import com.erfangholami.androidsolidservices.api.auth.supportsDPop
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Jwks
import io.jsonwebtoken.security.SignatureAlgorithm
import net.openid.appauth.AuthorizationServiceDiscovery
import java.math.BigInteger
import java.net.URI
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.security.auth.x500.X500Principal

internal class DPoPGenerator private constructor(
    val authDiscovery: AuthorizationServiceDiscovery,
    private val keyId: String?,
) {
    companion object {
        private val instances: MutableMap<String, DPoPGenerator> = mutableMapOf()
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS_PREFIX = BuildConfig.KEY_GENERATOR_ALIAS
        private const val SIGN_ATTEMPTS = 3
        private const val SIGN_RETRY_DELAY_MS = 25L
        private val signingPermits = Semaphore(4)

        fun getInstance(
            authDiscovery: AuthorizationServiceDiscovery,
            keyId: String? = null,
        ): DPoPGenerator {
            val instanceKey = keyId ?: "issuer:${authDiscovery.issuer}"
            return synchronized(instances) {
                instances.getOrPut(instanceKey) { DPoPGenerator(authDiscovery, keyId) }
            }
        }

        fun deleteKeys(keyId: String) {
            runCatching {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                keyStore.aliases().toList()
                    .filter { it.startsWith("${KEYSTORE_ALIAS_PREFIX}_${keyId}_") }
                    .forEach { alias -> runCatching { keyStore.deleteEntry(alias) } }
            }
            synchronized(instances) { instances.remove(keyId) }
        }
    }

    private val selectedAlgo: DPopSupportedAlgo = selectCategory()

    private val nonces = ConcurrentHashMap<String, String>()

    private val keyholder: KeyHolder = KeyPairHolderFactory.getKeyHolder(
        KEYSTORE_PROVIDER,
        if (keyId != null) "${KEYSTORE_ALIAS_PREFIX}_${keyId}_${selectedAlgo.name}"
        else "${KEYSTORE_ALIAS_PREFIX}_${selectedAlgo.name}",
        selectedAlgo
    )

    fun updateNonce(forUri: String, newNonce: String) {
        nonces[originOf(forUri)] = newNonce
    }

    fun generateProof(httpMethod: String, httpUri: String, accessToken: String? = null): String {
        val htu = URI(httpUri).let { URI(it.scheme, it.authority, it.path, null, null).toString() }

        val headers: MutableMap<String, Any> = mutableMapOf(
            "typ" to "dpop+jwt",
            "alg" to keyholder.getAlgorithmName(),
            "jwk" to Jwks.builder().key<PublicKey, PrivateKey>(keyholder.getPublicKey()).build(),
        )

        val claims: MutableMap<String, Any> = mutableMapOf(
            "jti" to UUID.randomUUID().toString(),
            "htm" to httpMethod,
            "htu" to htu,
            "iat" to Date().time / 1000,
        )
        nonces[originOf(httpUri)]?.let { claims["nonce"] = it }
        if (!accessToken.isNullOrEmpty()) {
            claims["ath"] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
            )
        }

        var lastFailure: RuntimeException? = null
        repeat(SIGN_ATTEMPTS) { attempt ->
            signingPermits.acquire()
            try {
                return Jwts.builder()
                    .header().add(headers).and()
                    .claims().add(claims).and()
                    .signWith(keyholder.getPrivateKey(), keyholder.getAlgorithm())
                    .compact()
            } catch (e: io.jsonwebtoken.security.SecurityException) {
                lastFailure = e
                if (!isPrunedKeystoreOperation(e) || attempt == SIGN_ATTEMPTS - 1) throw e
                Telemetry.log("solid.auth dpop sign retry ${attempt + 1} after keystore operation prune")
            } finally {
                signingPermits.release()
            }
            Thread.sleep(SIGN_RETRY_DELAY_MS)
        }
        throw lastFailure!!
    }

    private fun isPrunedKeystoreOperation(failure: Throwable): Boolean {
        var cause: Throwable? = failure
        while (cause != null) {
            val message = cause.message.orEmpty()
            if (message.contains("Pruned") ||
                message.contains("INVALID_OPERATION_HANDLE") ||
                message.contains("Invalid operation handle")
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun selectCategory(): DPopSupportedAlgo {
        if (!authDiscovery.supportsDPop() || authDiscovery.supportedDPopAlgorithms().isEmpty()) {
            throw IllegalArgumentException("Configuration doesn't support DPoP")
        }
        val algos = authDiscovery.supportedDPopAlgorithms()
        return listOf(
            DPopSupportedAlgo.ES256,
            DPopSupportedAlgo.ES384,
            DPopSupportedAlgo.ES512,
            DPopSupportedAlgo.PS256,
            DPopSupportedAlgo.PS384,
            DPopSupportedAlgo.PS512,
            DPopSupportedAlgo.RS256,
            DPopSupportedAlgo.RS384,
            DPopSupportedAlgo.RS512,
        ).firstOrNull { algos.contains(it.toString()) }
            ?: throw IllegalArgumentException("Server advertises no supported DPoP algorithms. Supported: $algos")
    }

    private fun originOf(uri: String): String = URI(uri).let { "${it.scheme}://${it.authority}" }
}

internal enum class DPopSupportedAlgo {
    RS256, RS384, RS512,
    PS256, PS384, PS512,
    ES256, ES384, ES512,
}

private abstract class KeyHolder {
    abstract val provider: String
    abstract val alias: String
    abstract val keyPair: KeyPair

    fun getPublicKey(): PublicKey = keyPair.public
    fun getPrivateKey(): PrivateKey = keyPair.private
    fun getAlgorithmName(): String = getAlgorithm().id
    abstract fun getAlgorithm(): SignatureAlgorithm
}

private object KeyPairHolderFactory {
    fun getKeyHolder(provider: String, alias: String, algorithmName: String): KeyHolder =
        getKeyHolder(provider, alias, DPopSupportedAlgo.valueOf(algorithmName))

    fun getKeyHolder(provider: String, alias: String, algorithm: DPopSupportedAlgo): KeyHolder =
        when (algorithm) {
            DPopSupportedAlgo.RS256 -> RS256KeyHolder(provider, alias)
            DPopSupportedAlgo.RS384 -> RS384KeyHolder(provider, alias)
            DPopSupportedAlgo.RS512 -> RS512KeyHolder(provider, alias)
            DPopSupportedAlgo.PS256 -> PS256KeyHolder(provider, alias)
            DPopSupportedAlgo.PS384 -> PS384KeyHolder(provider, alias)
            DPopSupportedAlgo.PS512 -> PS512KeyHolder(provider, alias)
            DPopSupportedAlgo.ES256 -> ES256KeyHolder(provider, alias)
            DPopSupportedAlgo.ES384 -> ES384KeyHolder(provider, alias)
            DPopSupportedAlgo.ES512 -> ES512KeyHolder(provider, alias)
        }
}

private fun loadOrCreateKeyPair(provider: String, alias: String, generate: () -> KeyPair): KeyPair {
    val keyStore = KeyStore.getInstance(provider)
    keyStore.load(null)
    if (keyStore.containsAlias(alias)) {
        val entry = runCatching { keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry }.getOrNull()
        if (entry != null) return KeyPair(entry.certificate.publicKey, entry.privateKey)
        val failure = IllegalStateException(
            "DPoP key '$alias' exists in the Android Keystore but could not be loaded; " +
                "refusing to replace it because the provider's tokens are bound to it.",
        )
        Telemetry.recordException(
            failure,
            TelemetryAttribute.OPERATION to "solid.auth.dpop_key",
            "auth_error" to "dpop_key_unrecoverable",
        )
        throw failure
    }
    Telemetry.log("solid.auth dpop key generated (alias=$alias)")
    return generate()
}

private abstract class RSKeyHolder(
    override val provider: String,
    override val alias: String,
) : KeyHolder() {
    private val localKeyPair: KeyPair
    abstract fun getDigest(): String
    override val keyPair: KeyPair get() = localKeyPair

    init {
        localKeyPair = loadOrCreateKeyPair(provider, alias) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, provider)
            kpg.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setCertificateSerialNumber(BigInteger.valueOf(777))
                    .setCertificateSubject(X500Principal("CN=$alias"))
                    .setDigests(getDigest())
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build()
            )
            kpg.generateKeyPair()
        }
    }
}

private abstract class PSKeyHolder(
    override val provider: String,
    override val alias: String,
) : KeyHolder() {
    private val localKeyPair: KeyPair
    abstract fun getDigest(): String
    override val keyPair: KeyPair get() = localKeyPair

    init {
        localKeyPair = loadOrCreateKeyPair(provider, alias) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, provider)
            kpg.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setCertificateSerialNumber(BigInteger.valueOf(777))
                    .setCertificateSubject(X500Principal("CN=$alias"))
                    .setDigests(getDigest())
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setKeySize(2048)
                    .build()
            )
            kpg.generateKeyPair()
        }
    }
}

private abstract class ESKeyHolder(
    override val provider: String,
    override val alias: String,
) : KeyHolder() {
    private val localKeyPair: KeyPair
    abstract fun getCurve(): String
    abstract fun getDigest(): String
    override val keyPair: KeyPair get() = localKeyPair

    init {
        localKeyPair = loadOrCreateKeyPair(provider, alias) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, provider)
            kpg.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec(getCurve()))
                    .setDigests(getDigest())
                    .build()
            )
            kpg.generateKeyPair()
        }
    }
}

private class RS256KeyHolder(provider: String, alias: String) : RSKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.RS256
    override fun getDigest(): String = KeyProperties.DIGEST_SHA256
}

private class RS384KeyHolder(provider: String, alias: String) : RSKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.RS384
    override fun getDigest(): String = KeyProperties.DIGEST_SHA384
}

private class RS512KeyHolder(provider: String, alias: String) : RSKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.RS512
    override fun getDigest(): String = KeyProperties.DIGEST_SHA512
}

private class PS256KeyHolder(provider: String, alias: String) : PSKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.PS256
    override fun getDigest(): String = KeyProperties.DIGEST_SHA256
}

private class PS384KeyHolder(provider: String, alias: String) : PSKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.PS384
    override fun getDigest(): String = KeyProperties.DIGEST_SHA384
}

private class PS512KeyHolder(provider: String, alias: String) : PSKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.PS512
    override fun getDigest(): String = KeyProperties.DIGEST_SHA512
}

private class ES256KeyHolder(provider: String, alias: String) : ESKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.ES256
    override fun getCurve(): String = "secp256r1"
    override fun getDigest(): String = KeyProperties.DIGEST_SHA256
}

private class ES384KeyHolder(provider: String, alias: String) : ESKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.ES384
    override fun getCurve(): String = "secp384r1"
    override fun getDigest(): String = KeyProperties.DIGEST_SHA384
}

private class ES512KeyHolder(provider: String, alias: String) : ESKeyHolder(provider, alias) {
    override fun getAlgorithm(): SignatureAlgorithm = Jwts.SIG.ES512
    override fun getCurve(): String = "secp521r1"
    override fun getDigest(): String = KeyProperties.DIGEST_SHA512
}
