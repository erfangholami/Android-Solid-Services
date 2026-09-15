package com.erfangholami.androidsolidservices.host

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.erfangholami.androidsolidservices.host.dispatch.CallerAttribution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The bound service a host exports for one binder.
 *
 * It owns the coroutine scope the binder dispatches on and cancels it when the service is
 * destroyed, so no pod call outlives its service. It also installs [CallerAttribution] before
 * the first transaction can arrive. A host subclasses it once per binder, builds the binder from
 * the dependencies it injects, and declares the service in its manifest with the matching
 * action from `SolidHostContract`.
 */
public abstract class HostService : Service() {

    /** The scope the binder dispatches on; cancelled in [onDestroy]. */
    protected val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CallerAttribution.install(this)
    }

    override fun onBind(intent: Intent?): IBinder = createBinder()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Builds the binder this service exports, called on each bind. */
    protected abstract fun createBinder(): IBinder
}
