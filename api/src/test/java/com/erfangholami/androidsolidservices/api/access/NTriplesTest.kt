package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Round-trip and escaping tests for the hand-rolled [NTriples] codec that
 * serialises every ACL/ACR this library writes. A serialize→parse cycle must
 * reproduce the exact quads, including literals containing control characters.
 */
class NTriplesTest {

    private val s = "https://alice.pod/r"
    private val p = "https://example.org/prop"

    private fun roundTrip(quads: List<RdfQuad>): List<RdfQuad> =
        NTriples.parse(NTriples.serialize(quads))

    @Test
    fun `an IRI triple round-trips`() {
        val q = RdfQuad(s, p, "https://example.org/obj")
        assertEquals(listOf(q), roundTrip(listOf(q)))
    }

    @Test
    fun `a typed literal keeps its datatype`() {
        val q = RdfQuad(s, p, "42", datatype = "http://www.w3.org/2001/XMLSchema#integer")
        assertEquals(listOf(q), roundTrip(listOf(q)))
    }

    @Test
    fun `an xsd string literal omits the datatype on the wire but parses back as string`() {
        val q = RdfQuad(s, p, "hello", datatype = XSD.STRING)
        val out = NTriples.serialize(listOf(q))
        assertFalse("xsd:string is the default and should not be emitted", out.contains("XMLSchema#string"))
        assertEquals(listOf(q), NTriples.parse(out))
    }

    @Test
    fun `a language-tagged literal round-trips`() {
        val q = RdfQuad(s, p, "bonjour", language = "fr")
        val out = NTriples.serialize(listOf(q))
        assertTrue(out.contains("\"bonjour\"@fr"))
        assertEquals(listOf(q), NTriples.parse(out))
    }

    @Test
    fun `control characters in a literal survive the round-trip`() {
        val q = RdfQuad(s, p, "line1\nline2\ttab \"quote\" back\\slash", datatype = XSD.STRING)
        assertEquals(listOf(q), roundTrip(listOf(q)))
    }

    @Test
    fun `blank-node subject and object round-trip`() {
        val q = RdfQuad("_:b0", p, "_:b1")
        assertEquals(listOf(q), roundTrip(listOf(q)))
    }

    @Test
    fun `parse skips comments and blank lines`() {
        val text = "# a comment\n\n<$s> <$p> <https://example.org/o> .\n"
        assertEquals(1, NTriples.parse(text).size)
    }

    @Test
    fun `parse resolves relative IRIs against the base`() {
        val quads = NTriples.parse("<#auth> <$p> <thing> .\n", URI.create("https://alice.pod/doc"))
        assertEquals("https://alice.pod/doc#auth", quads.single().subject)
        assertEquals("https://alice.pod/thing", quads.single().`object`)
    }

    @Test
    fun `a full authorization set round-trips as a whole`() {
        val quads = listOf(
            RdfQuad(s, RDF.TYPE, ACL.AUTHORIZATION),
            RdfQuad(s, ACL.MODE, ACL.READ),
            RdfQuad(s, ACL.MODE, ACL.WRITE),
            RdfQuad(s, ACL.AGENT, "https://bob.pod/profile/card#me"),
        )
        assertEquals(quads.toSet(), roundTrip(quads).toSet())
    }
}
