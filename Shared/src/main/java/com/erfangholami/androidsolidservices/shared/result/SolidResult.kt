package com.erfangholami.androidsolidservices.shared.result

/**
 * The result of every library operation: either a [Success] carrying the value,
 * or a [Failure] carrying a typed [SolidError].
 *
 * This is the one result shape across the whole library, replacing the historical
 * mix of `SolidNetworkResponse`, `DataModuleResult`, thrown exceptions, nullable
 * returns, and `Success(empty)`-on-failure. Because failure is a typed [SolidError]
 * — not a status int or a prose string — callers branch on [SolidError.code] and
 * never string-parse an error.
 *
 * Prefer the combinators ([map], [flatMap], [fold], [recover]) and the accessors
 * ([getOrNull], [errorOrNull], [getOrThrow]) over a manual `when`.
 */
public sealed class SolidResult<out T> {

    public data class Success<out T>(val value: T) : SolidResult<T>()

    public data class Failure(val error: SolidError) : SolidResult<Nothing>()

    public val isSuccess: Boolean get() = this is Success
    public val isFailure: Boolean get() = this is Failure

    /** The success value, or `null` on failure. */
    public fun getOrNull(): T? = (this as? Success)?.value

    /** The error, or `null` on success. */
    public fun errorOrNull(): SolidError? = (this as? Failure)?.error

    /** The success value, or throws [SolidResultException] carrying the error (and its cause). */
    public fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error.asException()
    }

    /** The success value, or [default] on failure. */
    public fun getOrDefault(default: @UnsafeVariance T): T =
        (this as? Success)?.value ?: default

    /** The success value, or the result of [onFailure] applied to the error. */
    public inline fun getOrElse(onFailure: (SolidError) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> onFailure(error)
    }

    /** Transforms the success value, propagating a failure unchanged. */
    public inline fun <R> map(transform: (T) -> R): SolidResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    /** Chains an operation that itself returns a [SolidResult], propagating a failure unchanged. */
    public inline fun <R> flatMap(transform: (T) -> SolidResult<R>): SolidResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    /** Collapses both variants to a single [R]. */
    public inline fun <R> fold(onSuccess: (T) -> R, onFailure: (SolidError) -> R): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }

    /** Recovers a failure to a success value; a success passes through unchanged. */
    public inline fun recover(transform: (SolidError) -> @UnsafeVariance T): SolidResult<T> = when (this) {
        is Success -> this
        is Failure -> Success(transform(error))
    }

    /** Runs [action] on the success value and returns this result unchanged. */
    public inline fun onSuccess(action: (T) -> Unit): SolidResult<T> {
        if (this is Success) action(value)
        return this
    }

    /** Runs [action] on the error and returns this result unchanged. */
    public inline fun onFailure(action: (SolidError) -> Unit): SolidResult<T> {
        if (this is Failure) action(error)
        return this
    }

    public companion object {
        public fun <T> success(value: T): SolidResult<T> = Success(value)
        public fun failure(error: SolidError): SolidResult<Nothing> = Failure(error)
    }
}

/**
 * Runs [block], returning its value as [SolidResult.Success] or mapping a thrown
 * exception to [SolidResult.Failure] via [SolidError.fromThrowable]. A
 * [kotlinx.coroutines.CancellationException] (recognized by class name so this
 * has no coroutines dependency) is rethrown so cancellation is never swallowed.
 */
public inline fun <T> solidCatching(block: () -> T): SolidResult<T> =
    try {
        SolidResult.Success(block())
    } catch (t: Throwable) {
        if (t.javaClass.name == "kotlinx.coroutines.CancellationException") throw t
        SolidResult.Failure(SolidError.fromThrowable(t))
    }
