package com.erfangholami.androidsolidservices.shared.model.grant

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * What one app may do as one WebID, as the user approved it on the host's consent screen.
 *
 * There is one grant per `(packageName, webId)`. The effective level for a call is the highest
 * level among the entries that cover the call's target: [GrantTarget.Pod] covers everything, a
 * [GrantTarget.Resource] covers by IRI prefix, and a [GrantTarget.Module] covers the module's
 * verbs and the containers the module occupies, which only the host knows. [levelOn] takes that
 * knowledge as a function so the model stays complete without it.
 *
 * @property packageName The Android package the grant belongs to; the host reads it from the caller's UID.
 * @property webId The account the app acts as.
 * @property appLabel The app's display name when the grant was made, for the host's own lists.
 * @property entries What the app may do; never empty for a stored grant.
 * @property grantedAt The ISO-8601 instant the user approved the grant.
 */
@Parcelize
@Serializable
public data class AppGrant(
    val packageName: String,
    val webId: String,
    val appLabel: String,
    val entries: List<GrantEntry>,
    val grantedAt: String,
) : Parcelable {

    /** The level held on the whole pod, or `null` when no entry is [GrantTarget.Pod]. */
    public fun podLevel(): AccessLevel? = highestLevel { it is GrantTarget.Pod }

    /**
     * The highest level held on [resourceUri], or `null` when no entry covers it. [moduleRoots]
     * answers, for a module id, the container IRIs that module occupies on this account.
     */
    public fun levelOn(
        resourceUri: String,
        moduleRoots: (moduleId: String) -> List<String>,
    ): AccessLevel? = highestLevel { target ->
        when (target) {
            is GrantTarget.Pod -> true
            is GrantTarget.Resource -> target.covers(resourceUri)
            is GrantTarget.Module ->
                moduleRoots(target.id).any { root -> GrantTarget.Resource(root).covers(resourceUri) }
        }
    }

    /**
     * The highest level held on the data module [moduleId], through a pod-wide or a module
     * entry, or `null` when neither exists.
     */
    public fun levelOnModule(moduleId: String): AccessLevel? = highestLevel { target ->
        target is GrantTarget.Pod || (target is GrantTarget.Module && target.id == moduleId)
    }

    /** The highest level among the entries whose target satisfies [covers], or `null` when none does. */
    public fun highestLevel(covers: (GrantTarget) -> Boolean): AccessLevel? =
        AccessLevel.strongest(entries.filter { covers(it.target) }.map { it.level })
}
