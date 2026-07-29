package com.erfangholami.androidsolidservices.shared.rdf.patch

import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.rdf.patch.N3Patch.Companion.build
import com.erfangholami.androidsolidservices.shared.util.IriUtils
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import java.io.InputStream

/**
 * Represents a Solid N3 Patch document for use with HTTP PATCH requests.
 *
 * A valid N3 Patch document declares `rdf:type solid:InsertDeletePatch` and
 * may contain `solid:deletes`, `solid:inserts`, and `solid:where` formulae.
 *
 * ## Building a patch
 *
 * **DSL builder** — no raw N3 strings required:
 * ```kotlin
 * // Simple insert
 * val patch = N3Patch.build {
 *     insertLiteral(contactUri, VCARD.FN, "Alice")
 * }
 *
 * // Safe replace — bind the old value via where, then swap it
 * val patch = N3Patch.build {
 *     where(contactUri, VCARD.FN, variable = "oldName")
 *     deleteVar(contactUri, VCARD.FN, variable = "oldName")
 *     insertLiteral(contactUri, VCARD.FN, "Alice")
 * }
 * ```
 *
 * **Diff factory** — automatically derives the patch from two resource states:
 * ```kotlin
 * val patch = N3Patch.fromDiff(originalResource, modifiedResource)
 * ```
 *
 * Content-Type: text/n3
 * Spec: https://solidproject.org/TR/protocol#n3-patch
 *
 * @property deletes the triples to remove, as an N3 triple block, or `null` for none.
 * @property inserts the triples to add, as an N3 triple block, or `null` for none.
 * @property where an optional N3 graph pattern that binds variables used in [deletes]
 *   and [inserts]; required for value-dependent edits such as a safe replace.
 */
