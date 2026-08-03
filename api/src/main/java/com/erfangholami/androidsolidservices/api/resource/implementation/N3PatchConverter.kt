package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.transport.SolidHttpClient
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch

/**
 * Translates a raw N3 Patch document into a SPARQL Update, for servers that accept only
 * `application/sparql-update`.
 *
 * The Solid Protocol makes `text/n3` the mandatory PATCH format, but Inrupt ESS deployments
 * advertise only SPARQL Update and answer 415 for N3 — and the raw path is the only one an IPC
 * caller can reach, because the typed [N3Patch] is flattened to its N3 text at the AIDL boundary.
 * This recovers the structure from the standard document shape (`solid:deletes` /
 * `solid:inserts` / `solid:where` formulae) so [SolidHttpClient.patchRaw] can retry.
 *
 * Deliberately not a full N3 parser: it extracts the three formula blocks and passes their
 * triples through verbatim, which is exactly right for SPARQL — prefixed names, `a`, literals
 * and variables all carry the same meaning there, and the declared prefixes travel along as
 * `PREFIX` lines. A document it cannot read with confidence — a duplicated or unterminated
 * formula, no patch clauses at all — yields `null`, and the caller keeps the server's
 * original 415 rather than a guessed rewrite.
 */
internal object N3PatchConverter {

    private const val SOLID_NS = "http://www.w3.org/ns/solid/terms#"

    private val PREFIX_DECLARATION =
        Regex("""@prefix\s+([A-Za-z][\w.-]*|)\s*:\s*<([^>\s]*)>\s*\.""")

    fun toSparqlUpdate(n3Body: String): String? {
        val code = codeMask(n3Body)

        val prefixes = PREFIX_DECLARATION.findAll(n3Body)
            .filter { code[it.range.first] }
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
        val solidPrefixes = prefixes.filter { it.second == SOLID_NS }.map { it.first }.distinct()

        val deletes = clause(n3Body, code, "deletes", solidPrefixes) ?: return null
        val inserts = clause(n3Body, code, "inserts", solidPrefixes) ?: return null
        val where = clause(n3Body, code, "where", solidPrefixes) ?: return null
        if (deletes.value == null && inserts.value == null) return null

        val patch = N3Patch(deletes = deletes.value, inserts = inserts.value, where = where.value)
        val prefixLines = prefixes.joinToString("") { (label, iri) -> "PREFIX $label: <$iri>\n" }
        return prefixLines + patch.toSparqlUpdate()
    }

    private class Clause(val value: String?)

    private fun clause(
        doc: String,
        code: BooleanArray,
        name: String,
        solidPrefixes: List<String>,
    ): Clause? {
        val tokens = solidPrefixes.map { "$it:$name" } + "<$SOLID_NS$name>"
        val blocks = mutableListOf<String>()

        for (token in tokens) {
            blocks += blocksFor(doc, code, token) ?: return null
        }

        return when (blocks.size) {
            0 -> Clause(null)
            1 -> Clause(blocks.single())
            else -> null
        }
    }

    private fun blocksFor(doc: String, code: BooleanArray, token: String): List<String>? {
        val out = mutableListOf<String>()
        var from = 0

        while (true) {
            val at = doc.indexOf(token, from)
            if (at < 0) return out
            from = at + token.length

            val open = clauseBrace(doc, code, at, token.length)
            if (open != null) {
                val close = matchBrace(doc, code, open) ?: return null
                out += doc.substring(open + 1, close).trim()
            }
        }
    }

    private fun clauseBrace(doc: String, code: BooleanArray, at: Int, tokenLength: Int): Int? {
        if (!isClauseUseSite(doc, code, at)) return null
        val next = nextCodeChar(doc, code, at + tokenLength) ?: return null
        return next.takeIf { doc[it] == '{' }
    }

    private fun isClauseUseSite(doc: String, code: BooleanArray, at: Int): Boolean {
        if (!code[at]) return false
        val before = doc.getOrNull(at - 1) ?: return true
        if (before.isLetterOrDigit()) return false
        return before != '_' && before != ':'
    }

    private fun nextCodeChar(doc: String, code: BooleanArray, start: Int): Int? {
        for (i in start until doc.length) {
            if (code[i] && !doc[i].isWhitespace()) return i
        }
        return null
    }

    private fun matchBrace(doc: String, code: BooleanArray, open: Int): Int? {
        var depth = 0
        for (i in open until doc.length) {
            if (!code[i]) continue
            when (doc[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return null
    }

    private fun codeMask(doc: String): BooleanArray {
        val code = BooleanArray(doc.length)
        var i = 0
        var state = State.NORMAL

        while (i < doc.length) {
            val step = when (state) {
                State.NORMAL -> stepNormal(doc, i, code)
                State.IRI -> stepIri(doc, i, code)
                State.STRING -> stepQuoted(doc, i, '"', State.STRING)
                State.SQ_STRING -> stepQuoted(doc, i, '\'', State.SQ_STRING)
                State.LONG_STRING -> stepLongString(doc, i)
                State.COMMENT -> Step(if (doc[i] == '\n') State.NORMAL else State.COMMENT)
            }
            state = step.next
            i += step.advance
        }
        return code
    }

    private class Step(val next: State, val advance: Int = 1)

    private fun stepNormal(doc: String, i: Int, code: BooleanArray): Step = when {
        doc.startsWith("\"\"\"", i) -> Step(State.LONG_STRING, advance = 3)
        doc[i] == '"' -> Step(State.STRING)
        doc[i] == '\'' -> Step(State.SQ_STRING)
        doc[i] == '#' -> Step(State.COMMENT)
        doc[i] == '<' -> {
            code[i] = true
            Step(State.IRI)
        }

        else -> {
            code[i] = true
            Step(State.NORMAL)
        }
    }

    private fun stepIri(doc: String, i: Int, code: BooleanArray): Step {
        code[i] = true
        return Step(if (doc[i] == '>') State.NORMAL else State.IRI)
    }

    private fun stepQuoted(doc: String, i: Int, quote: Char, self: State): Step = when (doc[i]) {
        '\\' -> Step(self, advance = 2)
        quote -> Step(State.NORMAL)
        else -> Step(self)
    }

    private fun stepLongString(doc: String, i: Int): Step = when {
        doc[i] == '\\' -> Step(State.LONG_STRING, advance = 2)
        doc.startsWith("\"\"\"", i) -> Step(State.NORMAL, advance = 3)
        else -> Step(State.LONG_STRING)
    }

    private enum class State { NORMAL, IRI, STRING, SQ_STRING, LONG_STRING, COMMENT }
}
