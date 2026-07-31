package com.erfangholami.androidsolidservices.client.internal.fakes

import org.junit.Assert.assertEquals

/**
 * Asserts that the fake received exactly these arguments in these slots.
 *
 * The comparison goes through [CallLog.describe] on both sides, so a test states the value it
 * expects rather than the string the log happens to hold.
 */
internal fun CallLog.Entry.assertArgs(vararg expected: Pair<String, Any?>) {
    expected.forEach { (name, value) ->
        assertEquals(
            "$method received the wrong '$name' — the SDK's call site has the arguments crossed",
            CallLog.describe(value),
            arg(name),
        )
    }
}

/** How [ASSResourceService] summarises an RDF resource it received. */
internal fun rdfArg(
    identifier: String,
    contentType: String = "application/ld+json",
    quads: Int = Fixtures.QUADS.size,
): String = "$identifier|$contentType|quads=$quads"

/** How [ASSResourceService] summarises a non-RDF resource it received. */
internal fun nonRdfArg(
    identifier: String,
    contentType: String = "image/png",
    bytes: Int = Fixtures.PNG_BYTES.size,
): String = "$identifier|$contentType|bytes=$bytes"
