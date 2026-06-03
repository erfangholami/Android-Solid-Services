package com.erfangholami.androidsolidservices.services.dispatch

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.exceptions.toSharingErrorCode
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import com.erfangholami.androidsolidservices.shared.error.ExceptionsErrorCode
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun <T> SolidNetworkResponse<T>.handle(
    onSuccess: (T) -> Unit,
    onError: (Int, String) -> Unit,
) {
    when (this) {
        is SolidNetworkResponse.Success -> onSuccess(data)
        is SolidNetworkResponse.Error -> onError(errorCode, errorMessage)
        is SolidNetworkResponse.Exception ->
            onError(exception.toSharingErrorCode(), exception.message ?: "Unknown error")
    }
}

fun <T : Parcelable> DataModuleResult<T>.handle(
    onSuccess: (T?) -> Unit,
    onError: (Int, String) -> Unit,
) {
    when (this) {
        is DataModuleResult.Success -> onSuccess(data)
        is DataModuleResult.Error -> onError(ExceptionsErrorCode.UNKNOWN, errorMessage ?: "")
        is DataModuleResult.Exception ->
            onError(ExceptionsErrorCode.UNKNOWN, exception.message ?: exception.toString())
    }
}

fun <T> CoroutineScope.dispatchNetwork(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onSuccess: (T) -> Unit,
    block: suspend () -> SolidNetworkResponse<T>,
): Job = launch(dispatcher) { block().handle(onSuccess, onError) }

fun CoroutineScope.dispatchUnit(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onResult: () -> Unit,
    block: suspend () -> SolidNetworkResponse<Unit>,
): Job = launch(dispatcher) { block().handle({ onResult() }, onError) }

fun <T : Parcelable> CoroutineScope.dispatchDataModule(
    dispatcher: CoroutineDispatcher,
    onError: (Int, String) -> Unit,
    onSuccess: (T?) -> Unit,
    block: suspend () -> DataModuleResult<T>,
): Job = launch(dispatcher) {
    try {
        block().handle(onSuccess, onError)
    } catch (t: Throwable) {
        onError(ExceptionsErrorCode.UNKNOWN, t.message ?: t.toString())
    }
}
