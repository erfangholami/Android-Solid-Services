package com.erfangholami.androidsolidservices.api.datamodule.typeindex

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.vocab.Solid

/**
 * Resolves (and bootstraps, when missing) a user's Solid type indexes.
 *
 * Shared by every data module that registers its instances in the private or
 * public type index (contacts address books, tickets containers, …). Resolution
 * follows the WebID profile: the type index link is looked up on the profile
 * document, then on the extended profile; when absent from both, the link is
 * written to the extended profile and an empty index resource is created under
 * the user's storage.
 */
internal object TypeIndexResolver {

    suspend fun getPrivateTypeIndex(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): PrivateTypeIndex {
        val webId =
            resourceManager.read(webIdString, webIdString, WebId::class.java)
                .getOrThrow()
        var privateTypeIndexUri = webId.getPrivateTypeIndex()

        if (privateTypeIndexUri == null) {
            val extendedProfile = resourceManager.read(
                webIdString,
                webId.getPrimaryTopicDocuments().firstOrNull() ?: webIdString,
                WebId::class.java
            ).getOrThrow()
            privateTypeIndexUri = extendedProfile.getPrivateTypeIndex()

            if (privateTypeIndexUri == null) {
                extendedProfile.setPrivateTypeIndex(webIdString, resolveStorage(resourceManager, webIdString, webId))
                privateTypeIndexUri = extendedProfile.getPrivateTypeIndex()
                val indexUri = requireNotNull(privateTypeIndexUri)
                registerTypeIndexLink(
                    resourceManager, webIdString, extendedProfile.getIdentifier(),
                    Solid.PRIVATE_TYPE_INDEX, indexUri,
                )
                ensureContainer(resourceManager, webIdString, indexUri)
                resourceManager.create(
                    webIdString,
                    PrivateTypeIndex(
                        indexUri,
                        "application/ld+json",
                        null,
                        null
                    )
                ).getOrThrow()
            }
        }

        return resourceManager.read(
            webIdString,
            privateTypeIndexUri.toString(),
            PrivateTypeIndex::class.java
        ).getOrThrow()
    }

    suspend fun getPublicTypeIndex(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): PublicTypeIndex {
        val webId =
            resourceManager.read(webIdString, webIdString, WebId::class.java)
                .getOrThrow()
        var publicTypeIndexUri = webId.getPublicTypeIndex()

        if (publicTypeIndexUri == null) {
            val extendedProfile = resourceManager.read(
                webIdString,
                webId.getPrimaryTopicDocuments().firstOrNull() ?: webIdString,
                WebId::class.java
            ).getOrThrow()
            publicTypeIndexUri = extendedProfile.getPublicTypeIndex()

            if (publicTypeIndexUri == null) {
                extendedProfile.setPublicTypeIndex(webIdString, resolveStorage(resourceManager, webIdString, webId))
                publicTypeIndexUri = extendedProfile.getPublicTypeIndex()
                val indexUri = requireNotNull(publicTypeIndexUri)
                registerTypeIndexLink(
                    resourceManager, webIdString, extendedProfile.getIdentifier(),
                    Solid.PUBLIC_TYPE_INDEX, indexUri,
                )
                ensureContainer(resourceManager, webIdString, indexUri)
                resourceManager.create(
                    webIdString,
                    PublicTypeIndex(
                        indexUri,
                        "application/ld+json",
                        null,
                        null
                    )
                ).getOrThrow()
            }
        }

        return resourceManager.read(
            webIdString,
            publicTypeIndexUri.toString(),
            PublicTypeIndex::class.java
        ).getOrThrow()
    }

    /**
     * Resolves the storage root the type index is allocated under, preferring the
     * profile's own `pim:storage` and falling back to [StorageDiscovery] (extended
     * profiles + a walk-up HEAD probe). Fails with a clear message instead of the old
     * `getStorages()[0]` `IndexOutOfBoundsException` when no storage can be found.
     */
    private suspend fun resolveStorage(
        resourceManager: SolidResourceManager,
        webIdString: String,
        profile: WebId,
    ): String =
        (profile.getStorages().firstOrNull()
            ?: StorageDiscovery.discover(resourceManager, webIdString))?.toString()
            ?: error("No pim:storage could be discovered for $webIdString")

    /**
     * Registers a type-index link on the profile via a targeted N3 PATCH that inserts
     * only the single `solid:privateTypeIndex` / `solid:publicTypeIndex` triple, rather
     * than a full-document PUT. A PUT would re-serialise and overwrite the whole profile
     * — silently dropping any concurrent or server-managed triple it can't round-trip —
     * and would need `acl:Write` on the entire document; the PATCH needs only to append
     * one triple.
     */
    private suspend fun registerTypeIndexLink(
        resourceManager: SolidResourceManager,
        webIdString: String,
        profileDocUri: String,
        predicate: String,
        indexUri: String,
    ) {
        val patch = N3Patch.build { insert(webIdString, predicate, indexUri) }
        resourceManager.patch(webIdString, profileDocUri, patch).getOrThrow()
    }

    /**
     * Ensures the parent container of [resourceUri] (and any missing ancestors) exists so a
     * freshly provisioned pod accepts the type-index bootstrap write, delegating to
     * [SolidResourceManager.ensureContainer].
     */
    private suspend fun ensureContainer(
        resourceManager: SolidResourceManager,
        ownerWebId: String,
        resourceUri: String,
    ) {
        val containerUri = resourceUri.substringBeforeLast('/') + "/"
        resourceManager.ensureContainer(ownerWebId, containerUri).getOrThrow()
    }
}
