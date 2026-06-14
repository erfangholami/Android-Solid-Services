package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.vocab.DC
import com.erfangholami.androidsolidservices.shared.vocab.STAT
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

    /** The size of this resource in bytes (its `Content-Length`), or `0` when the server did not report one. */
    public fun getSize(): Long = metadata.contentLength.takeIf { it >= 0 } ?: 0L

    /**
     * The last-modified time in epoch milliseconds. Prefers this resource's own `dcterms:modified`
     * (then `stat:mtime`) triple and falls back to the `Last-Modified` header. `null` when none is present.
     */
    public fun getLastModified(): Long? {
        val subject = getIdentifier().toString()
        return findPropertyForSubject(subject, DC.MODIFIED)?.let(::parseIsoInstantMillis)
            ?: statSecondsToMillis(findPropertyForSubject(subject, STAT.MTIME)?.toLongOrNull())
            ?: parseHttpDateMillis(metadata.lastModified)
    }

    /**
     * The creation time in epoch milliseconds, from this resource's own `dcterms:created`
     * (then `stat:ctime`) triple, or `null` when neither is present.
     */
    public fun getCreatedTime(): Long? {
        val subject = getIdentifier().toString()
        return findPropertyForSubject(subject, DC.CREATED)?.let(::parseIsoInstantMillis)
            ?: statSecondsToMillis(findPropertyForSubject(subject, STAT.CTIME)?.toLongOrNull())
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
    }
}
