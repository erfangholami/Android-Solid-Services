package com.erfangholami.androidsolidservices.shared.model.resource

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.util.getOwnerUri
import com.erfangholami.androidsolidservices.shared.util.getStorageDescriptionUri
import com.erfangholami.androidsolidservices.shared.util.tryParseUri
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * Represents a Solid pod storage root container.
 *
 * A storage is an LDP BasicContainer that carries
 * `rdf:type pim:Storage` and is advertised via
 * `Link: rel="type" <http://www.w3.org/ns/pim/space#Storage>`.
 *
 * Spec: https://solidproject.org/TR/protocol — Storage
 */
public class SolidStorage : SolidContainer {

    public constructor(identifier: URI) : this(identifier, null, null)

    public constructor(identifier: URI, quads: List<RdfQuad>?, headers: SolidHeaders?) :
            this(identifier, "application/ld+json", quads, headers)

    public constructor(
        identifier: URI,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers)

    /**
     * Returns the WebID of this storage's owner, or `null` if not advertised.
     *
     * Prefers a `solid:owner` triple in the storage description and falls back to the
     * `Link: rel="solid:owner"` response header.
     */
    public fun getOwner(): URI? {
        val fromDataset = findPropertyForSubject(getIdentifier().toString(), Solid.OWNER)
            ?.let { tryParseUri(it, "SolidStorage.owner") }
        if (fromDataset != null) return fromDataset
        return getHeaders().getOwnerUri()
    }

    /**
     * Returns the URI of this storage's storage-description resource, or `null` if not
     * advertised.
     *
     * Prefers a `solid:storageDescription` triple and falls back to the
     * `Link: rel="storageDescription"` response header.
     */
    public fun getStorageDescriptionUri(): URI? {
        val fromDataset =
            findPropertyForSubject(getIdentifier().toString(), Solid.STORAGE_DESCRIPTION)
                ?.let { tryParseUri(it, "SolidStorage.storageDescription") }
        if (fromDataset != null) return fromDataset
        return getHeaders().getStorageDescriptionUri()
    }

    /** Returns `true` if this resource's triples assert `rdf:type pim:Storage`. */
    public fun isStorageType(): Boolean =
        quads.any {
            it.subject == getIdentifier().toString() &&
                    it.predicate == RDF.TYPE &&
                    it.`object` == PIM.STORAGE_TYPE
        }
}