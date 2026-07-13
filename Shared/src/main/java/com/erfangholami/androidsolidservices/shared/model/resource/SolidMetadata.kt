package com.erfangholami.androidsolidservices.shared.model.resource

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.util.getAcceptPatch
import com.erfangholami.androidsolidservices.shared.util.getAcceptPost
import com.erfangholami.androidsolidservices.shared.util.getAcceptPut
import com.erfangholami.androidsolidservices.shared.util.getAclUri
import com.erfangholami.androidsolidservices.shared.util.getAllowedMethods
import com.erfangholami.androidsolidservices.shared.util.getContentLength
import com.erfangholami.androidsolidservices.shared.util.getContentType
import com.erfangholami.androidsolidservices.shared.util.getDescribedByUri
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.androidsolidservices.shared.util.getInboxUri
import com.erfangholami.androidsolidservices.shared.util.getLastModified
import com.erfangholami.androidsolidservices.shared.util.getLinkTypeUris
import com.erfangholami.androidsolidservices.shared.util.getLocation
import com.erfangholami.androidsolidservices.shared.util.getOidcIssuerUri
import com.erfangholami.androidsolidservices.shared.util.getOwnerUri
import com.erfangholami.androidsolidservices.shared.util.getStorageDescriptionUri
import com.erfangholami.androidsolidservices.shared.util.getWacAllow
import com.erfangholami.androidsolidservices.shared.util.getWwwAuthenticate
import com.erfangholami.androidsolidservices.shared.util.isStorage
import com.erfangholami.androidsolidservices.shared.util.tryParseUri
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * Solid-specific metadata extracted from HTTP response headers for a resource
 * retrieved from a Solid server.
 *
 * Each field corresponds to a header defined by the Solid Protocol or a spec it builds on:
 * - [aclUri] — Solid Protocol auxiliary resources: `Link: rel="acl"`
 * - [storageDescriptionUri] — Solid Protocol: `Link: rel="storageDescription"`
 * - [ownerUri] — Solid Protocol: `Link: rel="solid:owner"`
 * - [wacAllow] — Web Access Control: `WAC-Allow` header
 * - [allowedMethods] — HTTP + Solid Protocol: `Allow` header
 * - [linkTypes] — LDP + Solid Protocol: `Link: rel="type"` header
 * - [etag] — HTTP: `ETag` header
 * - [oidcIssuerUri] — Solid-OIDC: `Link: rel="solid:oidcIssuer"`
 * - [isStorage] — Solid Protocol: derived from `Link: rel="type" <pim:Storage>`
 *
 * The packaging of these fields into a single class is an implementation
 * convenience and is not defined by any Solid spec.
 */
