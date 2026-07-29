package com.erfangholami.androidsolidservices.api.datamodule.typeindex

import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.resource.implementation.StorageDiscovery
import com.erfangholami.androidsolidservices.api.resource.implementation.casUpdate
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.SettingTypeIndex
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.vocab.Solid

internal object TypeIndexResolver {

    suspend fun getPrivateTypeIndex(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): PrivateTypeIndex =
        resourceManager.read(
            webIdString,
            resolvePrivateTypeIndexUri(resourceManager, webIdString),
            PrivateTypeIndex::class.java
        ).getOrThrow()

    suspend fun getPublicTypeIndex(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): PublicTypeIndex =
        resourceManager.read(
            webIdString,
            resolvePublicTypeIndexUri(resourceManager, webIdString),
            PublicTypeIndex::class.java
        ).getOrThrow()

    suspend fun addInstance(
        resourceManager: SolidResourceManager,
        webIdString: String,
        forClass: String,
        instanceUri: String,
        isPrivate: Boolean,
    ) {
        val indexUri = resolveTypeIndexUri(resourceManager, webIdString, isPrivate)
        mutate(resourceManager, webIdString, indexUri, isPrivate) { index ->
            if (instanceUri in index.getInstances(forClass)) false
            else index.addInstance(forClass, instanceUri).let { true }
        }
    }

    suspend fun addInstanceContainer(
        resourceManager: SolidResourceManager,
        webIdString: String,
        forClass: String,
        containerUri: String,
        isPrivate: Boolean,
    ) {
        val indexUri = resolveTypeIndexUri(resourceManager, webIdString, isPrivate)
        mutate(resourceManager, webIdString, indexUri, isPrivate) { index ->
            if (containerUri in index.getInstanceContainers(forClass)) false
            else index.addInstanceContainer(forClass, containerUri).let { true }
        }
    }

    suspend fun removeResource(
        resourceManager: SolidResourceManager,
        webIdString: String,
        resourceUri: String,
    ) {
        val drop: (SettingTypeIndex) -> Boolean = { index ->
            if (!index.containsResource(resourceUri)) false
            else index.removeResource(resourceUri).let { true }
        }

        findTypeIndexUri(resourceManager, webIdString, isPrivate = true)?.let { uri ->
            var removed = false
            mutate(resourceManager, webIdString, uri, isPrivate = true) { index ->
                drop(index).also { removed = removed || it }
            }
            if (removed) return
        }

        findTypeIndexUri(resourceManager, webIdString, isPrivate = false)?.let { uri ->
            mutate(resourceManager, webIdString, uri, isPrivate = false, change = drop)
        }
    }

    private suspend fun mutate(
        resourceManager: SolidResourceManager,
        webIdString: String,
        indexUri: String,
        isPrivate: Boolean,
        change: (SettingTypeIndex) -> Boolean,
    ) {
        if (isPrivate) {
            resourceManager.casUpdate(
                webId = webIdString,
                read = { resourceManager.read(webIdString, indexUri, PrivateTypeIndex::class.java) },
                mutate = change,
            ).getOrThrow()
        } else {
            resourceManager.casUpdate(
                webId = webIdString,
                read = { resourceManager.read(webIdString, indexUri, PublicTypeIndex::class.java) },
                mutate = change,
            ).getOrThrow()
        }
    }

    private suspend fun resolveTypeIndexUri(
        resourceManager: SolidResourceManager,
        webIdString: String,
        isPrivate: Boolean,
    ): String =
        if (isPrivate) resolvePrivateTypeIndexUri(resourceManager, webIdString)
        else resolvePublicTypeIndexUri(resourceManager, webIdString)

