package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.JsonLdOptions
import com.apicatalog.jsonld.JsonLdVersion
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.http.media.MediaType
import com.apicatalog.jsonld.serialization.QuadsToJsonld
import com.apicatalog.jsonld.uri.UriValidationPolicy
import com.apicatalog.rdf.api.RdfQuadConsumer
import com.erfangholami.androidsolidservices.shared.util.encodeUri
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.ACP
import com.erfangholami.androidsolidservices.shared.vocab.AS
import com.erfangholami.androidsolidservices.shared.vocab.Cert
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.Notify
import com.erfangholami.androidsolidservices.shared.vocab.OWL
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.RDFS
import com.erfangholami.androidsolidservices.shared.vocab.SAI
import com.erfangholami.androidsolidservices.shared.vocab.STAT
import com.erfangholami.androidsolidservices.shared.vocab.Schema
import com.erfangholami.androidsolidservices.shared.vocab.ShapeTree
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import jakarta.json.spi.JsonProvider
import kotlinx.serialization.json.Json
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.net.URI

/**
 * An RDF source: a resource whose representation is a set of RDF triples.
 *
 * The triples are held in memory as a list of [RdfQuad]s and serialized to JSON-LD by
 * [getEntity]. You read and edit individual properties with [findProperty] /
 * [findAllProperties] (and their per-subject variants) and [addQuad] /
 * [addQuadLiteral] / [clearProperties], rather than manipulating raw RDF text.
 *
 * By convention the resource's own primary subject is the document URI with the
 * fragment `#it`; the per-subject query/mutation overloads default to that subject.
 *
 * For resources fetched from a Solid server, prefer the [SolidRDFResource] subtype,
 * which also exposes the response [SolidMetadata]. Use this base type for RDF documents
 * you construct locally.
 *
 * See the Solid Protocol (https://solidproject.org/TR/protocol) and LDP
 * (http://www.w3.org/TR/ldp/) for the RDF source model.
 */
public open class RDFResource : Resource {

    private val identifier: URI
    private val headers: SolidHeaders
    private val contentType: String
    protected var quads: MutableList<RdfQuad>
    protected val itselfSubject: String
    protected val contextDocument: JsonDocument? = JsonDocument.of(
        MediaType.JSON,
        JsonProvider.provider().createObjectBuilder().apply {
            add("rdf", RDF.NAMESPACE)
            add("rdfs", RDFS.NAMESPACE)
            add("owl", OWL.NAMESPACE)
            add("xsd", XSD.NAMESPACE)
            add("ldp", LDP.NAMESPACE)
            add("dc", DC.NAMESPACE)
            add("dcterms", DC.NAMESPACE)
            add("solid", Solid.NAMESPACE)
            add("foaf", FOAF.NAMESPACE)
            add("pim", PIM.NAMESPACE)
            add("acl", ACL.NAMESPACE)
            add("acp", ACP.NAMESPACE)
            add("cert", Cert.NAMESPACE)
            add("as", AS.NAMESPACE)
            add("notify", Notify.NAMESPACE)
            add("schema", Schema.NAMESPACE)
            add("interop", SAI.NAMESPACE)
            add("st", ShapeTree.NAMESPACE)
            add("stat", STAT.NAMESPACE)
            add("vcard", VCARD.NAMESPACE)
        }.build()
    )

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<RDFResource> = object : Parcelable.Creator<RDFResource> {
            override fun createFromParcel(parcel: Parcel): RDFResource = RDFResource(parcel)
            override fun newArray(size: Int): Array<RDFResource?> = Array(size) { null }
        }

