package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.util.encodeUri
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import kotlinx.serialization.json.Json
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.net.URI

/**
 * A non-RDF source: a "binary" resource handled as an opaque byte stream.
 *
 * Use this for content that is not RDF — images, PDFs, plain text, and so on. Unlike
 * [RDFResource], it carries no triple model; its body is exposed verbatim through
 * [getEntity] and described only by its [getContentType].
 *
 * For resources fetched from a Solid server, prefer the [SolidNonRDFResource] subtype,
 * which also exposes the response [SolidMetadata]. Use this base type for binary content
 * you construct locally before writing it to a pod.
 *
 * The backing stream is consumed when the body is read or the resource is parcelled, so
 * an instance is single-use; close it (or read it) exactly once.
 *
 * Parcelling buffers the entire body as raw bytes, so binary content survives IPC
 * unchanged. Android's binder transaction buffer is roughly 1 MB per process; sending a
 * larger body over AIDL fails with a `TransactionTooLargeException`.
 *
 * See the Solid Protocol (https://solidproject.org/TR/protocol) and LDP
 * (http://www.w3.org/TR/ldp/) for the non-RDF source model.
 */
public open class NonRDFResource : Resource {

    private val identifier: URI
    private val contentType: String
    private val headers: SolidHeaders
    private val entity: InputStream

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<NonRDFResource> = object : Parcelable.Creator<NonRDFResource> {
            override fun createFromParcel(parcel: Parcel): NonRDFResource {
                return NonRDFResource(parcel)
            }

            override fun newArray(size: Int): Array<NonRDFResource?> {
                return Array(size) { null }
            }
        }
    }


    protected constructor(inParcel: Parcel) {
        this.identifier = encodeUriString(inParcel.readString()!!)
        this.contentType = inParcel.readString()!!
        this.headers = SolidHeaders(Json.decodeFromString<Map<String, List<String>>>(inParcel.readString()!!))
        this.entity = ByteArrayInputStream(inParcel.createByteArray() ?: ByteArray(0))
    }

    public constructor(
        identifier: URI,
        contentType: String,
        headers: SolidHeaders?,
        entity: InputStream
    ) {
        this.identifier = encodeUri(identifier)
        this.contentType = contentType
        this.headers = headers ?: SolidHeaders.EMPTY
        this.entity = entity
    }

    public constructor(
        identifier: URI,
        contentType: String,
        entity: InputStream
    ) : this(identifier, contentType, null, entity)

    override fun getIdentifier(): URI {
        return identifier
    }

    override fun getContentType(): String {
        return contentType
    }

    override fun getHeaders(): SolidHeaders {
        return headers
    }

    override fun getEntity(): InputStream {
        return entity
    }

    override fun close() {
        try {
            getEntity().close()
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to close NonRDFSource entity.", e)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(identifier.toString())
        dest.writeString(contentType)
        dest.writeString(Json.encodeToString(headers.toMultimap()))
        dest.writeByteArray(getEntity().use { it.readBytes() })
    }
}