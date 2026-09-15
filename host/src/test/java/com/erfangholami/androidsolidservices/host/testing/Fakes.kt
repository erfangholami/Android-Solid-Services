package com.erfangholami.androidsolidservices.host.testing

import android.os.Bundle
import com.erfangholami.androidsolidservices.host.HostSession
import com.erfangholami.androidsolidservices.host.access.AccessCheck
import com.erfangholami.androidsolidservices.host.access.AccessPolicy
import com.erfangholami.androidsolidservices.host.access.ModuleRootResolver
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.host.grant.AppGrantStore
import com.erfangholami.androidsolidservices.shared.IASSParcelableCallback
import com.erfangholami.androidsolidservices.shared.IASSParcelableListCallback
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantEntry
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.jvm.kotlinFunction

const val ALICE = "https://alice.pod/profile/card#me"
const val CALLER = "com.example.notes"

fun grant(
    vararg entries: GrantEntry,
    packageName: String = CALLER,
    webId: String = ALICE,
): AppGrant = AppGrant(
    packageName = packageName,
    webId = webId,
    appLabel = "Notes",
    entries = entries.toList(),
    grantedAt = "2026-09-14T10:00:00Z",
)

fun entry(target: GrantTarget, level: AccessLevel): GrantEntry = GrantEntry(target, level)

class FakeHostSession(
    var loggedIn: Boolean = true,
    val sessions: MutableSet<String> = mutableSetOf(ALICE),
    val profiles: MutableMap<String, WebId> = mutableMapOf(),
) : HostSession {

    var readyCalls: Int = 0

    override suspend fun awaitReady() {
        readyCalls++
    }

    override suspend fun hasLoggedIn(): Boolean = loggedIn

    override suspend fun hasSession(webId: String): Boolean = webId in sessions

    override suspend fun profileDocument(webId: String): WebId? = profiles[webId]
}

class RecordingPolicy(var answer: AccessCheck = AccessCheck.Allowed) : AccessPolicy {

    data class Check(val caller: String, val webId: String, val target: VerbTarget, val level: AccessLevel)

    val checks: MutableList<Check> = mutableListOf()

    override suspend fun check(
        callerPackage: String,
        webId: String,
        target: VerbTarget,
        level: AccessLevel,
    ): AccessCheck {
        checks += Check(callerPackage, webId, target, level)
        return answer
    }
}

class InMemoryGrantStore(initial: List<AppGrant> = emptyList()) : AppGrantStore {

    private val state = MutableStateFlow(initial)

    var reads: Int = 0

    override fun grants(): Flow<List<AppGrant>> = state

    override suspend fun get(packageName: String, webId: String): AppGrant? {
        reads++
        return state.value.firstOrNull { it.packageName == packageName && it.webId == webId }
    }

    override suspend fun put(grant: AppGrant) {
        state.value = state.value.filterNot { it.packageName == grant.packageName && it.webId == grant.webId } + grant
    }

    override suspend fun revoke(packageName: String, webId: String) {
        state.value = state.value.filterNot { it.packageName == packageName && it.webId == webId }
    }

    override suspend fun revokeAll(webId: String) {
        state.value = state.value.filterNot { it.webId == webId }
    }

    override suspend fun revokePackage(packageName: String) {
        state.value = state.value.filterNot { it.packageName == packageName }
    }
}

class FakeModuleRoots(
    private val roots: Map<String, List<String>> = emptyMap(),
) : ModuleRootResolver {

    val resolved: MutableList<String> = mutableListOf()
    val invalidated: MutableList<String> = mutableListOf()

    override suspend fun rootContainers(moduleId: String, webId: String): List<String> {
        resolved += moduleId
        return roots[moduleId].orEmpty()
    }

    override fun invalidate(webId: String) {
        invalidated += webId
    }
}

sealed interface Outcome {
    data class Result(val bundle: Bundle?) : Outcome
    data class Error(val code: Int, val message: String) : Outcome
}

class CapturingCallback : IASSParcelableCallback.Stub() {

    private val outcome = CompletableDeferred<Outcome>()

    override fun onResult(result: Bundle?) {
        outcome.complete(Outcome.Result(result))
    }

    override fun onError(errorCode: Int, errorMessage: String) {
        outcome.complete(Outcome.Error(errorCode, errorMessage))
    }

    fun await(): Outcome = runBlocking { withTimeout(AWAIT_MILLIS) { outcome.await() } }
}

class CapturingListCallback : IASSParcelableListCallback.Stub() {

    private val outcome = CompletableDeferred<Outcome>()

    override fun onResult(result: Bundle?) {
        outcome.complete(Outcome.Result(result))
    }

    override fun onError(errorCode: Int, errorMessage: String) {
        outcome.complete(Outcome.Error(errorCode, errorMessage))
    }

    fun await(): Outcome = runBlocking { withTimeout(AWAIT_MILLIS) { outcome.await() } }
}

private const val AWAIT_MILLIS = 5_000L

/** The verbs a recording proxy has been asked to run, by name, in order. */
class Recorder {
    val calls: MutableList<String> = mutableListOf()
}

/**
 * Stands in for a library manager: records every verb and answers it from its declared return
 * type, so a binder test can tell a verb that reached the manager from one the guard stopped
 * without hand-writing fifty stubs. [overrides] answers a verb by name when the canned value
 * would not do.
 */
inline fun <reified T : Any> recording(recorder: Recorder, overrides: Map<String, Any?> = emptyMap()): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "toString" -> "recording ${T::class.simpleName}"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> {
                recorder.calls += method.name
                if (overrides.containsKey(method.name)) overrides[method.name] else cannedAnswer(method)
            }
        }
    } as T

fun cannedAnswer(method: Method): Any? {
    val function = method.kotlinFunction ?: return null
    val returnType = function.returnType
    if (returnType.classifier != SolidResult::class) return null
    val payload = returnType.arguments.firstOrNull()?.type?.classifier
    val value: Any? = when (payload) {
        Boolean::class -> true
        String::class -> ""
        Int::class -> 0
        Unit::class -> Unit
        List::class -> emptyList<Any>()
        else -> null
    }
    return SolidResult.Success(value)
}
