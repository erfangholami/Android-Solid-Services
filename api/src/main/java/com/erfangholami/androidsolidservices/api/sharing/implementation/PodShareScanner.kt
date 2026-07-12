package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.SharingProfile
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI

/**
 * Walks a pod breadth-first (bounded concurrency) reading every resource's effective ACL, so
 * [SharingManagerImplementation.rebuildGivenIndex] can reconstruct the given-shares index from
 * the live grants on the pod. Split out of the sharing facade; behaviour is unchanged.
 *
 * A node that can't be read (403 on a locked ACL, a failed container listing) is skipped and left
 * *unobserved* so its stored index rows are preserved rather than pruned; that also flips the
 * scan's [PodScan.complete] flag so the caller can log the gap.
 */
internal class PodShareScanner(
    private val rm: SolidResourceManager,
    private val helper: SharingManagerHelper,
    private val profile: SharingProfile,
) {

    data class PodScan(
        /** Every share observed across the entire tree. */
        val shares: List<GivenShare>,
        /**
         * URIs whose effective ACL was read authoritatively. Only these are reconciled against
         * the stored index; unread resources keep their rows.
         */
        val observedResources: Set<String>,
        /**
         * `true` only if the entire tree was fully observed. A single unreadable branch flips it
         * to `false`, allowing the caller to log the gap.
         */
        val complete: Boolean,
    )

    private data class NodeObservation(
        val shares: List<GivenShare>,
        val children: List<URI>,
        val observed: Boolean,
        val complete: Boolean,
    )

    /**
     * Whether [resourceUri] matches one of the active profile's excluded scan paths (the engine's
     * own bookkeeping, the inbox, the public profile document). Drives both halves of the
     * exclusion: the walk never descends into such a resource, and a rebuild prunes any index row
     * already stored for one instead of treating it as a user-managed share.
     */
    fun isExcludedFromScan(resourceUri: String): Boolean =
        profile.storageLayout.excludedScanPaths().any { resourceUri.contains(it) }

    suspend fun scanPod(webId: String, root: URI): PodScan =
        scanFrontier(webId, listOf(root), Semaphore(MAX_CONCURRENT_NODE_READS))

    private suspend fun scanFrontier(
        webId: String,
        frontier: List<URI>,
        gate: Semaphore,
    ): PodScan {
        if (frontier.isEmpty()) return PodScan(emptyList(), emptySet(), complete = true)

        val observations = coroutineScope {
            frontier.map { node ->
                async { gate.withPermit { visitNode(webId, node) } }
            }.awaitAll()
        }

        val observedHere = frontier.zip(observations)
            .filter { (_, obs) -> obs.observed }
            .map { (node, _) -> node.toString() }
            .toSet()

        val deeper = scanFrontier(webId, observations.flatMap { it.children }, gate)
        return PodScan(
            shares = observations.flatMap { it.shares } + deeper.shares,
            observedResources = observedHere + deeper.observedResources,
            complete = observations.all { it.complete } && deeper.complete,
        )
    }

    private suspend fun visitNode(webId: String, node: URI): NodeObservation {
        val nodeStr = node.toString()
        if (isExcludedFromScan(nodeStr)) {
            return NodeObservation(emptyList(), emptyList(), observed = false, complete = true)
        }

        var complete = true
        var observed = true
        val live = runCatching { helper.getSharesFromAcl(webId, node) }
            .onFailure { t ->
                Log.w(
                    TAG,
                    "scanPod: ACL read failed for $nodeStr (e.g. 403 from a deleted/" +
                            "locked ACL); skipping this resource and preserving its stored " +
                            "index rows. The rest of the pod is still walked.",
                    t,
                )
                complete = false
                observed = false
            }
            .getOrDefault(emptyList())

        if (!nodeStr.endsWith("/")) return NodeObservation(live, emptyList(), observed, complete)

        val container = runCatching {
            rm.read(webId, node, SolidContainer::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                TAG,
                "scanPod: container listing failed for $nodeStr; its subtree is " +
                        "unobserved (scan is partial), but the rest of the pod is still walked.",
                t,
            )
        }.getOrNull() ?: return NodeObservation(live, emptyList(), observed, complete = false)

        val children = container.getContained().mapNotNull { ref ->
            runCatching { URI.create(ref.identifier) }
                .onFailure { t ->
                    Log.w(TAG, "scanPod: malformed child URI '${ref.identifier}'; scan is partial.", t)
                    complete = false
                }
                .getOrNull()
        }
        return NodeObservation(live, children, observed, complete)
    }

    private companion object {
        const val TAG = "SharingManager"
        const val MAX_CONCURRENT_NODE_READS = 8
    }
}
