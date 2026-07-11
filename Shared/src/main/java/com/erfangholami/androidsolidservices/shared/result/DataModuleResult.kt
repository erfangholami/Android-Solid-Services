package com.erfangholami.androidsolidservices.shared.result

import android.os.Parcelable

/**
 * Sealed result type returned by contacts data-module operations.
 *
 * Every data-module method returns one of three variants:
 * - [Success] — the operation completed and [Success.data] holds the result.
 * - [Error] — the server or pod returned a meaningful error; [Error.errorMessage]
 *   describes it in human-readable form.
 * - [Exception] — an unexpected exception was thrown (network failure, parsing error,
 *   etc.); [Exception.exception] carries the original throwable.
 *
 * Use [getOrNull] when you want to ignore failures, or [getOrThrow] when a failure
 * should propagate as an exception.
 */
public sealed class DataModuleResult<T : Parcelable> {
    /** The operation succeeded; [data] holds the result value. */
    public data class Success<T : Parcelable>(val data: T) : DataModuleResult<T>()

    /** The operation failed with a server-side or protocol-level error. */
    public data class Error<T : Parcelable>(val errorMessage: String?) : DataModuleResult<T>()

    /** The operation failed due to an unexpected exception. */
    public data class Exception<T : Parcelable>(val exception: Throwable) : DataModuleResult<T>()
}

/**
 * Returns [DataModuleResult.Success.data], or throws [IllegalStateException] for [DataModuleResult.Error]
 * and re-throws the original exception for [DataModuleResult.Exception].
 */
public fun <T : Parcelable> DataModuleResult<T>.getOrThrow(): T = when (this) {
    is DataModuleResult.Success -> data
    is DataModuleResult.Error -> throw IllegalStateException(errorMessage)
    is DataModuleResult.Exception -> throw exception
}

/**
 * Returns [DataModuleResult.Success.data], or `null` for any failure variant.
 */
public fun <T : Parcelable> DataModuleResult<T>.getOrNull(): T? =
    if (this is DataModuleResult.Success) data else null

/**
 * Maps a [DataModuleResult.Success] payload with [transform]; failure variants are
 * propagated unchanged.
 */
public inline fun <T : Parcelable, R : Parcelable> DataModuleResult<T>.map(
    transform: (T) -> R,
): DataModuleResult<R> = when (this) {
    is DataModuleResult.Success -> DataModuleResult.Success(transform(data))
    is DataModuleResult.Error -> DataModuleResult.Error(errorMessage)
    is DataModuleResult.Exception -> DataModuleResult.Exception(exception)
}