public data class N3Patch(
    val deletes: String? = null,
    val inserts: String? = null,
    val where: String? = null,
) {
    init {
        require(deletes != null || inserts != null) {
            "N3Patch must contain at least one of 'deletes' or 'inserts'."
        }
    }

    /** The media type of an N3 Patch body: `text/n3`. */
    val contentType: String = HTTPAcceptType.N3

    /**
     * Serializes this patch to an N3 Patch document (`text/n3`).
     *
     * The output declares `rdf:type solid:InsertDeletePatch` and emits the
     * `solid:deletes`, `solid:inserts`, and `solid:where` clauses for whichever of
     * [deletes], [inserts], and [where] are present.
     */
    public fun toN3String(): String = buildString {
        appendLine("@prefix solid: <${Solid.NAMESPACE}> .")
        appendLine("@prefix rdf: <${RDF.NAMESPACE}> .")
        appendLine()
        appendLine("<> a solid:InsertDeletePatch ;")

        val clauses = mutableListOf<String>()
        if (deletes != null) clauses.add("  solid:deletes { $deletes }")
        if (inserts != null) clauses.add("  solid:inserts { $inserts }")
        if (where != null) clauses.add("  solid:where   { $where }")

        append(clauses.joinToString(" ;\n"))
        appendLine(" .")
    }

    /**
     * Renders this patch as a SPARQL 1.1 Update document
     * (`application/sparql-update`).
     *
     * The Solid Protocol requires servers to support N3 Patch, but not every
     * deployment does in practice: some storage endpoints advertise only
     * `Accept-Patch: application/sparql-update` and return `415 Unsupported
     * Media Type` for `text/n3` bodies. Because the major servers also accept
     * SPARQL Update, it is the more portable PATCH format across servers.
     *
     * Output shape:
     *  - data-only (no `where` clause) → `DELETE DATA { ... } ; INSERT DATA { ... }`
     *  - with `where` → `DELETE { ... } INSERT { ... } WHERE { ... }`
     *  - either clause may be absent if this patch has no corresponding
     *    triples
     */
    public fun toSparqlUpdate(): String = buildString {
        if (where != null) {
            if (deletes != null) {
                appendLine("DELETE {")
                append("  ").appendLine(deletes)
                appendLine("}")
            }
            if (inserts != null) {
                appendLine("INSERT {")
                append("  ").appendLine(inserts)
                appendLine("}")
            }
            appendLine("WHERE {")
            append("  ").appendLine(where)
            append("}")
        } else {
            val parts = mutableListOf<String>()
            if (deletes != null) {
                parts.add(buildString {
                    appendLine("DELETE DATA {")
                    append("  ").appendLine(deletes)
                    append("}")
                })
            }
            if (inserts != null) {
                parts.add(buildString {
                    appendLine("INSERT DATA {")
                    append("  ").appendLine(inserts)
                    append("}")
                })
            }
            append(parts.joinToString(" ;\n"))
        }
    }

    /** Returns the [toN3String] document as a byte stream. */
    public fun toInputStream(): InputStream = toN3String().byteInputStream()

    public companion object {
        /** Builds an insert-only patch from a raw N3 triple block. */
        public fun insert(triples: String): N3Patch = N3Patch(inserts = triples)

        /** Builds a delete-only patch from a raw N3 triple block. */
        public fun delete(triples: String): N3Patch = N3Patch(deletes = triples)

        /**
         * Builds a conditional patch that inserts [inserts] using variables bound by the
         * [where] pattern. Note this does not by itself delete the old value — pair it
         * with a matching delete (or use the [build] DSL) for a full replace.
         */
        public fun replace(inserts: String, where: String): N3Patch =
            N3Patch(inserts = inserts, where = where)

        /**
         * Builds a patch using the [N3PatchBuilder] DSL, which escapes triple terms for
         * you so no raw N3 syntax is required.
         */
        public fun build(block: N3PatchBuilder.() -> Unit): N3Patch =
            N3PatchBuilder().apply(block).build()

        /**
         * Derives a patch from the difference between two resource states.
         *
         * Compares the default-graph triples of [original] and [updated]: triples present
         * only in [original] become deletes, triples present only in [updated] become
         * inserts. Triples in named graphs are ignored.
         *
         * @throws IllegalArgumentException if the two states are identical (the patch
         *   would be empty).
         */
        public fun fromDiff(original: RDFResource, updated: RDFResource): N3Patch {
            val originalQuads = original.getAllQuads().filter { it.graph == null }.toSet()
            val updatedQuads = updated.getAllQuads().filter { it.graph == null }.toSet()

            val toDelete = originalQuads - updatedQuads
            val toInsert = updatedQuads - originalQuads

            require(toDelete.isNotEmpty() || toInsert.isNotEmpty()) {
                "No difference between original and updated resources — patch would be empty."
            }

            return N3Patch(
                deletes = toDelete.takeIf { it.isNotEmpty() }
                    ?.joinToString("\n    ") { it.toN3Triple() },
                inserts = toInsert.takeIf { it.isNotEmpty() }
                    ?.joinToString("\n    ") { it.toN3Triple() },
            )
        }
    }
}

/**
 * Kotlin DSL builder for [N3Patch].
 *
 * Produces properly escaped N3 triple strings — callers work entirely with
 * subject/predicate IRI strings and typed object values; no raw N3 syntax needed.
 *
 * Variable names passed to [where], [deleteVar], and [insertVar] must not include
 * the leading `?` — the builder adds it automatically.
 */
public class N3PatchBuilder {

    private val insertTriples = mutableListOf<String>()
    private val deleteTriples = mutableListOf<String>()
    private val whereTriples = mutableListOf<String>()

    /** Adds an insert triple whose object is an IRI. */
    public fun insert(subject: String, predicate: String, iriObject: String): N3PatchBuilder {
        insertTriples += triple(subject, predicate, iriObject.asN3IriTerm())
        return this
    }

