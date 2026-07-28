package com.erfangholami.androidsolidservices.shared.vocab

/**
 * WebID-TLS certificate vocabulary constants.
 * http://www.w3.org/ns/auth/cert#
 * Used in WebID profiles to advertise public keys for WebID-TLS authentication.
 */
public object Cert {
    public const val NAMESPACE: String = "http://www.w3.org/ns/auth/cert#"

    public const val KEY_TYPE: String = "${NAMESPACE}Key"
    public const val RSA_PUBLIC_KEY: String = "${NAMESPACE}RSAPublicKey"
    public const val CERTIFICATE: String = "${NAMESPACE}Certificate"

    /** Links a WebID to a public key. */
    public const val KEY: String = "${NAMESPACE}key"

    /** The RSA modulus (hex or integer). */
    public const val MODULUS: String = "${NAMESPACE}modulus"

    /** The RSA public exponent. */
    public const val EXPONENT: String = "${NAMESPACE}exponent"

    /** The label of a key. */
    public const val LABEL: String = "${NAMESPACE}label"

    /** The identity the key belongs to. */
    public const val IDENTITY: String = "${NAMESPACE}identity"
}
