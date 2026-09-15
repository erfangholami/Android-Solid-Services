package com.erfangholami.androidsolidservices.host.testing

import com.erfangholami.androidsolidservices.host.access.AccessCheck
import com.erfangholami.androidsolidservices.host.access.VerbTarget
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * One binder verb as the plan's table describes it: what it asks the policy for, which manager
 * verb it reaches when allowed, and how to call it. [invoke] builds a fresh callback every time,
 * so a row can run once allowed and once denied.
 */
class Verb(
    val name: String,
    val requirements: List<Pair<VerbTarget, AccessLevel>>,
    val managerVerb: String?,
    val deliversResult: Boolean = true,
    val invoke: () -> Outcome,
)

fun verb(
    name: String,
    vararg requirements: Pair<VerbTarget, AccessLevel>,
    managerVerb: String? = name,
    deliversResult: Boolean = true,
    invoke: () -> Outcome,
): Verb = Verb(name, requirements.toList(), managerVerb, deliversResult, invoke)

/** Runs every row of a binder's table under the allowed and the denied answer. */
class VerbTable(
    private val policy: RecordingPolicy,
    private val recorder: Recorder,
    private val verbs: List<Verb>,
) {

    fun assertEveryVerbIsGuardedAndReachesItsManager() {
        verbs.forEach { verb ->
            policy.answer = AccessCheck.Allowed
            policy.checks.clear()
            recorder.calls.clear()

            val outcome = verb.invoke()

            assertEquals("${verb.name} asked the policy for", verb.requirements, policy.checks.map { it.target to it.level })
            if (verb.managerVerb != null) {
                assertEquals("${verb.name} reached", listOf(verb.managerVerb), recorder.calls)
            }
            if (verb.deliversResult) {
                assertTrue("${verb.name} delivered $outcome", outcome is Outcome.Result)
            }
        }
    }

    fun assertEveryDeniedVerbStopsBeforeItsManager() {
        verbs.filter { it.requirements.isNotEmpty() }.forEach { verb ->
            policy.answer = AccessCheck.Denied(ExceptionsErrorCode.NOT_PERMISSION, "no")
            policy.checks.clear()
            recorder.calls.clear()

            val outcome = verb.invoke()

            assertEquals("${verb.name} denied", Outcome.Error(ExceptionsErrorCode.NOT_PERMISSION, "no"), outcome)
            assertTrue("${verb.name} reached ${recorder.calls}", recorder.calls.isEmpty())
        }
    }
}
