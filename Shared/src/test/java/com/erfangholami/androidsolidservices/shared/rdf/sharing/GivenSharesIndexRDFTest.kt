package com.erfangholami.androidsolidservices.shared.rdf.sharing

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.SolidShareVocabulary
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GivenSharesIndexRDFTest {

    private val index = "https://alice.pod/solidshare/shares/given_shares.ttl"
    private val bob = "https://bob.pod/profile/card#me"
    private val resource = "https://alice.pod/notes/n1"
    private val created = "2026-06-04T12:00:00Z"

    private val v = SolidShareVocabulary

    private fun indexOf(quads: List<RdfQuad>) = GivenSharesIndexRDF(index, quads = quads)

    @Test
    fun `a reified record exposes its modes and creation time`() {
        val node = "$index#share-1"
        val rdf = indexOf(
            listOf(
                RdfQuad(node, RDF.TYPE, v.shareType),
                RdfQuad(node, v.resource, resource),
                RdfQuad(node, v.receiver, bob),
                RdfQuad(node, ACL.MODE, ACL.READ),
                RdfQuad(node, ACL.MODE, ACL.APPEND),
                RdfQuad(node, DC.CREATED, created, datatype = XSD.DATE_TIME),
            ),
        )

        val record = rdf.getShareNodes().single()
        assertEquals(setOf(ShareMode.READ, ShareMode.APPEND), record.modes)
        assertEquals(created, record.createdAt)
        assertEquals(ShareReceiver.WebIdReceiver(bob), record.receiver)

        val share = rdf.getShares().single()
        assertEquals(ShareMode.APPEND, share.mode)
        assertEquals(created, share.createdAt)
    }

    @Test
    fun `a legacy bare-triple share is surfaced with no timestamp`() {
        val rdf = indexOf(listOf(RdfQuad(bob, ACL.READ, resource)))

        val legacy = rdf.getLegacyFlatShares().single()
        assertEquals(ShareReceiver.WebIdReceiver(bob), legacy.receiver)
        assertEquals(ShareMode.READ, legacy.mode)
        assertEquals(resource, legacy.resourceUri)

        val share = rdf.getShares().single()
        assertEquals(ShareMode.READ, share.mode)
        assertNull(share.createdAt)
    }

    @Test
    fun `a record supersedes a legacy row for the same pair`() {
        val node = "$index#share-1"
        val rdf = indexOf(
            listOf(
                RdfQuad(node, RDF.TYPE, v.shareType),
                RdfQuad(node, v.resource, resource),
                RdfQuad(node, v.receiver, bob),
                RdfQuad(node, ACL.MODE, ACL.WRITE),
                RdfQuad(bob, ACL.READ, resource),
            ),
        )

        val shares = rdf.getShares()
        assertEquals(1, shares.size)
        assertEquals(ShareMode.WRITE, shares.single().mode)
    }

    @Test
    fun `a group receiver is recognised from its vcard Group marker`() {
        val node = "$index#share-1"
        val group = "https://alice.pod/contacts/groups/friends"
        val rdf = indexOf(
            listOf(
                RdfQuad(node, RDF.TYPE, v.shareType),
                RdfQuad(node, v.resource, resource),
                RdfQuad(node, v.receiver, group),
                RdfQuad(node, ACL.MODE, ACL.READ),
                RdfQuad(group, RDF.TYPE, VCARD.GROUP),
            ),
        )

        assertEquals(ShareReceiver.GroupReceiver(group), rdf.getShareNodes().single().receiver)
    }

    @Test
    fun `an empty index has no shares`() {
        assertEquals(emptyList<Any>(), indexOf(emptyList()).getShares())
    }
}
