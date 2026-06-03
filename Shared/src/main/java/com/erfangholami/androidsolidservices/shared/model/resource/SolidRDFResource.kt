package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * An [RDFResource] retrieved from a Solid server.
 *
 * Adds server-supplied [SolidMetadata] (parsed from the response headers — ACL URI,
 * `WAC-Allow`, allowed methods, ETag, advertised types, and so on) on top of the
 * in-memory triple model inherited from [RDFResource]. This is the type returned for
 * non-container RDF documents read from a pod.
 */
public open class SolidRDFResource : RDFResource, SolidResource {

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<SolidRDFResource> = object : Parcelable.Creator<SolidRDFResource> {
            override fun createFromParcel(parcel: Parcel): SolidRDFResource =
                SolidRDFResource(parcel)

            override fun newArray(size: Int): Array<SolidRDFResource?> = arrayOfNulls(size)
        }
    }

    private val metadata: SolidMetadata = SolidMetadata.from(getHeaders())

    protected constructor(inParcel: Parcel) : super(inParcel)

    public constructor(identifier: URI) : this(identifier, null)

    public constructor(identifier: URI, quads: List<RdfQuad>?) :
            this(identifier, quads, null)

    public constructor(identifier: URI, quads: List<RdfQuad>?, headers: SolidHeaders?) :
            this(identifier, "application/ld+json", quads, headers)

    public constructor(
        identifier: URI,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?,
    ) : super(identifier, contentType, quads, headers)

    override fun getMetadata(): SolidMetadata = metadata

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
    }
}
