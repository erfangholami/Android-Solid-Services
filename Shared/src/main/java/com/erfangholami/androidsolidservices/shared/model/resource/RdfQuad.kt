package com.erfangholami.androidsolidservices.shared.model.resource

import kotlinx.serialization.Serializable

/**
 * Lightweight representation of an RDF quad (triple + optional named graph).
 *
 * Object interpretation:
 * - `datatype == null && language == null` → IRI or blank-node object
 * - `datatype != null` → typed literal (e.g. `xsd:string`, `xsd:dateTime`)
 * - `language != null` → language-tagged string literal
 *
 * Blank nodes use the `"_:"` prefix per the N-Quads / RdfQuadConsumer convention.
 */
@Serializable
public data class RdfQuad(
    val subject: String,
    val predicate: String,
    val `object`: String,
    val datatype: String? = null,
    val language: String? = null,
    val graph: String? = null
) {
    /** `true` when [object] is a literal, i.e. a [datatype] or [language] is set. */
    val isLiteralObject: Boolean get() = datatype != null || language != null

    /** `true` when [subject] is a blank node (has the `_:` prefix). */
    val isBlankSubject: Boolean get() = subject.startsWith("_:")

    /** `true` when [object] is a blank node (not a literal and has the `_:` prefix). */
    val isBlankObject: Boolean get() = !isLiteralObject && `object`.startsWith("_:")
}