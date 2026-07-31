package com.erfangholami.androidsolidservices.client.internal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import com.erfangholami.androidsolidservices.client.sdk.SolidException
import com.erfangholami.androidsolidservices.client.sdk.handleSolidException
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryAttribute
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns one binding to an Android Solid Services AIDL service and turns its callback-style calls
 * into `suspend` calls.
 *
 * [servicePackageName] is separate so instrumented tests can point the binding at the test APK,
 * which hosts fakes under the production class names; `asInterface` stays last so callers keep
 * passing it as a trailing lambda.
 *
 * Death handling: a call parked on an AIDL callback is never resumed by the framework when the
 * service process dies, so the connector tracks parked continuations and fails them itself —
 * with [DeadObjectException], which [await] answers by rebinding and retrying once, exactly as
 * it treats a death during the call's registration. A deliberate [unbind] fails them with
 * [SolidException.SolidServiceConnectionException] instead, so no retry resurrects a binding
 * the caller tore down.
 */
internal class ServiceConnector<S : Any>(
    context: Context,
    serviceClassName: String,
    private val servicePackageName: String,
    private val asInterface: (IBinder) -> S,
) {
    /** Binds to the installed Android Solid Services app. */
    constructor(
        context: Context,
        serviceClassName: String,
        asInterface: (IBinder) -> S,
    ) : this(context, serviceClassName, SdkTarget.servicePackageName, asInterface)

    private val appContext: Context = context.applicationContext
    private val serviceClassName: String = serviceClassName

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    @Volatile
    private var service: S? = null

    @Volatile
    private var bound: Boolean = false

    private val serviceLabel: String = serviceClassName.substringAfterLast('.')

    private val pending = ConcurrentHashMap.newKeySet<CancellableContinuation<*>>()

    private val deathRecipient = IBinder.DeathRecipient {
        Telemetry.log("ass.ipc $serviceLabel binder died")
        service = null
        _connectionState.value = false
        failPending(DeadObjectException())
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName,
            binder: IBinder,
        ) {
            service = asInterface(binder)
            runCatching { binder.linkToDeath(deathRecipient, 0) }
            _connectionState.value = true
            Telemetry.log("ass.ipc $serviceLabel connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Telemetry.log("ass.ipc $serviceLabel disconnected")
            service = null
            _connectionState.value = false
            failPending(DeadObjectException())
        }

        override fun onBindingDied(name: ComponentName) {
            Telemetry.log("ass.ipc $serviceLabel binding died — rebinding")
            failPending(DeadObjectException())
            rebind()
        }

        override fun onNullBinding(name: ComponentName) {
            Telemetry.log("ass.ipc $serviceLabel returned a null binding")
            _connectionState.value = false
        }
    }

    init {
        bind()
    }

    fun isConnected(): Boolean = service != null

    fun require(): S = service ?: throw SolidException.SolidServiceConnectionException()

    fun unbind() {
        runCatching { appContext.unbindService(connection) }
        service = null
        bound = false
        _connectionState.value = false
        failPending(SolidException.SolidServiceConnectionException())
    }

    suspend fun <T> await(register: (S, CallbackBridge<T>) -> Unit): T = try {
        awaitOnce(register)
    } catch (_: RemoteException) {
        rebind()
        awaitOnce(register)
    }

    private suspend fun <T> awaitOnce(register: (S, CallbackBridge<T>) -> Unit): T {
        val connectedService = awaitService()
        var parked: CancellableContinuation<T>? = null
        try {
            return suspendCancellableCoroutine { cont ->
                parked = cont
                pending.add(cont)
                try {
                    register(connectedService, CallbackBridge(cont))
                } catch (e: Throwable) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        } finally {
            parked?.let(pending::remove)
        }
    }

    private fun failPending(cause: Throwable) {
        val parked = pending.toList()
        pending.removeAll(parked.toSet())
        parked.forEach { cont ->
            @Suppress("UNCHECKED_CAST")
            val continuation = cont as CancellableContinuation<Any?>
            if (continuation.isActive) continuation.resumeWithException(cause)
        }
    }

    private suspend fun awaitService(): S {
        service?.let { return it }
        val span = Telemetry.startSpan("ass_ipc_bind")
        span.putAttribute("service", serviceLabel)
        try {
            if (!bound) bind()
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connectionState.first { it } }
            val connected = service
            if (connected == null) {
                span.putAttribute(TelemetryAttribute.OUTCOME, TelemetryAttribute.OUTCOME_ERROR)
                val failure = SolidException.SolidServiceConnectionException()
                Telemetry.recordException(
                    failure,
                    TelemetryAttribute.OPERATION to "ass.ipc.bind",
                    "service" to serviceLabel,
                )
                throw failure
            }
            span.putAttribute(TelemetryAttribute.OUTCOME, TelemetryAttribute.OUTCOME_SUCCESS)
            return connected
        } finally {
            span.stop()
        }
    }

    private fun bind() {
        if (bound) return
        val intent = Intent().setClassName(servicePackageName, serviceClassName)
        bound = runCatching {
            appContext.bindService(
                intent,
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
        }.getOrDefault(false)
    }

    private fun rebind() {
        runCatching { appContext.unbindService(connection) }
        service = null
        bound = false
        _connectionState.value = false
        bind()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}

internal class CallbackBridge<T>(
    private val cont: CancellableContinuation<T>,
) {
    fun onResult(value: T) {
        if (cont.isActive) cont.resume(value)
    }

    fun onError(
        errorCode: Int,
        errorMessage: String?,
    ) {
        if (cont.isActive) {
            cont.resumeWithException(handleSolidException(errorCode, errorMessage ?: ""))
        }
    }

    fun onFailure(error: Throwable) {
        if (cont.isActive) cont.resumeWithException(error)
    }
}
