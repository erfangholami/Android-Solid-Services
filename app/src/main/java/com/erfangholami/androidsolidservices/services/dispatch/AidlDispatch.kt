package com.erfangholami.androidsolidservices.services.dispatch

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private fun SolidError.toExceptionsErrorCode(): Int = when (code) {
    SolidErrorCode.ACCESS_DENIED -> ExceptionsErrorCode.ACCESS_DENIED
    SolidErrorCode.NO_INBOX -> ExceptionsErrorCode.NO_INBOX
    SolidErrorCode.INBOX_UNAUTHORIZED -> ExceptionsErrorCode.INBOX_UNAUTHORIZED
    SolidErrorCode.INBOX_FORBIDDEN -> ExceptionsErrorCode.INBOX_FORBIDDEN
    SolidErrorCode.NOTIFICATION_DELIVERY -> ExceptionsErrorCode.NOTIFICATION_DELIVERY_FAILED
    SolidErrorCode.IMPERSONATION_DETECTED -> ExceptionsErrorCode.IMPERSONATION_DETECTED
    SolidErrorCode.STALE_ACL -> ExceptionsErrorCode.STALE_ACL
    SolidErrorCode.UNSUPPORTED_AUTH_BACKEND -> ExceptionsErrorCode.UNSUPPORTED_AUTH_BACKEND
    else -> httpStatus ?: ExceptionsErrorCode.UNKNOWN
}

fun <T> SolidResult<T>.handle(
    onSuccess: (T) -> Unit,
    onError: (Int, String) -> Unit,
) {
    when (this) {
        is SolidResult.Success -> onSuccess(value)
        is SolidResult.Failure -> onError(error.toExceptionsErrorCode(), error.message)
    }
}

private fun beginAttributedCall(): String {
    val caller = CallerAttribution.currentCaller()
    Telemetry.setKey(TelemetryAttribute.CALLING_APP, caller)
    return caller
}

private fun CoroutineScope.dispatchGuarded(
    dispatcher: CoroutineDispatcher,
    caller: String,
    onError: (Int, String) -> Unit,
    block: suspend () -> Unit,
): Job = launch(dispatcher) {
    try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Telemetry.recordException(
            t,
            TelemetryAttribute.OPERATION to "aidl.dispatch",
            TelemetryAttribute.ERROR_TYPE to t.javaClass.simpleName,
            TelemetryAttribute.CALLING_APP to caller,
        )
        onError(ExceptionsErrorCode.UNKNOWN, t.message ?: t.toString())
    }
}

fun <T> CoroutineScope.dispatchNetwork(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onSuccess: (T) -> Unit,
    block: suspend () -> SolidResult<T>,
): Job {
    val caller = beginAttributedCall()
    return dispatchGuarded(dispatcher, caller, onError) { block().handle(onSuccess, onError) }
}

fun CoroutineScope.dispatchUnit(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onResult: () -> Unit,
    block: suspend () -> SolidResult<Unit>,
): Job {
    val caller = beginAttributedCall()
    return dispatchGuarded(dispatcher, caller, onError) { block().handle({ onResult() }, onError) }
}

fun <T : Parcelable> CoroutineScope.dispatchDataModule(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onSuccess: (T?) -> Unit,
    block: suspend () -> SolidResult<T>,
): Job {
    val caller = beginAttributedCall()
    return launch(dispatcher) {
        try {
            block().handle(onSuccess, onError)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Telemetry.recordException(
                t,
                TelemetryAttribute.OPERATION to "aidl.dataModule",
                TelemetryAttribute.ERROR_TYPE to t.javaClass.simpleName,
                TelemetryAttribute.CALLING_APP to caller,
            )
            onError(ExceptionsErrorCode.UNKNOWN, t.message ?: t.toString())
        }
    }
}