    /**
     * Adds an insert triple whose object is a literal.
     *
     * @param datatype the literal datatype IRI, or `null` for a plain string.
     * @param language a language tag, or `null`.
     */
    public fun insertLiteral(
        subject: String,
        predicate: String,
        value: String,
        datatype: String? = null,
        language: String? = null,
    ): N3PatchBuilder {
        insertTriples += triple(subject, predicate, literalTerm(value, datatype, language))
        return this
    }

    /** Adds an insert triple whose object is the [variable] bound by a [where] clause. */
    public fun insertVar(subject: String, predicate: String, variable: String): N3PatchBuilder {
        insertTriples += triple(subject, predicate, "?$variable")
        return this
    }

    /** Adds a delete triple whose object is an IRI. */
    public fun delete(subject: String, predicate: String, iriObject: String): N3PatchBuilder {
        deleteTriples += triple(subject, predicate, iriObject.asN3IriTerm())
        return this
    }

    /**
     * Adds a delete triple whose object is a literal. The literal must match the stored
     * value exactly (including datatype/language) for the server to remove it.
     */
    public fun deleteLiteral(
        subject: String,
        predicate: String,
        value: String,
        datatype: String? = null,
        language: String? = null,
    ): N3PatchBuilder {
        deleteTriples += triple(subject, predicate, literalTerm(value, datatype, language))
        return this
    }

    /**
     * Adds a delete triple whose object is the [variable] bound by a [where] clause.
     * Use this to remove whatever value currently exists for a property in a safe replace.
     */
    public fun deleteVar(subject: String, predicate: String, variable: String): N3PatchBuilder {
        deleteTriples += triple(subject, predicate, "?$variable")
        return this
    }

    /**
     * Adds a `where` pattern triple that binds [variable] to the current object of
     * [subject]/[predicate], so [deleteVar] and [insertVar] can reference it.
     */
    public fun where(subject: String, predicate: String, variable: String): N3PatchBuilder {
        whereTriples += triple(subject, predicate, "?$variable")
        return this
    }

    /** Assembles the accumulated triples into an [N3Patch]. */
    public fun build(): N3Patch {
        val insertsStr = insertTriples.takeIf { it.isNotEmpty() }?.joinToString("\n    ")
        val deletesStr = deleteTriples.takeIf { it.isNotEmpty() }?.joinToString("\n    ")
        val whereStr = whereTriples.takeIf { it.isNotEmpty() }?.joinToString("\n    ")
        return N3Patch(inserts = insertsStr, deletes = deletesStr, where = whereStr)
    }

    private fun String.asN3Subject(): String =
        if (startsWith("_:")) this else "<${IriUtils.escapeIri(this)}>"

    private fun String.asN3IriTerm(): String =
        if (startsWith("_:")) this else "<${IriUtils.escapeIri(this)}>"

    private fun literalTerm(value: String, datatype: String?, language: String?): String {
        val escaped = escapeN3Literal(value)
        return when {
            language != null -> "\"$escaped\"@$language"
            datatype != null -> "\"$escaped\"^^<$datatype>"
            else -> "\"$escaped\""
        }
    }

    private fun triple(subject: String, predicate: String, objectTerm: String): String =
        "${subject.asN3Subject()} <${IriUtils.escapeIri(predicate)}> $objectTerm ."
}

private fun escapeN3Literal(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

internal fun RdfQuad.toN3Triple(): String {
    val s = if (subject.startsWith("_:")) subject else "<${IriUtils.escapeIri(subject)}>"
    val p = "<${IriUtils.escapeIri(predicate)}>"
    val o = when {
        isBlankObject -> `object`
        isLiteralObject -> {
            val escaped = escapeN3Literal(`object`)
            when {
                language != null -> "\"$escaped\"@$language"
                datatype != null -> "\"$escaped\"^^<${IriUtils.escapeIri(datatype)}>"
                else -> "\"$escaped\""
            }
        }

        else -> "<${IriUtils.escapeIri(`object`)}>"
    }
    return "$s $p $o ."
}
