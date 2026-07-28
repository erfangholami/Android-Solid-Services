package com.erfangholami.androidsolidservices.shared.http

/**
 * HTTP header name constants.
 * See https://solidproject.org/TR/protocol for Solid-specific headers.
 */
public object HTTPHeaderName {
    public const val AUTHORIZATION: String = "Authorization"
    public const val ACCEPT: String = "Accept"
    public const val CONTENT_TYPE: String = "Content-Type"
    public const val CONTENT_LENGTH: String = "Content-Length"
    public const val LINK: String = "Link"
    public const val IF_NONE_MATCH: String = "If-None-Match"
    public const val IF_MATCH: String = "If-Match"
    public const val IF_UNMODIFIED_SINCE: String = "If-Unmodified-Since"
    public const val IF_MODIFIED_SINCE: String = "If-Modified-Since"
    public const val ORIGIN: String = "Origin"
    public const val HOST: String = "Host"

    public const val ETAG: String = "ETag"
    public const val LAST_MODIFIED: String = "Last-Modified"
    public const val LOCATION: String = "Location"
    public const val ALLOW: String = "Allow"
    public const val VARY: String = "Vary"
    public const val WWW_AUTHENTICATE: String = "WWW-Authenticate"

    public const val ACCESS_CONTROL_ALLOW_ORIGIN: String = "Access-Control-Allow-Origin"
    public const val ACCESS_CONTROL_ALLOW_METHODS: String = "Access-Control-Allow-Methods"
    public const val ACCESS_CONTROL_ALLOW_HEADERS: String = "Access-Control-Allow-SolidHeaders"
    public const val ACCESS_CONTROL_EXPOSE_HEADERS: String = "Access-Control-Expose-SolidHeaders"
    public const val ACCESS_CONTROL_MAX_AGE: String = "Access-Control-Max-Age"

    public const val ACCEPT_PATCH: String = "Accept-Patch"
    public const val ACCEPT_POST: String = "Accept-Post"
    public const val ACCEPT_PUT: String = "Accept-Put"

    public const val DPOP: String = "DPoP"
    public const val DPOP_NONCE: String = "DPoP-Nonce"

    /** WAC-Allow: user="read write", public="read" */
    public const val WAC_ALLOW: String = "WAC-Allow"
}

/**
 * HTTP media type / content type constants.
 */
public object HTTPAcceptType {
    public const val JSON_LD: String = "application/ld+json"
    public const val TURTLE: String = "text/turtle"
    public const val N3: String = "text/n3"
    public const val N_TRIPLES: String = "application/n-triples"
    public const val N_QUADS: String = "application/n-quads"
    public const val TRIG: String = "application/trig"
    public const val RDF_XML: String = "application/rdf+xml"
    public const val JSON_RDF: String = "application/rdf+json"

    /** SPARQL 1.1 Update — accepted by every major Solid server for PATCH. */
    public const val SPARQL_UPDATE: String = "application/sparql-update"

    public const val JSON: String = "application/json"
    public const val OCTET_STREAM: String = "application/octet-stream"
    public const val FORM_URL_ENCODED: String = "application/x-www-form-urlencoded"
    public const val MULTIPART_FORM_DATA: String = "multipart/form-data"
    public const val TEXT_PLAIN: String = "text/plain"
    public const val TEXT_HTML: String = "text/html"
    public const val ANY: String = "*/*"
}

/**
 * HTTP Link header relation type constants.
 * Used in Solid `Link:` headers.
 * See https://solidproject.org/TR/protocol
 */
public object HTTPLinkRelation {
    /** The ACL resource associated with this resource (WAC and ACP). */
    public const val ACL: String = "acl"

    /** A resource that describes this resource. */
    public const val DESCRIBED_BY: String = "describedby"

    /** This resource describes the context resource (inverse of describedby). */
    public const val DESCRIBES: String = "describes"

    /** The rdf:type(s) of this resource. */
    public const val TYPE: String = "type"

    /**
     * The storage description resource.
     * Full IRI: http://www.w3.org/ns/solid/terms#storageDescription
     */
    public const val STORAGE_DESCRIPTION: String = "http://www.w3.org/ns/solid/terms#storageDescription"

    /**
     * The owner of a storage.
     * Full IRI: http://www.w3.org/ns/solid/terms#owner
     */
    public const val OWNER: String = "http://www.w3.org/ns/solid/terms#owner"

    /**
     * The OIDC issuer for this resource / WebID.
     * Full IRI: http://www.w3.org/ns/solid/terms#oidcIssuer
     */
    public const val OIDC_ISSUER: String = "http://www.w3.org/ns/solid/terms#oidcIssuer"

    /** Canonical URL of a resource. */
    public const val CANONICAL: String = "canonical"

    /** Predecessor version in a version chain. */
    public const val PREDECESSOR_VERSION: String = "predecessor-version"

    /** Alternate representation. */
    public const val ALTERNATE: String = "alternate"
}

/**
 * HTTP status code constants used in the Solid protocol.
 * See https://solidproject.org/TR/protocol
 */
public object HTTPStatusCode {
    public const val OK: Int = 200
    public const val CREATED: Int = 201
    public const val NO_CONTENT: Int = 204

    public const val MOVED_PERMANENTLY: Int = 301
    public const val TEMPORARY_REDIRECT: Int = 307
    public const val PERMANENT_REDIRECT: Int = 308

    public const val BAD_REQUEST: Int = 400
    public const val UNAUTHORIZED: Int = 401
    public const val FORBIDDEN: Int = 403
    public const val NOT_FOUND: Int = 404
    public const val METHOD_NOT_ALLOWED: Int = 405
    public const val CONFLICT: Int = 409
    public const val GONE: Int = 410
    public const val PRECONDITION_FAILED: Int = 412
    public const val UNPROCESSABLE_ENTITY: Int = 422

    public const val INTERNAL_SERVER_ERROR: Int = 500
}