    private suspend fun findTypeIndexUri(
        resourceManager: SolidResourceManager,
        webIdString: String,
        isPrivate: Boolean,
    ): String? {
        val link: (WebId) -> String? =
            if (isPrivate) WebId::getPrivateTypeIndex else WebId::getPublicTypeIndex
        val profile =
            resourceManager.read(webIdString, webIdString, WebId::class.java).getOrThrow()
        link(profile)?.let { return it }
        return link(extendedProfile(resourceManager, webIdString, profile))
    }

    private suspend fun extendedProfile(
        resourceManager: SolidResourceManager,
        webIdString: String,
        profile: WebId,
    ): WebId = resourceManager.read(
        webIdString,
        profile.getPrimaryTopicDocuments().firstOrNull() ?: webIdString,
        WebId::class.java,
    ).getOrThrow()

    private suspend fun resolvePrivateTypeIndexUri(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): String {
        val webId =
            resourceManager.read(webIdString, webIdString, WebId::class.java)
                .getOrThrow()
        webId.getPrivateTypeIndex()?.let { return it }

        val extendedProfile = resourceManager.read(
            webIdString,
            webId.getPrimaryTopicDocuments().firstOrNull() ?: webIdString,
            WebId::class.java
        ).getOrThrow()
        extendedProfile.getPrivateTypeIndex()?.let { return it }

        extendedProfile.setPrivateTypeIndex(
            webIdString,
            resolveStorage(resourceManager, webIdString, webId),
        )
        val indexUri = requireNotNull(extendedProfile.getPrivateTypeIndex())
        registerTypeIndexLink(
            resourceManager, webIdString, extendedProfile.getIdentifier(),
            Solid.PRIVATE_TYPE_INDEX, indexUri,
        )
        ensureContainer(resourceManager, webIdString, indexUri)
        createIfAbsent(
            resourceManager, webIdString,
            PrivateTypeIndex(indexUri, "application/ld+json", null, null),
        )
        return indexUri
    }

    private suspend fun resolvePublicTypeIndexUri(
        resourceManager: SolidResourceManager,
        webIdString: String,
    ): String {
        val webId =
            resourceManager.read(webIdString, webIdString, WebId::class.java)
                .getOrThrow()
        webId.getPublicTypeIndex()?.let { return it }

        val extendedProfile = resourceManager.read(
            webIdString,
            webId.getPrimaryTopicDocuments().firstOrNull() ?: webIdString,
            WebId::class.java
        ).getOrThrow()
        extendedProfile.getPublicTypeIndex()?.let { return it }

        extendedProfile.setPublicTypeIndex(
            webIdString,
            resolveStorage(resourceManager, webIdString, webId),
        )
        val indexUri = requireNotNull(extendedProfile.getPublicTypeIndex())
        registerTypeIndexLink(
            resourceManager, webIdString, extendedProfile.getIdentifier(),
            Solid.PUBLIC_TYPE_INDEX, indexUri,
        )
        ensureContainer(resourceManager, webIdString, indexUri)
        createIfAbsent(
            resourceManager, webIdString,
            PublicTypeIndex(indexUri, "application/ld+json", null, null),
        )
        return indexUri
    }

    private suspend fun <T : Resource> createIfAbsent(
        resourceManager: SolidResourceManager,
        webIdString: String,
        index: T,
    ) {
        when (val response = resourceManager.create(webIdString, index)) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure ->
                if (response.error.code != SolidErrorCode.CONFLICT) response.getOrThrow()
        }
    }

    private suspend fun resolveStorage(
        resourceManager: SolidResourceManager,
        webIdString: String,
        profile: WebId,
    ): String =
        profile.getStorages().firstOrNull()
            ?: StorageDiscovery.discover(resourceManager, webIdString)
            ?: error("No pim:storage could be discovered for $webIdString")

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

    private suspend fun ensureContainer(
        resourceManager: SolidResourceManager,
        ownerWebId: String,
        resourceUri: String,
    ) {
        val containerUri = resourceUri.substringBeforeLast('/') + "/"
        resourceManager.ensureContainer(ownerWebId, containerUri).getOrThrow()
    }
}
