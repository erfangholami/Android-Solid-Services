package com.erfangholami.androidsolidservices.services

import com.erfangholami.androidsolidservices.shared.model.auth.IASSLoginCallback
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PendingLoginRequest(
    val callerPackage: String,
    val callerName: String,
    val callback: IASSLoginCallback,
)

@Singleton
class PendingLoginRequests @Inject constructor() {

    private val requests = ConcurrentHashMap<String, PendingLoginRequest>()

    fun put(id: String, request: PendingLoginRequest) {
        requests[id] = request
    }

    fun get(id: String): PendingLoginRequest? = requests[id]

    fun remove(id: String): PendingLoginRequest? = requests.remove(id)
}
