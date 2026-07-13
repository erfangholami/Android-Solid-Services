package com.erfangholami.androidsolidservices.api.sharing.implementation

import android.util.Log
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantDirection
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantSource
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantStatus
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SAI
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import java.net.URI

/**
 * Reads Solid Application Interoperability (SAI) access grants for a user by
 * walking the registry graph advertised from their WebID profile:
 *
 * ```
 * WebID profile  --interop:hasRegistrySet-->  RegistrySet
 *   --interop:hasAgentRegistry-->  AgentRegistry
 *     --interop:hasSocialAgentRegistration / hasApplicationRegistration-->  Registration
 *       --interop:hasAccessGrant-->  AccessGrant
 *         --interop:hasDataGrant-->  DataGrant (access modes, data instances)
 * ```
 *
 * SAI deployment is rare across servers (CSS / NSS / Inrupt ESS), and the
 * graph is cross-pod and shape-tree-scoped rather than resource-centric, so
 * this reader is deliberately **best-effort and additive**: every step is
 * guarded, a failure at any node is logged with its reason and skipped, and a
 * pod without a registry set yields an empty list. It never throws — the
 * authoritative answer comes from the app's own indexes.
 *
 * Spec: https://solidproject.org/TR/sai
 */
internal class SaiAccessGrantReader(private val rm: SolidResourceManager) {

    private companion object {
        private const val SAI_LOG_TAG = "SaiAccessGrantReader"
    }

    suspend fun readAccessGrants(webId: String): List<AccessGrant> =
        runCatching { crawl(webId) }
            .onFailure { t ->
                Log.w(
                    SAI_LOG_TAG,
                    "readAccessGrants: SAI discovery failed for $webId; " +
                        "returning no SAI grants (authoritative grants come from the app indexes).",
                    t,
                )
            }
            .getOrDefault(emptyList())

    private suspend fun crawl(webId: String): List<AccessGrant> {
        val profile = rm.read(webId, webId, WebId::class.java).getOrThrow()
        val registrySetUri = profile.findProperty(SAI.HAS_REGISTRY_SET)?.let(::parseUriOrNull)
        if (registrySetUri == null) {
            Log.i(SAI_LOG_TAG, "crawl: $webId advertises no interop:hasRegistrySet; no SAI grants.")
            return emptyList()
        }

        val registrySet = rm.read(webId, registrySetUri.toString(), SolidRDFResource::class.java).getOrThrow()
        val agentRegistries = registrySet
            .findAllProperties(SAI.HAS_AGENT_REGISTRY)
            .mapNotNull(::parseUriOrNull)

        val grants = mutableListOf<AccessGrant>()
        agentRegistries.forEach { agentRegistryUri ->
            collectFromAgentRegistry(webId, agentRegistryUri, grants)
        }
        return grants
    }

    private suspend fun collectFromAgentRegistry(
        webId: String,
        agentRegistryUri: URI,
        out: MutableList<AccessGrant>,
    ) {
        runCatching {
            val agentRegistry =
                rm.read(webId, agentRegistryUri.toString(), SolidRDFResource::class.java).getOrThrow()
            val registrations = (
                agentRegistry.findAllProperties(SAI.HAS_SOCIAL_AGENT_REGISTRATION) +
                    agentRegistry.findAllProperties(SAI.HAS_APPLICATION_REGISTRATION)
                ).mapNotNull(::parseUriOrNull)
            registrations.forEach { registrationUri ->
                collectFromRegistration(webId, registrationUri, out)
            }
        }.onFailure { t ->
            Log.w(SAI_LOG_TAG, "collectFromAgentRegistry: read failed for $agentRegistryUri; skipping.", t)
        }
    }

    private suspend fun collectFromRegistration(
        webId: String,
        registrationUri: URI,
        out: MutableList<AccessGrant>,
    ) {
        runCatching {
            val registration =
                rm.read(webId, registrationUri.toString(), SolidRDFResource::class.java).getOrThrow()
            val grantUris = registration
                .findAllProperties(SAI.HAS_ACCESS_GRANT)
                .mapNotNull(::parseUriOrNull)
            grantUris.forEach { grantUri ->
                runCatching { parseGrant(webId, grantUri)?.let(out::add) }
                    .onFailure { t ->
                        Log.w(SAI_LOG_TAG, "collectFromRegistration: grant parse failed for $grantUri; skipping.", t)
                    }
            }
        }.onFailure { t ->
            Log.w(SAI_LOG_TAG, "collectFromRegistration: read failed for $registrationUri; skipping.", t)
        }
    }

    private suspend fun parseGrant(webId: String, grantUri: URI): AccessGrant? {
        val rdf = rm.read(webId, grantUri.toString(), SolidRDFResource::class.java).getOrThrow()
        val grantSubject = rdf.getAllQuads()
            .firstOrNull { it.predicate == RDF.TYPE && it.`object` == SAI.ACCESS_GRANT }
            ?.subject
            ?: return null

        val grantee = rdf.findPropertyForSubject(grantSubject, SAI.GRANTEE) ?: return null
        val dataOwner = rdf.findPropertyForSubject(grantSubject, SAI.DATA_OWNER)
        val grantedAt = rdf.findPropertyForSubject(grantSubject, SAI.GRANTED_AT)

        val direction = when {
            IriUtils.sameIri(grantee, webId) -> AccessGrantDirection.RECEIVED
            dataOwner != null && IriUtils.sameIri(dataOwner, webId) -> AccessGrantDirection.GIVEN
            else -> AccessGrantDirection.RECEIVED
        }
        val counterpart = when (direction) {
            AccessGrantDirection.RECEIVED -> dataOwner ?: grantee
            else -> grantee
        }

        val dataGrantSubjects = rdf.findAllPropertiesForSubject(grantSubject, SAI.HAS_DATA_GRANT)
        val modeIris = (
            rdf.findAllPropertiesForSubject(grantSubject, SAI.ACCESS_MODE) +
                dataGrantSubjects.flatMap { rdf.findAllPropertiesForSubject(it, SAI.ACCESS_MODE) }
            ).toSet()
        val mode = ShareMode.strongest(modeIris) ?: ShareMode.READ

        val resourceUri = dataGrantSubjects
            .firstNotNullOfOrNull { rdf.findPropertyForSubject(it, SAI.HAS_DATA_INSTANCE) }
            ?: grantUri.toString()

        return AccessGrant(
            direction = direction,
            counterpartWebId = counterpart,
            resourceUri = resourceUri,
            mode = mode,
            status = AccessGrantStatus.ACTIVE,
            source = AccessGrantSource.SAI_REGISTRY,
            grantedAt = grantedAt,
            requestUri = null,
        )
    }

    private fun parseUriOrNull(value: String): URI? =
        runCatching { URI.create(value) }
            .onFailure { t -> Log.w(SAI_LOG_TAG, "parseUriOrNull: malformed IRI '$value'; skipping.", t) }
            .getOrNull()
}
