package com.erfangholami.androidsolidservices.services.dispatch

import android.os.Bundle
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.ipc.IpcEnvelope
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
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

/**
 * Runs one callback delivery, absorbing anything the remote binder throws.
 *
 * The callback belongs to the calling app, so its failures are not this process's to die from: it
 * may have gone away (`DeadObjectException`), or be an older build whose stub rejects this
 * interface (`SecurityException: Binder invocation to an incorrect interface`). Both are reported
 * and dropped — a client's state can never crash the provider.
 */
internal inline fun deliverSafely(method: String, action: () -> Unit) {
    try {
        action()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Telemetry.recordException(
            t,
            TelemetryAttribute.OPERATION to "aidl.callback",
            TelemetryAttribute.ERROR_TYPE to t.javaClass.simpleName,
            "callback_method" to method,
        )
    }
}

fun IASSParcelableCallback.deliverResult(result: Bundle?): Unit =
    deliverSafely("onResult") { onResult(result) }

fun IASSParcelableCallback.deliverError(code: Int, message: String): Unit =
    deliverSafely("onError") { onError(code, message) }

fun IASSParcelableListCallback.deliverResult(result: Bundle?): Unit =
    deliverSafely("onResult") { onResult(result) }

fun IASSParcelableListCallback.deliverError(code: Int, message: String): Unit =
    deliverSafely("onError") { onError(code, message) }

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
    val safeError: (Int, String) -> Unit = { code, message ->
        deliverSafely("onError") { onError(code, message) }
    }
    val safeSuccess: (T) -> Unit = { value ->
        deliverSafely("onResult") { onSuccess(value) }
    }
    return dispatchGuarded(dispatcher, caller, safeError) { block().handle(safeSuccess, safeError) }
}

fun <T : Parcelable?> CoroutineScope.dispatchParcelable(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    block: suspend () -> SolidResult<T>,
): Job = dispatchNetwork(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.of(it)) }, block)

fun <T : Parcelable> CoroutineScope.dispatchParcelableList(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableListCallback,
    block: suspend () -> SolidResult<List<T>>,
): Job = dispatchNetwork(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.ofList(it)) }, block)

fun CoroutineScope.dispatchString(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    block: suspend () -> SolidResult<String?>,
): Job = dispatchNetwork(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.ofString(it)) }, block)

fun CoroutineScope.dispatchBoolean(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    block: suspend () -> SolidResult<Boolean>,
): Job = dispatchNetwork(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.ofBoolean(it)) }, block)

fun <T> CoroutineScope.dispatchAcknowledged(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    block: suspend () -> SolidResult<T>,
): Job = dispatchNetwork(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.empty()) }, block)

fun <T> CoroutineScope.dispatchAnswering(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    answer: Parcelable?,
    block: suspend () -> SolidResult<T>,
): Job = dispatchNetwork(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.of(answer)) }, block)

fun <T : Parcelable> CoroutineScope.dispatchDataModuleParcelable(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    block: suspend () -> SolidResult<T>,
): Job = dispatchDataModule(dispatcher, callback::onError, { callback.onResult(IpcEnvelope.of(it)) }, block)

fun <T : Parcelable> CoroutineScope.dispatchDataModule(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onSuccess: (T?) -> Unit,
    block: suspend () -> SolidResult<T>,
): Job {
    val caller = beginAttributedCall()
    val safeError: (Int, String) -> Unit = { code, message ->
        deliverSafely("onError") { onError(code, message) }
    }
    val safeSuccess: (T?) -> Unit = { value ->
        deliverSafely("onResult") { onSuccess(value) }
    }
    return launch(dispatcher) {
        try {
            block().handle(safeSuccess, safeError)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Telemetry.recordException(
                t,
                TelemetryAttribute.OPERATION to "aidl.dataModule",
                TelemetryAttribute.ERROR_TYPE to t.javaClass.simpleName,
                TelemetryAttribute.CALLING_APP to caller,
            )
            safeError(ExceptionsErrorCode.UNKNOWN, t.message ?: t.toString())
        }
    }
}
