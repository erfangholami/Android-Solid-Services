package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.host.grant.AppGrantStore
import com.erfangholami.androidsolidservices.shared.model.grant.AccessLevel
import com.erfangholami.androidsolidservices.shared.model.grant.AppGrant
import com.erfangholami.androidsolidservices.shared.model.grant.GrantTarget
import com.erfangholami.androidsolidservices.shared.result.ExceptionsErrorCode

/**
 * The access policy over scoped grants: the effective level for a call is the highest level
 * among the grant entries that cover the call's target, and the call passes when that level
 * includes the verb's minimum.
 *
 * A pod entry covers everything. A resource entry covers its IRI and, for a container, the
 * subtree. A module entry covers the module's verbs and the containers [moduleRoots] reports
 * for it, which are only resolved when the grant holds a module entry. A denial names what the
 * caller holds and what the verb needs, so a developer can see which scope to ask for.
 */
public class ScopedAccessPolicy(
    private val grants: AppGrantStore,
    private val moduleRoots: ModuleRootResolver,
) : AccessPolicy {

    override suspend fun check(
        callerPackage: String,
        webId: String,
        target: VerbTarget,
        level: AccessLevel,
    ): AccessCheck {
        val grant = grants.get(callerPackage, webId)
            ?: return AccessCheck.Denied(
                ExceptionsErrorCode.NOT_PERMISSION,
                "$callerPackage holds no grant for $webId.",
            )
        val held = heldLevel(grant, target, webId)
            ?: return AccessCheck.Denied(
                ExceptionsErrorCode.NOT_PERMISSION,
                "Nothing in the grant for $callerPackage covers ${describe(target)}.",
            )
        if (!held.includes(level)) {
            return AccessCheck.Denied(
                ExceptionsErrorCode.NOT_PERMISSION,
                "Granted ${held.name} on ${describe(target)}; this call needs ${level.name}.",
            )
        }
        return AccessCheck.Allowed
    }

    private suspend fun heldLevel(grant: AppGrant, target: VerbTarget, webId: String): AccessLevel? =
        when (target) {
            is VerbTarget.Resource -> {
                val roots = resolveModuleRoots(grant, webId)
                grant.levelOn(target.uri) { moduleId -> roots[moduleId].orEmpty() }
            }

            is VerbTarget.Module -> grant.levelOnModule(target.id)
            VerbTarget.Pod -> grant.podLevel()
            VerbTarget.AnyEntry -> AccessLevel.strongest(grant.entries.map { it.level })
        }

    private suspend fun resolveModuleRoots(grant: AppGrant, webId: String): Map<String, List<String>> =
        grant.entries
            .mapNotNull { (it.target as? GrantTarget.Module)?.id }
            .distinct()
            .associateWith { moduleId -> moduleRoots.rootContainers(moduleId, webId) }

    private fun describe(target: VerbTarget): String = when (target) {
        is VerbTarget.Resource -> target.uri
        is VerbTarget.Module -> "the ${target.id} module"
        VerbTarget.Pod -> "the whole pod"
        VerbTarget.AnyEntry -> "this account"
    }
}
