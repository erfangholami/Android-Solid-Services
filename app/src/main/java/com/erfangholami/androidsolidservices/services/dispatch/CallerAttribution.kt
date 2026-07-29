package com.erfangholami.androidsolidservices.services.dispatch

import android.content.Context
import android.os.Binder
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

internal object CallerAttribution {
    private const val UNKNOWN = "unknown"
    private const val SELF = "self"

    @Volatile
    private var appContext: Context? = null

    private val packageNames = ConcurrentHashMap<Int, String>()

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun currentCaller(): String {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return SELF
        packageNames[uid]?.let { return it }
        val context = appContext ?: return UNKNOWN
        val resolved = runCatching { context.packageManager.getNameForUid(uid) }.getOrNull()
        if (resolved != null) packageNames[uid] = resolved
        return resolved ?: UNKNOWN
    }
}