public data class SolidMetadata(
    /** URI of the WAC/ACP access-control resource. From `Link: rel="acl"`. */
    val aclUri: String?,

    /** URI of the storage description resource. From `Link: rel="storageDescription"`. */
    val storageDescriptionUri: String?,

    /** URI of the storage owner. From `Link: rel="solid:owner"`. */
    val ownerUri: String?,

    /** Parsed `WAC-Allow` header — what modes the current caller has on this resource. */
    val wacAllow: WacAllow?,

    /** HTTP methods the server accepts for this resource. From `Allow` header. */
    val allowedMethods: Set<String>,

    /**
     * RDF type IRIs advertised via `Link: <iri>; rel="type"` headers.
     * Common values: `ldp:Resource`, `ldp:BasicContainer`, `pim:Storage`.
     */
    val linkTypes: Set<String>,

    /** ETag for cache validation and conditional requests. From `ETag` header. */
    val etag: String?,

    /** Last-Modified timestamp string. From `Last-Modified` header. */
    val lastModified: String?,

    /** Location URI set on 201 Created responses. From `Location` header. */
    val location: String?,

    /** Media type of the resource. From `Content-Type` header. */
    val contentType: String?,

    /** Size of the resource body in bytes. From `Content-Length` header. -1 if absent. */
    val contentLength: Long,

    /** URI of the resource that describes this resource. From `Link: rel="describedby"`. */
    val describeByUri: String?,

    /** Media types accepted for PATCH requests. From `Accept-Patch` header. */
    val acceptPatch: List<String>,

    /** Media types accepted for POST requests. From `Accept-Post` header. */
    val acceptPost: List<String>,

    /** Media types accepted for PUT requests. From `Accept-Put` header. */
    val acceptPut: List<String>,

    /** WWW-Authenticate challenge. From `WWW-Authenticate` header. Present on 401 responses. */
    val wwwAuthenticate: String?,

    /**
     * OIDC issuer URI for this resource or WebID.
     * From `Link: <iri>; rel="http://www.w3.org/ns/solid/terms#oidcIssuer"`.
     * Spec: https://solidproject.org/TR/oidc
     */
    val oidcIssuerUri: String?,

    /**
     * Whether this resource is a Solid pod storage root.
     * True when `Link: rel="type" <http://www.w3.org/ns/pim/space#Storage>` is present.
     */
    val isStorage: Boolean,

    /**
     * URI of the LDN inbox advertised by `Link: rel="http://www.w3.org/ns/ldp#inbox"`.
     * Used as a fallback when a WebID's profile body doesn't carry an
     * `ldp:inbox` predicate, since the inbox may be exposed either way.
     */
    val inboxUri: String? = null,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(aclUri)
        dest.writeString(storageDescriptionUri)
        dest.writeString(ownerUri)
        dest.writeParcelable(wacAllow, flags)
        dest.writeStringList(allowedMethods.toList())
        dest.writeStringList(linkTypes.toList())
        dest.writeString(etag)
        dest.writeString(lastModified)
        dest.writeString(location)
        dest.writeString(contentType)
        dest.writeLong(contentLength)
        dest.writeString(describeByUri)
        dest.writeStringList(acceptPatch)
        dest.writeStringList(acceptPost)
        dest.writeStringList(acceptPut)
        dest.writeString(wwwAuthenticate)
        dest.writeString(oidcIssuerUri)
        dest.writeByte(if (isStorage) 1 else 0)
        dest.writeString(inboxUri)
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<SolidMetadata> = object : Parcelable.Creator<SolidMetadata> {
            override fun createFromParcel(parcel: Parcel): SolidMetadata = SolidMetadata(
                aclUri = parcel.readString(),
                storageDescriptionUri = parcel.readString(),
                ownerUri = parcel.readString(),
                wacAllow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    parcel.readParcelable(WacAllow::class.java.classLoader, WacAllow::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    parcel.readParcelable(WacAllow::class.java.classLoader)
                },
                allowedMethods = parcel.createStringArrayList()!!.toSet(),
                linkTypes = parcel.createStringArrayList()!!.toSet(),
                etag = parcel.readString(),
                lastModified = parcel.readString(),
                location = parcel.readString(),
                contentType = parcel.readString(),
                contentLength = parcel.readLong(),
                describeByUri = parcel.readString(),
                acceptPatch = parcel.createStringArrayList()!!,
                acceptPost = parcel.createStringArrayList()!!,
                acceptPut = parcel.createStringArrayList()!!,
                wwwAuthenticate = parcel.readString(),
                oidcIssuerUri = parcel.readString(),
                isStorage = parcel.readByte() != 0.toByte(),
                inboxUri = parcel.readString(),
            )

            override fun newArray(size: Int): Array<SolidMetadata?> = arrayOfNulls(size)
        }

        /** Builds a [SolidMetadata] by extracting the relevant fields from HTTP response [headers]. */
        public fun from(headers: SolidHeaders): SolidMetadata = SolidMetadata(
            aclUri = headers.getAclUri(),
            storageDescriptionUri = headers.getStorageDescriptionUri(),
            ownerUri = headers.getOwnerUri(),
            wacAllow = headers.getWacAllow(),
            allowedMethods = headers.getAllowedMethods(),
            linkTypes = headers.getLinkTypeUris(),
            etag = headers.getETag(),
            lastModified = headers.getLastModified(),
            location = headers.getLocation(),
            contentType = headers.getContentType(),
            contentLength = headers.getContentLength(),
            describeByUri = headers.getDescribedByUri(),
            acceptPatch = headers.getAcceptPatch(),
            acceptPost = headers.getAcceptPost(),
            acceptPut = headers.getAcceptPut(),
            wwwAuthenticate = headers.getWwwAuthenticate(),
            oidcIssuerUri = headers.getOidcIssuerUri(),
            isStorage = headers.isStorage(),
            inboxUri = headers.getInboxUri(),
        )

        /** A metadata instance with every field absent; useful as a default or placeholder. */
        public val EMPTY: SolidMetadata = SolidMetadata(
            aclUri = null,
            storageDescriptionUri = null,
            ownerUri = null,
            wacAllow = null,
            allowedMethods = emptySet(),
            linkTypes = emptySet(),
            etag = null,
            lastModified = null,
            location = null,
            contentType = null,
            contentLength = -1L,
            describeByUri = null,
            acceptPatch = emptyList(),
            acceptPost = emptyList(),
            acceptPut = emptyList(),
            wwwAuthenticate = null,
            oidcIssuerUri = null,
            isStorage = false,
            inboxUri = null,
        )
    }
}
