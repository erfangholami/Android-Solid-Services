package com.erfangholami.androidsolidservices.api.resource.implementation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The converter runs exactly once per 415 — when a server has refused `text/n3` and the only way
 * to deliver the caller's patch is to restate it as SPARQL Update. A wrong translation here
 * silently edits the wrong triples on the pod, so every shape the standard document allows gets
 * pinned; anything the converter cannot read with confidence must yield null, keeping the
 * server's honest 415 instead of a guessed rewrite.
 */
class N3PatchConverterTest {

    @Test
    fun `an insert-only patch becomes INSERT DATA`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#> .
            _:patch a solid:InsertDeletePatch ;
              solid:inserts { <https://a.example/x> <http://schema.org/name> "hello" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(sparql.contains("INSERT DATA {"))
        assertTrue(sparql.contains("""<https://a.example/x> <http://schema.org/name> "hello" ."""))
        assertTrue("insert-only must not emit the WHERE form ESS rejects", !sparql.contains("WHERE"))
    }

    @Test
    fun `deletes and inserts become a DELETE DATA then INSERT DATA sequence`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#> .
            _:p a solid:InsertDeletePatch ;
              solid:deletes { <https://a.example/x> <http://schema.org/name> "old" . } ;
              solid:inserts { <https://a.example/x> <http://schema.org/name> "new" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        val deleteAt = sparql.indexOf("DELETE DATA {")
        val insertAt = sparql.indexOf("INSERT DATA {")
        assertTrue(deleteAt >= 0 && insertAt >= 0)
        assertTrue("N3 Patch applies deletions before insertions", deleteAt < insertAt)
        assertTrue("operations must be separated as one update sequence", sparql.contains(";"))
    }

    @Test
    fun `a conditional patch keeps its WHERE clause and variables`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#> .
            _:p a solid:InsertDeletePatch ;
              solid:where   { <https://a.example/x> <http://schema.org/name> ?old . } ;
              solid:deletes { <https://a.example/x> <http://schema.org/name> ?old . } ;
              solid:inserts { <https://a.example/x> <http://schema.org/name> "new" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(sparql.contains("WHERE {"))
        assertTrue(sparql.contains("?old"))
        assertTrue("the conditional form must not degrade to DATA", !sparql.contains("INSERT DATA"))
    }

    @Test
    fun `declared prefixes travel along as PREFIX lines`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#> .
            @prefix schema: <http://schema.org/> .
            _:p a solid:InsertDeletePatch ;
              solid:inserts { <https://a.example/x> schema:name "hello" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(
            "the formula uses schema:name, so the prefix must be declared in SPARQL too",
            sparql.contains("PREFIX schema: <http://schema.org/>"),
        )
    }

    @Test
    fun `any prefix bound to the solid namespace is recognised`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix p: <http://www.w3.org/ns/solid/terms#> .
            _:x a p:InsertDeletePatch ;
              p:inserts { <https://a.example/x> <http://schema.org/name> "hello" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(sparql.contains("INSERT DATA {"))
    }

    @Test
    fun `full-IRI clause predicates work without any prefix`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            <> a <http://www.w3.org/ns/solid/terms#InsertDeletePatch> ;
              <http://www.w3.org/ns/solid/terms#inserts> { <https://a.example/x> <http://schema.org/name> "hi" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(sparql.contains("INSERT DATA {"))
    }

    @Test
    fun `braces and clause keywords inside literals do not confuse the extraction`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#> .
            # a comment mentioning solid:inserts { and an unbalanced brace
            _:p a solid:InsertDeletePatch ;
              solid:inserts { <https://a.example/x> <http://schema.org/text> "a } brace, a { brace, solid:deletes" . } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(sparql.contains(""""a } brace, a { brace, solid:deletes""""))
        assertTrue("the literal's text must not have been read as a deletes clause", !sparql.contains("DELETE"))
    }

    @Test
    fun `the formula content is passed through verbatim`() {
        val sparql = N3PatchConverter.toSparqlUpdate(
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#> .
            _:p a solid:InsertDeletePatch ;
              solid:inserts {
                <https://a.example/x> a <http://schema.org/Note> .
                <https://a.example/x> <http://schema.org/dateCreated> "2026-07-31T10:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime> .
                <https://a.example/x> <http://www.w3.org/2000/01/rdf-schema#label> "Une note"@fr .
              } .
            """.trimIndent(),
        )

        requireNotNull(sparql)
        assertTrue(sparql.contains("\"2026-07-31T10:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>"))
        assertTrue(sparql.contains("\"Une note\"@fr"))
    }

    @Test
    fun `a document with no patch clauses is rejected`() {
        assertNull(N3PatchConverter.toSparqlUpdate("<https://a.example/x> <http://schema.org/name> \"just a triple\" ."))
        assertNull(N3PatchConverter.toSparqlUpdate("this is not N3 at all"))
        assertNull(N3PatchConverter.toSparqlUpdate(""))
    }

    @Test
    fun `a duplicated clause makes the document ambiguous and is rejected`() {
        assertNull(
            N3PatchConverter.toSparqlUpdate(
                """
                @prefix solid: <http://www.w3.org/ns/solid/terms#> .
                _:p a solid:InsertDeletePatch ;
                  solid:inserts { <https://a.example/x> <http://schema.org/name> "one" . } ;
                  solid:inserts { <https://a.example/x> <http://schema.org/name> "two" . } .
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `an unterminated formula is rejected rather than guessed at`() {
        assertNull(
            N3PatchConverter.toSparqlUpdate(
                """
                @prefix solid: <http://www.w3.org/ns/solid/terms#> .
                _:p a solid:InsertDeletePatch ;
                  solid:inserts { <https://a.example/x> <http://schema.org/name> "hello" .
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `the exact document the SDK itself emits converts cleanly`() {
        val n3 = com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch.build {
            insertLiteral("https://a.example/x", "http://schema.org/text", "added by an N3 patch")
        }.toN3String()

        val sparql = N3PatchConverter.toSparqlUpdate(n3)

        requireNotNull(sparql)
        assertTrue(sparql.contains("INSERT DATA {"))
        assertTrue(sparql.contains("\"added by an N3 patch\""))
        assertEquals("no deletions were asked for", false, sparql.contains("DELETE"))
    }
}
