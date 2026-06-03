package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * The root of the resource model used throughout the SDK.
 *
 * A `Resource` is anything addressable by a URI on a Solid pod. The Solid Protocol
 * builds on the W3C Linked Data Platform (LDP), which classifies resources into two
 * kinds, mirrored by the two direct subtypes here:
 *
 * - [RDFResource] — an *RDF source*: a document whose content is a set of RDF triples
 *   (e.g. Turtle or JSON-LD). Its triples can be read and edited individually.
 * - [NonRDFResource] — a *non-RDF source* (a "binary"): an opaque byte stream such as
 *   an image or PDF, handled only as raw bytes.
 *
 * The `Solid…` subtypes ([SolidRDFResource], [SolidNonRDFResource], [SolidContainer])
 * additionally expose server-supplied [SolidMetadata] via [SolidResource].
 *
 * Instances are [Parcelable] so they can cross process boundaries over AIDL IPC, and
 * [AutoCloseable] because [getEntity] backs the representation with a stream — use them
 * in a `use { }` block, or call [close], to release it.
 *
 * See the Solid Protocol (https://solidproject.org/TR/protocol) and LDP
 * (http://www.w3.org/TR/ldp/) for the underlying resource model.
 */
public interface Resource : AutoCloseable, Parcelable {

    /** The URI that identifies this resource on the pod. */
    public fun getIdentifier(): URI

    /** The media type of the resource representation (the value of its `Content-Type`). */
    public fun getContentType(): String

    /** The HTTP headers associated with this resource (empty when constructed locally). */
    public fun getHeaders(): SolidHeaders

    /**
     * Returns the resource representation as a byte stream.
     *
     * For an [RDFResource] this is the serialized RDF (JSON-LD); for a [NonRDFResource]
     * it is the raw body. The caller owns the returned stream and should close it (or
     * close this resource).
     *
     * @throws IOException if the representation cannot be produced.
     */
    @Throws(IOException::class)
    public fun getEntity(): InputStream

}