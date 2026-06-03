package com.erfangholami.androidsolidservices.shared.rdf.contacts

import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.contacts.Group
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.VCARD
import com.erfangholami.androidsolidservices.shared.vocab.XSD
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import java.net.URI

/**
 * RDF representation of the groups index document for a Solid address book.
 *
 * The groups index lists all `vcard:Group` resources belonging to an address book,
 * using `vcard:includesGroup` triples. It is separate from the address-book root so
 * that group membership can be loaded on demand.
 *
 * Construct from a pod response by passing the parsed quads, or create a new empty
 * instance by supplying only the identifier.
 */
public class GroupsIndexRDF : SolidRDFResource {

    public constructor(
        identifier: URI,
        contentType: String? = null,
        quads: List<RdfQuad>? = null,
        headers: SolidHeaders? = null
    ) : super(identifier, contentType ?: "application/ld+json", quads, headers)

    /**
     * Returns lightweight [Group] summaries for all groups belonging to [addressBookUri].
     *
     * Groups whose `vcard:fn` is missing from this index are silently skipped.
     */
    public fun getGroups(addressBookUri: String): List<Group> =
        quads
            .filter { it.predicate == VCARD.INCLUDES_GROUP && it.subject == addressBookUri }
            .mapNotNull { triple ->
                val groupName = quads.find {
                    it.subject == triple.`object` && it.predicate == VCARD.FN
                }?.`object` ?: return@mapNotNull null
                Group(triple.`object`, groupName)
            }

    /**
     * Adds [group] to this index under [addressBookUri], recording the group's type and
     * display name inline so the index can be rendered without fetching each group document.
     */
    public fun addGroup(addressBookUri: String, group: GroupRDF) {
        val groupUri = group.getIdentifier().toString()
        addQuad(groupUri, RDF.TYPE, VCARD.GROUP)
        addQuadLiteral(groupUri, VCARD.FN, group.getTitle(), XSD.STRING)
        addQuad(addressBookUri, VCARD.INCLUDES_GROUP, groupUri, maxNumber = Int.MAX_VALUE)
    }

    /**
     * Removes all index entries for [groupUri] (both as subject and as object).
     *
     * @return `true` if any entries were removed, `false` if the group was not found.
     */
    public fun removeGroup(groupUri: URI): Boolean {
        val groupStr = groupUri.toString()
        val affected = quads.filter { it.subject == groupStr || it.`object` == groupStr }
        if (affected.isEmpty()) return false
        quads.removeAll(affected)
        return true
    }
}
