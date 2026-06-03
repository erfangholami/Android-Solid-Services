package com.erfangholami.androidsolidservices.shared.http

/**
 * The result type returned by every Solid network operation in the `api` module.
 *
 * Rather than throwing on failure, operations return one of three variants so callers
 * can distinguish a server-reported HTTP error from an unexpected local failure without
 * wrapping each call in a `try`/`catch`:
 *
 * - [Success] — the request completed and the server returned a successful response.
 *   Carries the parsed [data].
 * - [Error] — the server responded, but with a non-success HTTP status (e.g. `401`,
 *   `403`, `404`, `412`). Carries the [Error.errorCode] (the HTTP status code) and an
 *   [Error.errorMessage]. This is the variant to inspect for protocol-level outcomes
 *   such as access denied, not found, or a failed precondition.
 * - [Exception] — the request never produced an HTTP response (network failure,
 *   serialization error, cancellation, etc.). Carries the underlying [Exception.exception].
 *
 * Use the `getOrXxx` helpers to collapse the variants when you only care about the
 * success value, or branch on the variant with a `when` expression for full control.
 *
 * @param T the type of the value produced on success.
 */
public sealed class SolidNetworkResponse<T> {
    /** The operation succeeded; [data] holds the parsed result. */
    public data class Success<T>(val data: T) : SolidNetworkResponse<T>()

    /**
     * The server returned a non-success HTTP response.
     *
     * @property errorCode the HTTP status code (e.g. `404`, `412`).
     * @property errorMessage a human-readable description of the failure.
     */
    public data class Error<T>(val errorCode: Int, val errorMessage: String) : SolidNetworkResponse<T>()

    /**
     * The operation failed before an HTTP response was obtained.
     *
     * @property exception the cause (network I/O, parsing, cancellation, etc.).
     */
    public data class Exception<T>(val exception: Throwable) : SolidNetworkResponse<T>()

    /**
     * Returns the success value, or throws if this is not [Success].
     *
     * Throws [IllegalStateException] for [Error] (message includes the status code)
     * and re-throws the captured cause for [Exception]. Use when a failure should
     * abort the current flow.
     */
    public fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("Operation failed ($errorCode): $errorMessage")
        is Exception -> throw exception
    }

    /** Returns the success value, or `null` for [Error] or [Exception]. */
    public fun getOrNull(): T? = if (this is Success) data else null

    /** Returns the success value, or [default] for [Error] or [Exception]. */
    public fun getOrDefault(default: T): T = if (this is Success) data else default
}
