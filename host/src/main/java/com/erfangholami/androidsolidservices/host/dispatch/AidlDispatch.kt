package com.erfangholami.androidsolidservices.host.dispatch

import android.os.Bundle
import android.os.Parcelable
import com.erfangholami.androidsolidservices.host.access.AccessCheck
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

/**
 * The access decision a verb waits for before it touches the pod, evaluated inside the
 * dispatched coroutine so no policy work ever runs on a binder thread.
 */
internal typealias Gate = suspend () -> AccessCheck

private fun SolidError.toExceptionsErrorCode(): Int = when (code) {
    SolidErrorCode.NOT_AUTHENTICATED -> ExceptionsErrorCode.SOLID_NOT_LOGGED_IN
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

internal fun <T> SolidResult<T>.handle(
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
 * The callback belongs to the calling app, so its failures are not this process's to die from:
 * it may have gone away (`DeadObjectException`), or be an older build whose stub rejects this
 * interface. Both are reported and dropped; a client's state can never crash the host.
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

internal fun IASSParcelableCallback.deliverResult(result: Bundle?): Unit =
    deliverSafely("onResult") { onResult(result) }

internal fun IASSParcelableCallback.deliverError(code: Int, message: String): Unit =
    deliverSafely("onError") { onError(code, message) }

internal fun IASSParcelableListCallback.deliverResult(result: Bundle?): Unit =
    deliverSafely("onResult") { onResult(result) }

internal fun IASSParcelableListCallback.deliverError(code: Int, message: String): Unit =
    deliverSafely("onError") { onError(code, message) }

private fun beginAttributedCall(): String {
    val caller = CallerAttribution.currentCaller()
    Telemetry.setKey(TelemetryAttribute.CALLING_APP, caller)
    return caller
}

private fun CoroutineScope.dispatchGuarded(
    dispatcher: CoroutineDispatcher,
    caller: String,
    gate: Gate?,
    onError: (Int, String) -> Unit,
    block: suspend () -> Unit,
): Job = launch(dispatcher) {
    try {
        when (val check = gate?.invoke() ?: AccessCheck.Allowed) {
            is AccessCheck.Denied -> onError(check.code, check.message)
            AccessCheck.Allowed -> block()
        }
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

internal fun <T> CoroutineScope.dispatchNetwork(
    dispatcher: CoroutineDispatcher,
    gate: Gate?,
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
    return dispatchGuarded(dispatcher, caller, gate, safeError) { block().handle(safeSuccess, safeError) }
}

internal fun <T : Parcelable?> CoroutineScope.dispatchParcelable(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    gate: Gate? = null,
    block: suspend () -> SolidResult<T>,
): Job = dispatchNetwork(dispatcher, gate, callback::onError, { callback.onResult(IpcEnvelope.of(it)) }, block)

internal fun <T : Parcelable> CoroutineScope.dispatchParcelableList(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableListCallback,
    gate: Gate? = null,
    block: suspend () -> SolidResult<List<T>>,
): Job = dispatchNetwork(dispatcher, gate, callback::onError, { callback.onResult(IpcEnvelope.ofList(it)) }, block)

internal fun CoroutineScope.dispatchString(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    gate: Gate? = null,
    block: suspend () -> SolidResult<String?>,
): Job = dispatchNetwork(dispatcher, gate, callback::onError, { callback.onResult(IpcEnvelope.ofString(it)) }, block)

internal fun CoroutineScope.dispatchBoolean(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    gate: Gate? = null,
    block: suspend () -> SolidResult<Boolean>,
): Job = dispatchNetwork(dispatcher, gate, callback::onError, { callback.onResult(IpcEnvelope.ofBoolean(it)) }, block)

internal fun <T> CoroutineScope.dispatchAcknowledged(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    gate: Gate? = null,
    block: suspend () -> SolidResult<T>,
): Job = dispatchNetwork(dispatcher, gate, callback::onError, { callback.onResult(IpcEnvelope.empty()) }, block)

internal fun <T> CoroutineScope.dispatchAnswering(
    dispatcher: CoroutineDispatcher,
    callback: IASSParcelableCallback,
    answer: Parcelable?,
    gate: Gate? = null,
    block: suspend () -> SolidResult<T>,
): Job = dispatchNetwork(dispatcher, gate, callback::onError, { callback.onResult(IpcEnvelope.of(answer)) }, block)