        /**
         * Parses a JSON-LD document (its raw text) into a flat list of [RdfQuad]s.
         *
         * Relative IRIs are resolved against [baseUri] (the document URL), as required by JSON-LD
         * and the Solid Protocol. URI validation is scheme-only, tolerating the relative /
         * non-canonical IRIs some pods emit. The JSON-LD processor (titanium) is used internally and
         * is not part of this signature, so it stays off consumers' compile classpath.
         *
         * @param jsonLdText the JSON-LD document text to expand and convert to RDF.
         * @param baseUri base IRI for resolving relative references, or `null`.
         * @param i18nDirection when `true`, emit `i18n-datatype` for directional language strings.
         * @return the resulting quads, in document order.
         */
        public fun parseJsonLd(
            jsonLdText: String,
            baseUri: String? = null,
            i18nDirection: Boolean = false,
        ): MutableList<RdfQuad> {
            val options = JsonLdOptions().apply {
                baseUri?.let { base = URI.create(it) }
                if (i18nDirection) rdfDirection = JsonLdOptions.RdfDirection.I18N_DATATYPE
                processingMode = JsonLdVersion.V1_1
                isProduceGeneralizedRdf = true
                uriValidation = UriValidationPolicy.SchemeOnly
            }
            val document = JsonDocument.of(jsonLdText.byteInputStream())
            val result = mutableListOf<RdfQuad>()
            val api = JsonLd.toRdf(document)
            api.options(options)
            api.provide(object : RdfQuadConsumer {
                override fun quad(
                    subject: String, predicate: String, `object`: String,
                    datatype: String?, language: String?, direction: String?, graph: String?
                ): RdfQuadConsumer {
                    result.add(RdfQuad(subject, predicate, `object`, datatype, language, graph))
                    return this
                }
            })
            return result
        }
    }

    protected constructor(inParcel: Parcel) {
        this.identifier = encodeUriString(inParcel.readString()!!)
        this.headers = SolidHeaders(Json.decodeFromString<Map<String, List<String>>>(inParcel.readString()!!))
        this.contentType = inParcel.readString()!!
        this.quads = Json.decodeFromString<List<RdfQuad>>(inParcel.readString()!!).toMutableList()
        this.itselfSubject = inParcel.readString()!!
    }

    public constructor(identifier: URI) : this(identifier, null as List<RdfQuad>?)

    public constructor(identifier: URI, quads: List<RdfQuad>?) :
            this(identifier, quads, null)

    public constructor(identifier: URI, quads: List<RdfQuad>?, headers: SolidHeaders?) :
            this(identifier, "application/ld+json", quads, headers)

    public constructor(identifier: URI, contentType: String, quads: List<RdfQuad>?) :
            this(identifier, contentType, quads, null)

    public constructor(
        identifier: URI,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) {
        this.identifier = encodeUri(identifier)
        this.headers = headers ?: SolidHeaders.EMPTY
        this.contentType = contentType
        this.quads = quads?.toMutableList() ?: mutableListOf()
        this.itselfSubject = "$identifier#it"
    }

    /**
     * Adds a triple whose object is an IRI (or blank node).
     *
     * If the number of existing triples with the same [subject] and [predicate] has
     * reached [maxNumber], those triples are first removed so the new value replaces
     * them; below the limit, the triple is simply appended. With the default
     * [maxNumber] of 1 this gives single-valued "set" semantics.
     *
     * @param maxNumber the maximum number of values to keep for this subject/predicate.
     */
    public fun addQuad(
        subject: String,
        predicate: String,
        obj: String,
        maxNumber: Int = 1
    ) {
        addQuadLiteral(subject, predicate, obj, null, null, maxNumber)
    }

    /**
     * Adds a triple whose object is a literal.
     *
     * Replacement semantics match [addQuad]: when the [maxNumber] limit is reached for
     * this [subject]/[predicate], existing values are removed before the new one is added.
     *
     * @param value the literal lexical value.
     * @param datatype the literal datatype IRI; defaults to `xsd:string`.
     * @param language a language tag for a language-tagged string, or `null`.
     * @param maxNumber the maximum number of values to keep for this subject/predicate.
     */
    public fun addQuadLiteral(
        subject: String,
        predicate: String,
        value: String,
        datatype: String? = XSD.STRING,
        language: String? = null,
        maxNumber: Int = 1
    ) {
        val quad = RdfQuad(subject, predicate, value, datatype, language)
        val current = quads.filter { it.subject == subject && it.predicate == predicate }
        if (current.size < maxNumber) {
            quads.add(quad)
            return
        }
        quads.removeAll { it.subject == subject && it.predicate == predicate }
        quads.add(quad)
    }

    /**
     * Ensures [subject] carries an `rdf:type` triple with [typeIri], preserving any
     * other type triples already present.
     *
     * Unlike `addQuad(subject, RDF.TYPE, typeIri)` — whose default single-value
     * semantics would replace every existing `rdf:type` — this appends only when the
     * type is absent, so types written by other applications survive a parse/serialize
     * round trip.
     */
    public fun ensureType(subject: String, typeIri: String) {
        val present = quads.any {
            it.subject == subject && it.predicate == RDF.TYPE && it.`object` == typeIri
        }
        if (!present) {
            addQuad(subject, RDF.TYPE, typeIri, maxNumber = Int.MAX_VALUE)
        }
    }

    /**
     * Removes every triple with the given [predicate] for [subject].
     *
     * @param subject the subject to clear; defaults to this resource's primary subject.
     */
    public fun clearProperties(predicate: String, subject: String = itselfSubject) {
        quads.removeAll { it.subject == subject && it.predicate == predicate }
    }

    /** Returns the object values of every triple with the given [predicate], across all subjects. */
    public fun findAllProperties(predicate: String): List<String> =
        quads.filter { it.predicate == predicate }.map { it.`object` }

    /** Returns the object value of the first triple with the given [predicate], or `null` if none. */
    public fun findProperty(predicate: String): String? =
        quads.find { it.predicate == predicate }?.`object`

    /** Returns the object values of every triple matching the given [subject] and [predicate]. */
    public fun findAllPropertiesForSubject(subject: String, predicate: String): List<String> =
        quads.filter { it.subject == subject && it.predicate == predicate }.map { it.`object` }

    /** Returns the object value of the first triple matching [subject] and [predicate], or `null`. */
    public fun findPropertyForSubject(subject: String, predicate: String): String? =
        quads.find { it.subject == subject && it.predicate == predicate }?.`object`

    /** Returns a snapshot copy of all triples held by this resource. */
    public fun getAllQuads(): List<RdfQuad> = quads.toList()

    override fun getIdentifier(): URI = identifier

    override fun getContentType(): String = contentType

    override fun getHeaders(): SolidHeaders = headers

    override fun getEntity(): InputStream {
        val converter = QuadsToJsonld()
        quads.forEach { q ->
            converter.quad(
                q.subject,
                q.predicate,
                q.`object`,
                q.datatype,
                q.language,
                null,
                q.graph
            )
        }
        val jsonLdArray = converter.toJsonLd()
        val compacted = JsonLd.compact(
            JsonDocument.of(jsonLdArray.toString().byteInputStream()),
            contextDocument
        ).get()
        return compacted.toString().byteInputStream()
    }

    override fun close() {
        try {
            getEntity().close()
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to close RDFSource entity.", e)
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(identifier.toString())
        dest.writeString(Json.encodeToString(headers.toMultimap()))
        dest.writeString(contentType)
        dest.writeString(Json.encodeToString(quads))
        dest.writeString(itselfSubject)
    }
}
