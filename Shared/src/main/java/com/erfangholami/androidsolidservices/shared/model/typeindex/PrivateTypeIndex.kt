package com.erfangholami.androidsolidservices.shared.model.typeindex
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad

/**
 * A Solid private type index document (`solid:TypeIndex` + `solid:UnlistedDocument`).
 *
 * Registrations in the private type index are not publicly discoverable — they are
 * only visible to the pod owner. Use this for resources that should remain private,
 * such as a personal contacts address book.
 *
 * The private type index URI is advertised in the owner's WebID profile via
 * `solid:privateTypeIndex`. Use [getPrivateTypeIndex] on [WebId] to retrieve it.
 *
 * Spec: https://solid.github.io/webid-profile/ — Type Indexes
 */
public class PrivateTypeIndex : SettingTypeIndex {

    public constructor(
        identifier: String,
        contentType: String,
        quads: List<RdfQuad>?,
        headers: SolidHeaders?
    ) : super(identifier, contentType, quads, headers)

    override fun setTypes() {
        val existing = quads.filter {
            it.subject == getIdentifier() && it.predicate == typeKey
        }
        if (existing.isEmpty()) {
            addQuad(getIdentifier(), typeKey, typeIndex, maxNumber = Int.MAX_VALUE)
            addQuad(
                getIdentifier(),
                typeKey,
                unlistedDocument,
                maxNumber = Int.MAX_VALUE
            )
        }
    }
}
