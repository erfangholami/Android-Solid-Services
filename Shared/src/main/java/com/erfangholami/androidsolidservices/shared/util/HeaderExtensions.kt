package com.erfangholami.androidsolidservices.shared.util

import com.erfangholami.androidsolidservices.shared.http.EntityTag
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.http.HTTPLinkRelation
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import java.net.URI

/**
 * Extension functions for parsing Solid-specific HTTP headers from OkHttp [SolidHeaders].
 * All functions return `null` / empty when the header is absent.
 *
 * Spec: https://solidproject.org/TR/protocol
 */

public fun SolidHeaders.getContentLength(): Long {
    return this["content-length"]?.toLongOrNull() ?: -1L
}

public fun SolidHeaders.getContentType(): String? = get(HTTPHeaderName.CONTENT_TYPE)

public fun SolidHeaders.getAcceptPatch(): List<String> =
    get(HTTPHeaderName.ACCEPT_PATCH)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

public fun SolidHeaders.getAcceptPost(): List<String> =
    get(HTTPHeaderName.ACCEPT_POST)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

public fun SolidHeaders.getAcceptPut(): List<String> =
    get(HTTPHeaderName.ACCEPT_PUT)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

public fun SolidHeaders.getWwwAuthenticate(): String? = get(HTTPHeaderName.WWW_AUTHENTICATE)

/**
 * Returns the URI of the WAC / ACP access-control resource advertised by
 * `Link: <...>; rel="acl"`, or `null` if absent.
 */
public fun SolidHeaders.getAclUri(): String? =
    parseLinkRelation(this, HTTPLinkRelation.ACL)?.toString()

/**
 * Returns the URI of the description resource advertised by
 * `Link: <...>; rel="describedby"`, or `null` if absent.
 */
public fun SolidHeaders.getDescribedByUri(): String? =
    parseLinkRelation(this, HTTPLinkRelation.DESCRIBED_BY)?.toString()

/**
 * Returns the URI of the storage description resource advertised by
 * `Link: <...>; rel="http://www.w3.org/ns/solid/terms#storageDescription"`,
 * or `null` if absent.
 */
public fun SolidHeaders.getStorageDescriptionUri(): String? =
    parseLinkRelation(this, HTTPLinkRelation.STORAGE_DESCRIPTION)?.toString()

/**
 * Returns the URI of the storage owner advertised by
 * `Link: <...>; rel="http://www.w3.org/ns/solid/terms#owner"`,
 * or `null` if absent.
 */
public fun SolidHeaders.getOwnerUri(): String? =
    parseLinkRelation(this, HTTPLinkRelation.OWNER)?.toString()

/**
 * Returns the URI of the LDN inbox advertised by
 * `Link: <...>; rel="http://www.w3.org/ns/ldp#inbox"`, or `null` if absent.
 * Solid Protocol §4.1.1 allows this on storage roots and on WebID URLs;
 * agents may also expose it via an `ldp:inbox` triple in their profile.
 */
public fun SolidHeaders.getInboxUri(): String? =
    parseLinkRelation(this, LDP.INBOX)?.toString()

/**
 * Returns the OIDC issuer URI advertised by
 * `Link: <...>; rel="http://www.w3.org/ns/solid/terms#oidcIssuer"`,
 * or `null` if absent.
 *
 * Spec: https://solidproject.org/TR/oidc — Solid-OIDC issuer discovery
 */
public fun SolidHeaders.getOidcIssuerUri(): String? =
    parseLinkRelation(this, HTTPLinkRelation.OIDC_ISSUER)?.toString()

/**
 * Returns `true` when the `Link` header contains
 * `rel="type" <http://www.w3.org/ns/pim/space#Storage>`,
 * indicating this resource is a Solid pod storage root.
 */
