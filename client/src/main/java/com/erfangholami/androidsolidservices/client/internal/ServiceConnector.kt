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

/**
 * Binds to one of the Android Solid Services AIDL services and exposes it as a typed [S].
 *
 * Centralizes the bind/connection/await plumbing that every client otherwise duplicates, and makes
 * the binding self-healing so a call succeeds whenever ASS is installed and reachable:
 * - binds with [Context.BIND_AUTO_CREATE] (the OS starts ASS's process/service on demand) plus
 *   [Context.BIND_IMPORTANT] (raises ASS's priority while this client is bound);
 * - [await] waits for the connection (bounded by [CONNECT_TIMEOUT_MS]) instead of failing the
 *   instant the binding hasn't completed — this absorbs the startup race and transient reconnects;
 * - re-establishes a dead binding ([ServiceConnection.onBindingDied]) and a [linkToDeath] death
 *   recipient drops state proactively when ASS's process dies;
 * - retries a call once if the binder died mid-flight (process recycled).
 *
 * Note: Android can kill ASS's process at any time, so "always running" is not achievable — this
 * instead guarantees on-demand reconnection. Binding uses the application context; call [unbind] to
 * release it.
 *
 * @param serviceClassName fully-qualified ASS service class (see `Constants`).
 * @param asInterface the generated `IXxx.Stub::asInterface` for the service.
 */
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
        // ASS's process died; drop state. The next call rebinds on demand.
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
            // Binding is retained; the OS reconnects (onServiceConnected) when the service returns.
            service = null
            _connectionState.value = false
        }

        override fun onBindingDied(name: ComponentName) {
            // The binding is permanently dead — tear it down and re-establish.
            rebind()
        }

        override fun onNullBinding(name: ComponentName) {
            _connectionState.value = false
        }
    }

    init {
        bind()
    }

    /** `true` once the bound service has connected. */
    fun isConnected(): Boolean = service != null

    /**
     * The connected service, or throws [SolidException.SolidServiceConnectionException] if it hasn't
     * connected yet. For synchronous (non-`suspend`) callers; `suspend` paths should use [await],
     * which waits for the connection.
     */
    fun require(): S = service ?: throw SolidException.SolidServiceConnectionException()

    /** Releases the service binding. Safe to call when already unbound. */
    fun unbind() {
        runCatching { appContext.unbindService(connection) }
        service = null
        bound = false
        _connectionState.value = false
    }

    /**
     * Awaits the binding (rebinding if needed), invokes [register] against the connected service,
     * and resumes with the callback's result. IPC errors map to the typed [SolidException]
     * hierarchy; a dead-binder failure (ASS process recycled) is retried once.
     */
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

    /** Returns the connected service, waiting up to [CONNECT_TIMEOUT_MS] for the binding. */
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
        /** Max time to wait for a (re)bind to connect before surfacing a connection error. */
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}

/** Resumes [cont] from an AIDL callback, mapping IPC `(errorCode, errorMessage)` to [SolidException]. */
internal class CallbackBridge<T>(private val cont: CancellableContinuation<T>) {

    fun onResult(value: T) {
        if (cont.isActive) cont.resume(value)
    }

    fun onError(errorCode: Int, errorMessage: String?) {
        if (cont.isActive) {
            cont.resumeWithException(handleSolidException(errorCode, errorMessage ?: ""))
        }
    }

    /** Fails the coroutine with [error] directly (e.g. a resource reconstruction failure). */
    fun onFailure(error: Throwable) {
        if (cont.isActive) cont.resumeWithException(error)
    }
}
