package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.RDFS
import com.erfangholami.androidsolidservices.shared.vocab.STAT

/**
 * Represents an LDP BasicContainer resource.
 * Parses contained resource references and their optional server-supplied
 * metadata (size, modified, mtime) from the quad store.
 *
 * Spec: https://solidproject.org/TR/protocol — Container resources
 */
public open class SolidContainer : SolidRDFResource {

    private val containerRes = arrayListOf<SolidSourceReference>()

    public constructor(identifier: String) : this(identifier, null)

    public constructor(identifier: String, quads: List<RdfQuad>?) :
            this(identifier, quads, null)

    public constructor(identifier: String, contentType: String, quads: List<RdfQuad>?) :
            this(identifier, contentType, quads, null)

    public constructor(identifier: String, quads: List<RdfQuad>?, headers: SolidHeaders?) :
            this(identifier, "application/ld+json", quads, headers)

    public constructor(
        identifier: String,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers) {
        parseContainedResources()
    }

    protected constructor(inParcel: Parcel) : super(inParcel) {
        parseContainedResources()
        val enriched = inParcel.createTypedArrayList(SolidSourceReference.CREATOR)
        if (enriched != null) {
            containerRes.clear()
            containerRes.addAll(enriched)
        }
    }

    private fun parseContainedResources() {
        val containerUri = getIdentifier()
        quads
            .filter { it.predicate == LDP.CONTAINS && iriMatches(it.subject, containerUri) }
            .forEach { containsQuad ->
                val rawIri = containsQuad.`object`

                val types = quads
                    .filter { it.subject == rawIri && it.predicate == RDF.TYPE }
                    .map { it.`object` }

                val size = quads
                    .find { it.subject == rawIri && it.predicate == STAT.SIZE }
                    ?.`object`?.toLongOrNull()

                val modified = quads
                    .find { it.subject == rawIri && it.predicate == DC.MODIFIED }
                    ?.`object`

                val mtime = quads
                    .find { it.subject == rawIri && it.predicate == STAT.MTIME }
                    ?.`object`?.toLongOrNull()

                containerRes.add(
                    SolidSourceReference(
                        identifier = encodeIriIfNeeded(rawIri),
                        types = types,
                        size = size,
                        modified = modified,
                        mtime = mtime,
                    )
                )
            }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeTypedList(containerRes)
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<SolidContainer> = object : Parcelable.Creator<SolidContainer> {
            override fun createFromParcel(parcel: Parcel): SolidContainer = SolidContainer(parcel)
            override fun newArray(size: Int): Array<SolidContainer?> = arrayOfNulls(size)
        }

        private fun iriMatches(a: String, b: String): Boolean {
            if (a == b) return true
            return encodeIriIfNeeded(a) == encodeIriIfNeeded(b)
        }

        private fun encodeIriIfNeeded(iri: String): String =
            encodeUriString(iri).toString()
    }

    /**
     * Returns references to the resources directly contained in this container,
     * parsed from its `ldp:contains` triples and any server-supplied stat metadata.
     */
    public fun getContained(): List<SolidSourceReference> = containerRes

    /**
     * Replaces the contained-resource references with [refs].
     *
     * Useful for attaching richer per-child metadata (for example results of follow-up
     * HEAD requests) that the container listing alone does not provide.
     */
    public fun enrichContained(refs: List<SolidSourceReference>) {
        containerRes.clear()
        containerRes.addAll(refs)
    }

    /** Returns `true` if this container carries an `rdfs:label`. */
    public fun hasLabel(): Boolean = getLabel() != null

    /** Returns this container's `rdfs:label`, or `null` if it has none. */
    public fun getLabel(): String? =
        quads.find {
            it.subject == getIdentifier() && it.predicate == RDFS.LABEL
        }?.`object`
}