public fun SolidHeaders.isStorage(): Boolean {
    return values(HTTPHeaderName.LINK).any { headerValue ->
        headerValue.contains(PIM.STORAGE_TYPE) &&
                headerValue.contains("""rel="${HTTPLinkRelation.TYPE}"""")
                    .or(headerValue.contains("rel=${HTTPLinkRelation.TYPE}"))
    }
}

/**
 * Returns the bare ETag value from the `ETag` response header, with surrounding
 * quotes stripped, suitable for use as an `If-Match` validator.
 *
 * Returns `null` if the header is absent **or carries a weak validator**
 * (`W/"..."`). The only consumers of this value in the codebase feed it to
 * `If-Match`, which requires the strong comparison function (RFC 7232 §3.1):
 * a weak validator can never match, so sending it guarantees a `412 Precondition
 * Failed`. Some servers (e.g. Node Solid Server / solidcommunity.net) emit only
 * weak ETags for RDF resources; reporting `null` here makes those writes fall
 * back to unconditional, which is the correct behaviour for a weak validator.
 */
public fun SolidHeaders.getETag(): String? =
    getEntityTag()?.takeIf { !it.weak }?.value

/**
 * Returns the full [EntityTag] from the `ETag` response header — including a **weak**
 * validator (`W/"..."`), unlike [getETag] which reports only strong tags. Returns
 * `null` when the header is absent or blank.
 *
 * Use this when the strength matters: a strong tag drives an `If-Match` conditional
 * write, while a weak tag (all Node Solid Server emits for RDF) can't satisfy
 * `If-Match` and the caller should fall back to `If-Unmodified-Since` instead.
 */
public fun SolidHeaders.getEntityTag(): EntityTag? =
    EntityTag.parse(get(HTTPHeaderName.ETAG))

/**
 * Returns the `Last-Modified` header value, or `null` if absent.
 */
public fun SolidHeaders.getLastModified(): String? = get(HTTPHeaderName.LAST_MODIFIED)

/**
 * Returns the `Location` header value as a [URI], or `null` if absent.
 * This is set on 201 Created responses.
 */
public fun SolidHeaders.getLocation(): String? =
    get(HTTPHeaderName.LOCATION)?.let { tryParseUri(it, "SolidHeaders.getLocation") }?.toString()

/**
 * Returns the set of HTTP methods listed in the `Allow` response header,
 * or an empty set if the header is absent.
 * Example: `Allow: GET, HEAD, OPTIONS, PUT, PATCH, DELETE`
 */
public fun SolidHeaders.getAllowedMethods(): Set<String> =
    get(HTTPHeaderName.ALLOW)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

/**
 * Returns the parsed [WacAllow] from the `WAC-Allow` response header,
 * or `null` if the header is absent or malformed.
 */
public fun SolidHeaders.getWacAllow(): WacAllow? =
    WacAllow.parse(get(HTTPHeaderName.WAC_ALLOW))

/**
 * Returns all `rel` values listed in `Link` headers as a flat list of strings.
 */
public fun SolidHeaders.getLinkRelTypes(): List<String> {
    val results = mutableListOf<String>()
    values(HTTPHeaderName.LINK).forEach { headerValue ->
        val relRegex = Regex("""rel="?([^";,\s]+)"?""")
        relRegex.findAll(headerValue).forEach { results.add(it.groupValues[1]) }
    }
    return results
}

/**
 * Returns the set of type URIs advertised via `Link: <uri>; rel="type"` headers.
 * Used to identify resource types such as `ldp:BasicContainer` or `pim:Storage`.
 */
public fun SolidHeaders.getLinkTypeUris(): Set<String> {
    val results = mutableSetOf<String>()
    values(HTTPHeaderName.LINK).forEach { headerValue ->
        headerValue.split(Regex(",(?=\\s*<)")).forEach { segment ->
            val relMatch = Regex("""rel="?([^";,\s]+)"?""").find(segment) ?: return@forEach
            if (relMatch.groupValues[1] == HTTPLinkRelation.TYPE) {
                val uriMatch = Regex("""<([^>]+)>""").find(segment) ?: return@forEach
                tryParseUri(uriMatch.groupValues[1], "SolidHeaders.getLinkTypeUris")
                    ?.let { results.add(it.toString()) }
            }
        }
    }
    return results
}

private fun parseLinkRelation(headers: SolidHeaders, rel: String): URI? {
    headers.values(HTTPHeaderName.LINK).forEach { headerValue ->
        headerValue.split(Regex(",(?=\\s*<)")).forEach { segment ->
            val uriMatch = Regex("""<([^>]+)>""").find(segment) ?: return@forEach
            val relMatch = Regex("""rel="?([^";,\s]+)"?""").find(segment) ?: return@forEach
            if (relMatch.groupValues[1] == rel) {
                return tryParseUri(uriMatch.groupValues[1], "SolidHeaders.parseLinkRelation(rel=$rel)")
            }
        }
    }
    return null
}
