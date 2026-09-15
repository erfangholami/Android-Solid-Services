package com.erfangholami.androidsolidservices.api.datamodule

import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.testing.InMemoryPodResourceManager
import com.erfangholami.androidsolidservices.api.testing.inMemoryPod
import com.erfangholami.androidsolidservices.shared.model.contacts.CONTACTS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.profile.WebId
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_DIRECTORY_SUFFIX
import com.erfangholami.androidsolidservices.shared.model.tickets.TICKETS_INDEX_NAME
import com.erfangholami.androidsolidservices.shared.model.typeindex.PrivateTypeIndex
import com.erfangholami.androidsolidservices.shared.model.typeindex.PublicTypeIndex
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A host decides whether a resource belongs to a module from these containers, so they have to
 * name every registered collection plus the root a fresh one would land in, and nothing else.
 */
class DataModuleRootContainersTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val storage = "https://alice.pod/"
    private val privateIndexUri = "https://alice.pod/settings/privateTypeIndex"
    private val publicIndexUri = "https://alice.pod/settings/publicTypeIndex"

    private fun podWithStorage(
        seedPrivate: PrivateTypeIndex.() -> Unit = {},
        seedPublic: PublicTypeIndex.() -> Unit = {},
    ): InMemoryPodResourceManager =
        inMemoryPod(webId, privateIndexUri, publicIndexUri, seedPrivateIndex = seedPrivate, seedPublicIndex = seedPublic)
            .apply {
                val profile = store[webId] as WebId
                put(WebId(webId, profile.getAllQuads() + RdfQuad(webId, PIM.STORAGE, storage)))
            }

    @Test
    fun `contacts reports each registered book's container and the allocation root`() = runBlocking {
        val pod = podWithStorage(
            seedPrivate = { addInstance(VCARD.ADDRESS_BOOK, "https://alice.pod/datamodule/contacts/b1/index.ttl#this") },
            seedPublic = { addInstance(VCARD.ADDRESS_BOOK, "https://alice.pod/shared/friends/index.ttl#this") },
        )

        val roots = SolidContactsDataModule.getInstance(pod).rootContainers(webId).getOrThrow()

        assertEquals(
            listOf(
                "https://alice.pod/datamodule/contacts/b1/",
                "https://alice.pod/shared/friends/",
                "$storage$CONTACTS_DIRECTORY_SUFFIX",
            ),
            roots,
        )
    }

    @Test
    fun `tickets reports each registered index's container and the allocation root`() = runBlocking {
        val pod = podWithStorage(
            seedPrivate = { addInstance(Schema.TICKET, "https://alice.pod/wallet/$TICKETS_INDEX_NAME") },
        )

        val roots = SolidTicketsDataModule.getInstance(pod).rootContainers(webId).getOrThrow()

        assertEquals(listOf("https://alice.pod/wallet/", "$storage$TICKETS_DIRECTORY_SUFFIX"), roots)
    }
}
