package com.erfangholami.androidsolidservices.shared.result

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse

/**
 * Interop bridges between the legacy result types and the unified [SolidResult]/[SolidError]
 * model. They let features migrate one at a time while the whole library keeps compiling:
 * a not-yet-migrated caller can convert a new [SolidResult] back to the shape it expects, and
 * a migrated caller can lift a legacy response into the new model.
 *
 * These are transitional. Once every feature returns [SolidResult] natively, the legacy types
 * and these bridges are deleted.
 */

/** Lifts a legacy [SolidNetworkResponse] into the unified [SolidResult] model. */
public fun <T> SolidNetworkResponse<T>.toResult(): SolidResult<T> = when (this) {
    is SolidNetworkResponse.Success -> SolidResult.Success(data)
    is SolidNetworkResponse.Error -> SolidResult.Failure(SolidError.fromHttp(errorCode, errorMessage))
    is SolidNetworkResponse.Exception -> SolidResult.Failure(SolidError.fromThrowable(exception))
}

/**
 * Projects a [SolidResult] back onto the legacy [SolidNetworkResponse] shape: an error that
 * carries an [SolidError.httpStatus] becomes [SolidNetworkResponse.Error], anything else becomes
 * [SolidNetworkResponse.Exception] (so its typed cause survives).
 */
public fun <T> SolidResult<T>.toNetworkResponse(): SolidNetworkResponse<T> = when (this) {
    is SolidResult.Success -> SolidNetworkResponse.Success(value)
    is SolidResult.Failure -> {
        val status = error.httpStatus
        if (status != null) {
            SolidNetworkResponse.Error(status, error.message)
        } else {
            SolidNetworkResponse.Exception(error.asException())
        }
    }
}

/**
 * Lifts a legacy [DataModuleResult] into the unified model. Note the historical
 * `DataModuleResult.Error` carried only a prose message (the HTTP status was already
 * discarded upstream), so it maps to [SolidError.Unknown]; migrating the data modules at
 * their source is what restores the typed status.
 */
public fun <T : Parcelable> DataModuleResult<T>.toResult(): SolidResult<T> = when (this) {
    is DataModuleResult.Success -> SolidResult.Success(data)
    is DataModuleResult.Error -> SolidResult.Failure(SolidError.Unknown(errorMessage ?: "Data module error."))
    is DataModuleResult.Exception -> SolidResult.Failure(SolidError.fromThrowable(exception))
}
