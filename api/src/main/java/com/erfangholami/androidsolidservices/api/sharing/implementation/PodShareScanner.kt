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

internal class PodShareScanner(
    private val rm: SolidResourceManager,
    private val helper: SharingManagerHelper,
    private val profile: SharingProfile,
) {

    data class PodScan(
        val shares: List<GivenShare>,
        val observedResources: Set<String>,
        val complete: Boolean,
    )

    private data class NodeObservation(
        val shares: List<GivenShare>,
        val children: List<String>,
        val observed: Boolean,
        val complete: Boolean,
    )

    fun isExcludedFromScan(resourceUri: String): Boolean =
        profile.storageLayout.excludedScanPaths().any { resourceUri.contains(it) }

    suspend fun scanPod(webId: String, root: String): PodScan =
        scanFrontier(webId, listOf(root), Semaphore(MAX_CONCURRENT_NODE_READS))

    private suspend fun scanFrontier(
        webId: String,
        frontier: List<String>,
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
            .map { (node, _) -> node }
            .toSet()

        val deeper = scanFrontier(webId, observations.flatMap { it.children }, gate)
        return PodScan(
            shares = observations.flatMap { it.shares } + deeper.shares,
            observedResources = observedHere + deeper.observedResources,
            complete = observations.all { it.complete } && deeper.complete,
        )
    }

    private suspend fun visitNode(webId: String, node: String): NodeObservation {
        if (isExcludedFromScan(node)) {
            return NodeObservation(emptyList(), emptyList(), observed = false, complete = true)
        }

        var complete = true
        var observed = true
        val live = runCatching { helper.getSharesFromAcl(webId, node) }
            .onFailure { t ->
                Log.w(
                    TAG,
                    "scanPod: ACL read failed for $node (e.g. 403 from a deleted/" +
                            "locked ACL); skipping this resource and preserving its stored " +
                            "index rows. The rest of the pod is still walked.",
                    t,
                )
                complete = false
                observed = false
            }
            .getOrDefault(emptyList())

        if (!node.endsWith("/")) return NodeObservation(live, emptyList(), observed, complete)

        val container = runCatching {
            rm.read(webId, node, SolidContainer::class.java).getOrThrow()
        }.onFailure { t ->
            Log.w(
                TAG,
                "scanPod: container listing failed for $node; its subtree is " +
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
                .map { ref.identifier }
                .getOrNull()
        }
        return NodeObservation(live, children, observed, complete)
    }

    private companion object {
        const val TAG = "SharingManager"
        const val MAX_CONCURRENT_NODE_READS = 8
    }
}
