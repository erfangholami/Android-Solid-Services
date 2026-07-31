package com.erfangholami.androidsolidservices.client.internal

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Returns `null` from [onBind], which Android delivers as `onNullBinding` — the "service exists
 * but offers this caller nothing" case a disabled or feature-gated service produces. The connector
 * must report it as disconnected rather than pretend a binding exists.
 */
class NullBindingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}
