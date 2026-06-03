package com.erfangholami.androidsolidservices.shared.model.typeindex
import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * A Solid public type index document (`solid:TypeIndex` + `solid:ListedDocument`).
 *
 * Registrations in the public type index are world-readable — they advertise what
 * types of data a pod contains so that other applications can discover and request
 * access to them. Use this for resources you are willing to share or make known, such
 * as a shared contacts address book.
 *
 * The public type index URI is advertised in the owner's WebID profile via
 * `solid:publicTypeIndex`. Use [getPublicTypeIndex] on [WebId] to retrieve it.
 *
 * Spec: https://solid.github.io/webid-profile/ — Type Indexes
 */
public class PublicTypeIndex : SettingTypeIndex {

    public constructor(
        identifier: URI,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers)

    override fun setTypes() {
        val existing = quads.filter {
            it.subject == getIdentifier().toString() && it.predicate == typeKey
        }
        if (existing.isEmpty()) {
            addQuad(getIdentifier().toString(), typeKey, typeIndex, maxNumber = Int.MAX_VALUE)
            addQuad(getIdentifier().toString(), typeKey, listedDocument, maxNumber = Int.MAX_VALUE)
        }
    }
}