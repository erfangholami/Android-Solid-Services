package com.erfangholami.androidsolidservices.host.dispatch

import android.content.Context
import android.os.Binder
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/**
 * Names the app on the other end of a binder transaction.
 *
 * Every guarded verb reads the caller here, on the binder thread, before it hands the work to a
 * coroutine: `Binder.getCallingUid()` is only meaningful while the transaction is in flight. A
 * UID resolves to a package once and is remembered, because the same app calls thousands of
 * times. [install] has to run before the first transaction; [HostService][com.erfangholami.androidsolidservices.host.HostService]
 * does it in `onCreate`.
 */
public object CallerAttribution {
    private const val UNKNOWN = "unknown"
    private const val SELF = "self"

    @Volatile
    private var appContext: Context? = null

    private val packageNames = ConcurrentHashMap<Int, String>()

    /** Remembers the application context the package lookups go through. */
    public fun install(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * The calling app's package name, or `null` when the UID resolves to no package or nothing
     * was [install]ed. The host's own process resolves to its own package name.
     */
    public fun callingPackage(): String? {
        val context = appContext ?: return null
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return context.packageName
        packageNames[uid]?.let { return it }
        val resolved = runCatching { context.packageManager.getNameForUid(uid) }.getOrNull()
        if (resolved != null) packageNames[uid] = resolved
        return resolved
    }

    internal fun currentCaller(): String {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return SELF
        return callingPackage() ?: UNKNOWN
    }
}
