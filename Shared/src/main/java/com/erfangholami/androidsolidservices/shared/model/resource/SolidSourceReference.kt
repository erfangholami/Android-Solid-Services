package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.vocab.LDP

/**
 * A lightweight reference to a resource contained inside a [SolidContainer].
 * Populated from the container's RDF dataset, which Solid servers SHOULD enrich
 * with stat metadata per https://solidproject.org/TR/protocol (section on
 * container contained resource metadata).
 *
 * @property identifier  The IRI of the contained resource.
 * @property types       List of `rdf:type` IRI values for the resource.
 * @property size        File size in bytes (`stat:size`), if provided by the server.
 * @property modified    Last-modified datetime string (`dcterms:modified`), if provided.
 * @property mtime       Unix timestamp of last modification (`stat:mtime`), if provided.
 * @property contentType Content-type hint derived from `rdf:type` media-type URI, if present.
 * @property headMetadata Full HTTP HEAD metadata for this resource, populated after a HEAD request.
 */
public data class SolidSourceReference(
    val identifier: String,
    val types: List<String>,
    val size: Long? = null,
    val modified: String? = null,
    val mtime: Long? = null,
    val contentType: String? = null,
    val headMetadata: SolidMetadata? = null,
) : Parcelable {

    /**
     * Returns `true` if [types] includes any LDP container type. This is the reliable
     * check, but only works when the container listing enriched this reference with the
     * child's `rdf:type`; use [isContainerByUri] as a fallback when [types] is empty.
     */
    public fun isContainer(): Boolean =
        types.any {
            it == LDP.BASIC_CONTAINER ||
                    it == LDP.CONTAINER ||
                    it == LDP.DIRECT_CONTAINER ||
                    it == LDP.INDIRECT_CONTAINER
        }

    /**
     * Returns `true` if the [identifier] ends with `/`, the Solid convention for a
     * container URI. A cheap heuristic for when [types] is unavailable.
     */
    public fun isContainerByUri(): Boolean = identifier.endsWith("/")

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(identifier)
        dest.writeStringList(types)
        dest.writeValue(size)
        dest.writeString(modified)
        dest.writeValue(mtime)
        dest.writeString(contentType)
        dest.writeParcelable(headMetadata, flags)
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<SolidSourceReference> = object : Parcelable.Creator<SolidSourceReference> {
            override fun createFromParcel(parcel: Parcel): SolidSourceReference =
                SolidSourceReference(
                    identifier = parcel.readString()!!,
                    types = parcel.createStringArrayList()!!,
                    size = parcel.readValue(Long::class.java.classLoader) as Long?,
                    modified = parcel.readString(),
                    mtime = parcel.readValue(Long::class.java.classLoader) as Long?,
                    contentType = parcel.readString(),
                    headMetadata = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        parcel.readParcelable(SolidMetadata::class.java.classLoader, SolidMetadata::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        parcel.readParcelable(SolidMetadata::class.java.classLoader)
                    },
                )

            override fun newArray(size: Int): Array<SolidSourceReference?> = arrayOfNulls(size)
        }
    }
}
