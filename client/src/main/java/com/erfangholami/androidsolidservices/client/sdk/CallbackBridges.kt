package com.erfangholami.androidsolidservices.client.sdk

import android.os.Bundle
import android.os.Parcelable
import com.erfangholami.androidsolidservices.client.internal.CallbackBridge
import com.erfangholami.androidsolidservices.client.internal.ServiceConnector
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.ipc.booleanValue
import com.erfangholami.androidsolidservices.shared.ipc.parcelable
import com.erfangholami.androidsolidservices.shared.ipc.parcelableList
import com.erfangholami.androidsolidservices.shared.ipc.stringValue

internal fun <T> envelopeCallback(
    read: (Bundle?) -> T,
    onValue: (T) -> Unit,
    onFailure: (Int, String) -> Unit,
): IASSParcelableCallback.Stub = object : IASSParcelableCallback.Stub() {

    override fun onResult(result: Bundle?) = onValue(read(result))

    override fun onError(errorCode: Int, errorMessage: String) = onFailure(errorCode, errorMessage)
}

internal fun <T> envelopeListCallback(
    read: (Bundle?) -> T,
    onValue: (T) -> Unit,
    onFailure: (Int, String) -> Unit,
): IASSParcelableListCallback.Stub = object : IASSParcelableListCallback.Stub() {

    override fun onResult(result: Bundle?) = onValue(read(result))

    override fun onError(errorCode: Int, errorMessage: String) = onFailure(errorCode, errorMessage)
}

internal fun <T> envelopeBridge(
    bridge: CallbackBridge<T>,
    read: (Bundle?) -> T,
): IASSParcelableCallback.Stub = envelopeCallback(
    read = { runCatching { read(it) } },
    onValue = { outcome -> outcome.onSuccess(bridge::onResult).onFailure(bridge::onFailure) },
    onFailure = bridge::onError,
)

internal fun <T> envelopeListBridge(
    bridge: CallbackBridge<T>,
    read: (Bundle?) -> T,
): IASSParcelableListCallback.Stub = envelopeListCallback(
    read = { runCatching { read(it) } },
    onValue = { outcome -> outcome.onSuccess(bridge::onResult).onFailure(bridge::onFailure) },
    onFailure = bridge::onError,
)

internal fun <T : Parcelable> parcelableBridge(
    bridge: CallbackBridge<T?>,
    expected: Class<T>,
): IASSParcelableCallback.Stub = envelopeBridge(bridge) { it.parcelable(expected) }

internal fun unitBridge(bridge: CallbackBridge<Unit>): IASSParcelableCallback.Stub =
    envelopeBridge(bridge) { }

internal fun stringBridge(bridge: CallbackBridge<String>): IASSParcelableCallback.Stub =
    envelopeBridge(bridge) { it.stringValue().orEmpty() }

internal fun nullableStringBridge(bridge: CallbackBridge<String?>): IASSParcelableCallback.Stub =
    envelopeBridge(bridge) { it.stringValue() }

internal fun booleanBridge(bridge: CallbackBridge<Boolean>): IASSParcelableCallback.Stub =
    envelopeBridge(bridge) { it.booleanValue() }

internal suspend fun <S : Any, T> ServiceConnector<S>.suspendEnvelope(
    read: (Bundle?) -> T,
    call: (S, IASSParcelableCallback) -> Unit,
): T = await { service, bridge -> call(service, envelopeBridge(bridge, read)) }

internal suspend fun <S : Any, T : Parcelable> ServiceConnector<S>.suspendParcelable(
    expected: Class<T>,
    call: (S, IASSParcelableCallback) -> Unit,
): T? = suspendEnvelope({ it.parcelable(expected) }, call)

internal suspend fun <S : Any, T : Parcelable> ServiceConnector<S>.suspendParcelableList(
    expected: Class<T>,
    call: (S, IASSParcelableListCallback) -> Unit,
): List<T> = await { service, bridge ->
    call(service, envelopeListBridge(bridge) { it.parcelableList(expected) })
}

internal suspend fun <S : Any> ServiceConnector<S>.suspendUnit(
    call: (S, IASSParcelableCallback) -> Unit,
): Unit = suspendEnvelope({ }, call)

internal suspend fun <S : Any> ServiceConnector<S>.suspendString(
    call: (S, IASSParcelableCallback) -> Unit,
): String = suspendEnvelope({ it.stringValue().orEmpty() }, call)

internal suspend fun <S : Any> ServiceConnector<S>.suspendBoolean(
    call: (S, IASSParcelableCallback) -> Unit,
): Boolean = suspendEnvelope({ it.booleanValue() }, call)
