package com.erfangholami.androidsolidservices.api.datamodule.typeindex

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import java.net.URI

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
            resourceManager.read(webIdString, URI.create(webIdString), WebId::class.java)
                .getOrThrow()
        var privateTypeIndexUri = webId.getPrivateTypeIndex()

        if (privateTypeIndexUri == null) {
            val extendedProfile = resourceManager.read(
                webIdString,
                webId.getPrimaryTopicDocuments().firstOrNull() ?: URI.create(webIdString),
                WebId::class.java
            ).getOrThrow()
            privateTypeIndexUri = extendedProfile.getPrivateTypeIndex()

            if (privateTypeIndexUri == null) {
                extendedProfile.setPrivateTypeIndex(webIdString, webId.getStorages()[0].toString())
                resourceManager.update(webIdString, extendedProfile).getOrThrow()
                privateTypeIndexUri = extendedProfile.getPrivateTypeIndex()
                val indexUri = requireNotNull(privateTypeIndexUri)
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
            privateTypeIndexUri,
            PrivateTypeIndex::class.java
        ).getOrThrow()
    }

    suspend fun getPublicTypeIndex(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): PublicTypeIndex {
        val webId =
            resourceManager.read(webIdString, URI.create(webIdString), WebId::class.java)
                .getOrThrow()
        var publicTypeIndexUri = webId.getPublicTypeIndex()

        if (publicTypeIndexUri == null) {
            val extendedProfile = resourceManager.read(
                webIdString,
                webId.getPrimaryTopicDocuments().firstOrNull() ?: URI.create(webIdString),
                WebId::class.java
            ).getOrThrow()
            publicTypeIndexUri = extendedProfile.getPublicTypeIndex()

            if (publicTypeIndexUri == null) {
                extendedProfile.setPublicTypeIndex(webIdString, webId.getStorages()[0].toString())
                resourceManager.update(webIdString, extendedProfile).getOrThrow()
                publicTypeIndexUri = extendedProfile.getPublicTypeIndex()
                val indexUri = requireNotNull(publicTypeIndexUri)
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
            publicTypeIndexUri,
            PublicTypeIndex::class.java
        ).getOrThrow()
    }

    /**
     * Ensures the parent container of [resourceUri] exists, creating it as an LDP
     * BasicContainer when a HEAD reports it missing (404). Servers that do not
     * auto-create intermediate containers on PUT would otherwise reject the type-index
     * bootstrap write on a freshly provisioned pod.
     */
    private suspend fun ensureContainer(
        resourceManager: SolidResourceManager,
        ownerWebId: String,
        resourceUri: URI,
    ) {
        val container = resourceUri.toString().substringBeforeLast('/') + "/"
        val containerUri = URI.create(container)
        val missing = resourceManager.head(ownerWebId, containerUri).let {
            it is SolidResult.Failure && it.error.code == SolidErrorCode.NOT_FOUND
        }
        if (missing) {
            resourceManager.create(ownerWebId, SolidContainer(containerUri)).getOrThrow()
        }
    }
}
