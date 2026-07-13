package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.io.InputStream

/**
 * A [NonRDFResource] (binary) retrieved from a Solid server.
 *
 * Adds server-supplied [SolidMetadata] (parsed from the response headers — content type
 * and length, ACL URI, `WAC-Allow`, ETag, and so on) on top of the opaque byte stream
 * inherited from [NonRDFResource]. This is the type returned for binary resources read
 * from a pod.
 */
public open class SolidNonRDFResource : NonRDFResource, SolidResource {

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<SolidNonRDFResource> = object : Parcelable.Creator<SolidNonRDFResource> {
            override fun createFromParcel(parcel: Parcel): SolidNonRDFResource =
                SolidNonRDFResource(parcel)

            override fun newArray(size: Int): Array<SolidNonRDFResource?> = arrayOfNulls(size)
        }
    }

    private val metadata: SolidMetadata = SolidMetadata.from(getHeaders())

    protected constructor(inParcel: Parcel) : super(inParcel)

    public constructor(
        identifier: String,
        contentType: String,
        entity: InputStream,
    ) : this(identifier, contentType, entity, null)

    public constructor(
        identifier: String,
        contentType: String,
        entity: InputStream,
        headers: SolidHeaders?,
    ) : super(identifier, contentType, headers, entity)

    override fun getMetadata(): SolidMetadata = metadata

    /** The size of this resource in bytes (its `Content-Length`), or `0` when the server did not report one. */
    public fun getSize(): Long = metadata.contentLength.takeIf { it >= 0 } ?: 0L

    /** The last-modified time in epoch milliseconds (the `Last-Modified` header), or `null` when absent. */
    public fun getLastModified(): Long? = parseHttpDateMillis(metadata.lastModified)

    /**
     * The creation time in epoch milliseconds. Always `null` for a binary resource: HTTP exposes no
     * creation header, and a non-RDF resource carries no triples from which to read `dcterms:created`.
     */
    public fun getCreatedTime(): Long? = null

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
    }
}
