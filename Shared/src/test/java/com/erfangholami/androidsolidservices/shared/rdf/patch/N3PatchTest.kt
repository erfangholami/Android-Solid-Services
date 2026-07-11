package com.erfangholami.androidsolidservices.shared.rdf.patch

import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Tests the N3 Patch builder's escaping (the injection boundary — patches are
 * built from user-controlled contact names, notes, etc.) and its serialization
 * to both N3 and SPARQL Update.
 */
class N3PatchTest {

    private val subject = "https://alice.pod/contacts/p1#this"

    @Test
    fun `literal terms escape quotes, newlines, tabs, and backslashes`() {
        val n3 = N3Patch.build {
            insertLiteral(subject, VCARD.NOTE, "a\"b\nc\td\\e")
        }.toN3String()

        // a"b<NL>c<TAB>d\e  →  a\"b\nc\td\\e   (each control neutralised)
        assertTrue(n3.contains("""a\"b\nc\td\\e"""))
    }

    @Test
    fun `an injected quote cannot break out of the literal`() {
        val malicious = """x" . <urn:evil> <urn:evil> <urn:evil> ."""
        val n3 = N3Patch.build {
            insertLiteral(subject, VCARD.FN, malicious)
        }.toN3String()

        // The closing quote is escaped, so the injected triple stays inert text.
        assertTrue("the injected quote must be escaped", n3.contains("""x\" ."""))
        assertFalse(
            "the injected value must not appear as an unescaped break-out",
            n3.contains("""x" . <urn:evil>"""),
        )
    }

    @Test
    fun `data-only patch renders DELETE DATA and INSERT DATA`() {
        val sparql = N3Patch.build {
            delete(subject, VCARD.FN, "urn:old")
            insertLiteral(subject, VCARD.FN, "Alice")
        }.toSparqlUpdate()

        assertTrue(sparql.contains("DELETE DATA {"))
        assertTrue(sparql.contains("INSERT DATA {"))
        assertFalse(sparql.contains("WHERE {"))
    }

    @Test
    fun `a where-bound patch renders DELETE INSERT WHERE`() {
        val sparql = N3Patch.build {
            where(subject, VCARD.FN, variable = "old")
            deleteVar(subject, VCARD.FN, variable = "old")
            insertLiteral(subject, VCARD.FN, "Alice")
        }.toSparqlUpdate()

        assertTrue(sparql.contains("DELETE {"))
        assertTrue(sparql.contains("INSERT {"))
        assertTrue(sparql.contains("WHERE {"))
        assertTrue("the bound variable must appear", sparql.contains("?old"))
    }

    @Test
    fun `n3 document declares the InsertDeletePatch shape`() {
        val n3 = N3Patch.build {
            insertLiteral(subject, VCARD.FN, "Alice")
        }.toN3String()

        assertTrue(n3.contains("a solid:InsertDeletePatch"))
        assertTrue(n3.contains("solid:inserts"))
    }

    @Test
    fun `a typed literal carries its datatype`() {
        val n3 = N3Patch.build {
            insertLiteral(subject, VCARD.BIRTHDAY, "1990-05-01", datatype = XSD.DATE)
        }.toN3String()
        assertTrue(n3.contains(""""1990-05-01"^^<${XSD.DATE}>"""))
    }

    @Test
    fun `fromDiff turns removed triples into deletes and added into inserts`() {
        val original = RDFResource(
            URI.create(subject), "text/turtle",
            listOf(RdfQuad(subject, VCARD.FN, "Old", datatype = XSD.STRING)), null,
        )
        val updated = RDFResource(
            URI.create(subject), "text/turtle",
            listOf(RdfQuad(subject, VCARD.FN, "New", datatype = XSD.STRING)), null,
        )

        val patch = N3Patch.fromDiff(original, updated)
        assertTrue(patch.deletes!!.contains("\"Old\""))
        assertTrue(patch.inserts!!.contains("\"New\""))
    }

    @Test
    fun `fromDiff on identical states throws`() {
        val quads = listOf(RdfQuad(subject, VCARD.FN, "Same", datatype = XSD.STRING))
        val a = RDFResource(URI.create(subject), "text/turtle", quads, null)
        val b = RDFResource(URI.create(subject), "text/turtle", quads, null)
        assertThrows(IllegalArgumentException::class.java) { N3Patch.fromDiff(a, b) }
    }

    @Test
    fun `an empty patch is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { N3Patch(deletes = null, inserts = null) }
    }

    @Test
    fun `contentType is text n3`() {
        assertEquals("text/n3", N3Patch.insert("<a> <b> <c> .").contentType)
    }
}
