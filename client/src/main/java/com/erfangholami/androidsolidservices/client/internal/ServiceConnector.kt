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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ServiceConnector<S : Any>(
    context: Context,
    serviceClassName: String,
    private val asInterface: (IBinder) -> S,
) {
    private val appContext: Context = context.applicationContext
    private val serviceClassName: String = serviceClassName

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    @Volatile
    private var service: S? = null

    @Volatile
    private var bound: Boolean = false

    private val deathRecipient = IBinder.DeathRecipient {
        service = null
        _connectionState.value = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = asInterface(binder)
            runCatching { binder.linkToDeath(deathRecipient, 0) }
            _connectionState.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _connectionState.value = false
        }

        override fun onBindingDied(name: ComponentName) {
            rebind()
        }

        override fun onNullBinding(name: ComponentName) {
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
    }

    suspend fun <T> await(register: (S, CallbackBridge<T>) -> Unit): T =
        try {
            awaitOnce(register)
        } catch (_: DeadObjectException) {
            rebind()
            awaitOnce(register)
        } catch (_: RemoteException) {
            rebind()
            awaitOnce(register)
        }

    private suspend fun <T> awaitOnce(register: (S, CallbackBridge<T>) -> Unit): T {
        val connectedService = awaitService()
        return suspendCancellableCoroutine { cont ->
            try {
                register(connectedService, CallbackBridge(cont))
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    private suspend fun awaitService(): S {
        service?.let { return it }
        if (!bound) bind()
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connectionState.first { it } }
        return service ?: throw SolidException.SolidServiceConnectionException()
    }

    private fun bind() {
        if (bound) return
        val intent = Intent().setClassName(ANDROID_SOLID_SERVICES_PACKAGE_NAME, serviceClassName)
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

internal class CallbackBridge<T>(private val cont: CancellableContinuation<T>) {

    fun onResult(value: T) {
        if (cont.isActive) cont.resume(value)
    }

    fun onError(errorCode: Int, errorMessage: String?) {
        if (cont.isActive) {
            cont.resumeWithException(handleSolidException(errorCode, errorMessage ?: ""))
        }
    }

    fun onFailure(error: Throwable) {
        if (cont.isActive) cont.resumeWithException(error)
    }
}
