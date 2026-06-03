package com.erfangholami.androidsolidservices.api.access

import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import java.net.URI

/**
 * Tiny N-Triples writer used by the access backends.
 *
 * Why a hand-rolled writer instead of pushing everything through
 * Titanium's JSON-LD compaction:
 *
 *  - Inrupt PodSpaces' ACR endpoint rejects compacted JSON-LD that doesn't
 *    match its proprietary shape (`{"id": "...", "type": "AccessControlResource",
 *    "resource": "...", "accessControl": [...]}`) with `400 "invalid ACR
 *    format"`. It does, however, advertise `application/n-triples` in its
 *    `Accept-Put`, and N-Triples has no context to disagree about.
 *  - WAC ACL writes get the same benefit for free: a flat line per triple
 *    is independent of the server's preferred JSON-LD context.
 *
 * The format is the strict N-Triples 1.1 subset we actually need:
 *
 *  - Each quad becomes one line `<subject> <predicate> <object> .` followed
 *    by `\n`.
 *  - Subject and predicate are always written as absolute IRIs in angle
 *    brackets. We don't emit blank nodes — every ACL/ACR subject in this
 *    codebase is built from `${aclUri}#fragment` so it always resolves.
 *  - Objects are IRIs (when [RdfQuad.datatype] and [RdfQuad.language] are
 *    both null) or literals (otherwise). Literals are quoted with the
 *    escapes mandated by the N-Triples grammar.
 *
 * Spec: https://www.w3.org/TR/n-triples/
 */
internal object NTriples {

    const val MEDIA_TYPE: String = "application/n-triples"

    fun serialize(quads: List<RdfQuad>): String = buildString(quads.size * 80) {
        quads.forEach { q ->
            append('<').append(IriUtils.escapeIri(q.subject)).append("> ")
            append('<').append(IriUtils.escapeIri(q.predicate)).append("> ")
            if (q.datatype != null || q.language != null) {
                append('"').append(escapeLiteral(q.`object`)).append('"')
                val lang = q.language
                val dt = q.datatype
                when {
                    !lang.isNullOrEmpty() -> append('@').append(lang)
                    !dt.isNullOrEmpty() && dt != XSD_STRING ->
                        append("^^<").append(IriUtils.escapeIri(dt)).append('>')
                }
            } else {
                append('<').append(IriUtils.escapeIri(q.`object`)).append('>')
            }
            append(" .\n")
        }
    }

    /**
     * Parses an N-Triples document into [RdfQuad]s. Round-trips the output of
     * [serialize], so an ACL/ACR this library wrote (or that a server echoes
     * back) as `application/n-triples` is readable again.
     *
     * Supports the N-Triples 1.1 grammar we actually encounter: IRIs in angle
     * brackets, blank nodes (`_:label`), plain/typed/language literals, `#`
     * comment lines and blank lines. Relative IRIs (technically illegal in
     * N-Triples but emitted by some servers) are resolved against [base] when
     * provided. Malformed lines throw so the caller can surface a clear error
     * rather than silently dropping data.
     *
     * Spec: https://www.w3.org/TR/n-triples/
     */
    fun parse(text: String, base: URI? = null): List<RdfQuad> {
        val quads = mutableListOf<RdfQuad>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            quads += parseLine(line, base)
        }
        return quads
    }

    private fun parseLine(line: String, base: URI?): RdfQuad {
        var pos = 0
        fun skipWs() { while (pos < line.length && line[pos].isWhitespace()) pos++ }

        skipWs()
        val subject = readIriOrBlank(line, pos, base).also { pos = it.second }.first
        skipWs()
        val predicate = readIri(line, pos, base).also { pos = it.second }.first
        skipWs()
        val (obj, objEnd) = readObject(line, pos, base)
        pos = objEnd
        skipWs()
        require(pos < line.length && line[pos] == '.') {
            "N-Triples line missing terminating '.': $line"
        }
        return RdfQuad(subject, predicate, obj.value, obj.datatype, obj.language)
    }

    private data class ObjTerm(val value: String, val datatype: String?, val language: String?)

    private fun readObject(line: String, start: Int, base: URI?): Pair<ObjTerm, Int> {
        if (start >= line.length) error("N-Triples: missing object in: $line")
        return if (line[start] == '"') {
            readLiteral(line, start, base)
        } else {
            val (iri, next) = readIriOrBlank(line, start, base)
            ObjTerm(iri, null, null) to next
        }
    }

    private fun readIri(line: String, start: Int, base: URI?): Pair<String, Int> {
        require(start < line.length && line[start] == '<') {
            "N-Triples: expected IRI at index $start in: $line"
        }
        val end = line.indexOf('>', start + 1)
        require(end > start) { "N-Triples: unterminated IRI in: $line" }
        val iri = unescapeUnicode(line.substring(start + 1, end))
        return resolveIri(iri, base) to (end + 1)
    }

    private fun readIriOrBlank(line: String, start: Int, base: URI?): Pair<String, Int> {
        if (start < line.length && line[start] == '<') return readIri(line, start, base)
        require(start + 1 < line.length && line[start] == '_' && line[start + 1] == ':') {
            "N-Triples: expected IRI or blank node at index $start in: $line"
        }
        var j = start
        while (j < line.length && !line[j].isWhitespace() && line[j] != '.') j++
        return line.substring(start, j) to j
    }

    private fun readLiteral(line: String, start: Int, base: URI?): Pair<ObjTerm, Int> {
        val sb = StringBuilder()
        var j = start + 1
        while (j < line.length) {
            val c = line[j]
            when {
                c == '\\' -> {
                    require(j + 1 < line.length) { "N-Triples: dangling escape in: $line" }
                    when (val e = line[j + 1]) {
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            sb.append(line.substring(j + 2, j + 6).toInt(16).toChar()); j += 4
                        }
                        'U' -> {
                            sb.appendCodePoint(line.substring(j + 2, j + 10).toInt(16)); j += 6
                        }
                        else -> sb.append(e)
                    }
                    j += 2
                }

                c == '"' -> break
                else -> { sb.append(c); j++ }
            }
        }
        require(j < line.length && line[j] == '"') { "N-Triples: unterminated literal in: $line" }
        j++
        var language: String? = null
        var datatype: String? = null
        if (j < line.length && line[j] == '@') {
            val k = j + 1
            var m = k
            while (m < line.length && (line[m].isLetterOrDigit() || line[m] == '-')) m++
            language = line.substring(k, m)
            j = m
        } else if (j + 1 < line.length && line[j] == '^' && line[j + 1] == '^') {
            val (dt, next) = readIri(line, j + 2, base)
            datatype = dt
            j = next
        } else {
            datatype = XSD_STRING
        }
        return ObjTerm(sb.toString(), datatype, language) to j
    }

    private fun resolveIri(iri: String, base: URI?): String {
        if (base == null) return iri
        return runCatching {
            if (URI(iri).isAbsolute) iri else base.resolve(iri).toString()
        }.getOrDefault(iri)
    }

    private fun unescapeUnicode(iri: String): String {
        if ('\\' !in iri) return iri
        val sb = StringBuilder(iri.length)
        var i = 0
        while (i < iri.length) {
            val c = iri[i]
            if (c == '\\' && i + 1 < iri.length) {
                when (iri[i + 1]) {
                    'u' -> { sb.append(iri.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                    'U' -> { sb.appendCodePoint(iri.substring(i + 2, i + 10).toInt(16)); i += 10 }
                    else -> { sb.append(iri[i + 1]); i += 2 }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"

    private fun escapeLiteral(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }
}
