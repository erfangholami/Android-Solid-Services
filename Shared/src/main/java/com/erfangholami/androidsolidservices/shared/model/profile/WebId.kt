package com.erfangholami.androidsolidservices.shared.model.profile

import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.http.media.MediaType
import com.erfangholami.androidsolidservices.shared.model.profile.WebId.Companion.writeToString
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.util.toPlainString
import com.erfangholami.androidsolidservices.shared.util.tryParseUri
import com.erfangholami.androidsolidservices.shared.vocab.ACL
import com.erfangholami.androidsolidservices.shared.vocab.Cert
import com.erfangholami.androidsolidservices.shared.vocab.FOAF
import com.erfangholami.androidsolidservices.shared.vocab.LDP
import com.erfangholami.androidsolidservices.shared.vocab.PIM
import com.erfangholami.androidsolidservices.shared.vocab.RDF
import com.erfangholami.androidsolidservices.shared.vocab.RDFS
import com.erfangholami.androidsolidservices.shared.vocab.Solid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders

/**
 * Represents a Solid WebID identity document — both for reading profile data
 * and for constructing or updating it.
 *
 * A WebID document is an RDF resource that describes an agent. It MUST contain:
 * - `rdf:type foaf:Agent`
 * - `pim:preferencesFile` pointing to the agent's preferences file
 *
 * It MAY contain:
 * - `pim:storage` (one or more storage roots)
 * - `ldp:inbox` (notification inbox)
 * - `rdfs:seeAlso` (extended profile documents)
 * - `foaf:isPrimaryTopicOf`
 * - `solid:oidcIssuer` (OIDC identity provider)
 * - FOAF name / given name / family name / photo
 * - `foaf:knows` (social graph)
 * - `acl:trustedApp` (trusted client applications)
 * - `cert:key` (public key for WebID-TLS)
 *
 * Use [getMetadata] (from [SolidRDFResource]) to access the ETag, ACL URI, and
 * allowed methods returned by the server — useful for conditional PUT updates.
 *
 * Spec: https://solid.github.io/webid-profile/
 */
public open class WebId : SolidRDFResource {

    public companion object {
        private const val KEY_IDENTIFIER = "identifier"
        private const val KEY_TYPE = "type"
        private const val KEY_DATASET = "dataset"

        /**
         * Serializes [webId] to a compact JSON string for local storage.
         * Returns `null` if [webId] is `null`.
         */
        public fun writeToString(webId: WebId?): String? {
            webId ?: return null
            return buildJsonObject {
                put(KEY_IDENTIFIER, webId.getIdentifier())
                put(KEY_TYPE, webId.getContentType())
                put(KEY_DATASET, webId.getEntity().toPlainString())
            }.toString()
        }

        /**
         * Deserializes a [WebId] from a JSON string produced by [writeToString].
         */
        public fun readFromString(objectString: String): WebId {
            val obj = Json.parseToJsonElement(objectString).jsonObject
            return WebId(
                identifier = obj[KEY_IDENTIFIER]!!.jsonPrimitive.content,
                contentType = obj[KEY_TYPE]!!.jsonPrimitive.content,
                quads = parseJsonLd(obj[KEY_DATASET]!!.jsonPrimitive.content),
            )
        }
    }

    public constructor(identifier: String, quads: List<RdfQuad>) :
            super(identifier, quads)

    public constructor(identifier: String, contentType: String, quads: List<RdfQuad>) :
            super(identifier, contentType, quads, null)

    public constructor(identifier: String, contentType: String, quads: List<RdfQuad>, headers: SolidHeaders?) :
            super(identifier, contentType, quads, headers)

    /** Returns all `rdf:type` values declared in this profile. */
    public fun getTypes(): List<String> =
        findAllProperties(RDF.TYPE).mapNotNull { tryParseUri(it, "WebId.types")?.toString() }

    /** Returns the `pim:preferencesFile` URI, or `null` if not declared. */
    public fun getPreferencesFile(): String? =
        findProperty(PIM.PREFERENCES_FILE)?.let { tryParseUri(it, "WebId.preferencesFile")?.toString() }

    /** Returns all `pim:storage` root URIs declared in this profile. */
    public fun getStorages(): List<String> =
        findAllProperties(PIM.STORAGE).mapNotNull { tryParseUri(it, "WebId.storages")?.toString() }

    /** Returns the `ldp:inbox` URI, or `null` if not declared. */
    public fun getInbox(): String? =
        findProperty(LDP.INBOX)?.let { tryParseUri(it, "WebId.inbox")?.toString() }

    /** Returns all `rdfs:seeAlso` (extended profile) document URIs. */
    public fun getRelatedResources(): List<String> =
        findAllProperties(RDFS.SEE_ALSO).mapNotNull { tryParseUri(it, "WebId.seeAlso")?.toString() }

    /** Returns all `foaf:isPrimaryTopicOf` document URIs. */
    public fun getPrimaryTopicDocuments(): List<String> =
        findAllProperties(FOAF.IS_PRIMARY_TOPIC_OF)
            .mapNotNull { tryParseUri(it, "WebId.primaryTopicOf")?.toString() }

    /** Returns all `solid:oidcIssuer` URIs declared in this profile. */
    public fun getOidcIssuers(): List<String> =
        findAllProperties(Solid.OIDC_ISSUER).mapNotNull { tryParseUri(it, "WebId.oidcIssuer")?.toString() }

    /** Returns the `solid:privateTypeIndex` URI, or `null` if not declared. */
    public fun getPrivateTypeIndex(): String? =
        findProperty(Solid.PRIVATE_TYPE_INDEX)?.let { tryParseUri(it, "WebId.privateTypeIndex")?.toString() }

    /** Returns the `solid:publicTypeIndex` URI, or `null` if not declared. */
    public fun getPublicTypeIndex(): String? =
        findProperty(Solid.PUBLIC_TYPE_INDEX)?.let { tryParseUri(it, "WebId.publicTypeIndex")?.toString() }

    /** Returns the `foaf:name` literal, or `null` if not declared. */
    public fun getName(): String? = findProperty(FOAF.NAME)

    /** Returns the `foaf:givenName` literal, or `null` if not declared. */
    public fun getGivenName(): String? = findProperty(FOAF.GIVEN_NAME)

    /** Returns the `foaf:familyName` literal, or `null` if not declared. */
    public fun getFamilyName(): String? = findProperty(FOAF.FAMILY_NAME)

    /** Returns the `foaf:img` photo URI, or `null` if not declared. */
    public fun getPhoto(): String? =
        findProperty(FOAF.IMG)?.let { tryParseUri(it, "WebId.photo")?.toString() }

    /** Returns all `foaf:knows` WebID URIs listed in this profile. */
    public fun getKnows(): List<String> =
        findAllProperties(FOAF.KNOWS).mapNotNull { tryParseUri(it, "WebId.knows")?.toString() }

    /** Returns all `acl:trustedApp` URIs listed in this profile. */
    public fun getTrustedApps(): List<String> =
        findAllProperties(ACL.TRUSTED_APP)
            .mapNotNull { tryParseUri(it, "WebId.trustedApps")?.toString() }

    /** Returns all `cert:key` public-key URIs listed in this profile. */
    public fun getCertKeys(): List<String> =
        findAllProperties(Cert.KEY)
            .mapNotNull { tryParseUri(it, "WebId.certKeys")?.toString() }

    /**
     * Adds a `solid:privateTypeIndex` triple to this profile document.
     *
     * @param webId   The WebID subject IRI.
     * @param storage The pod storage root URI; used to derive the default [uri].
     * @param uri     The private type index URI; defaults to `{storage}/settings/privateTypeIndex`.
     */
    public fun setPrivateTypeIndex(
        webId: String,
        storage: String,
        uri: String = "$storage/settings/privateTypeIndex",
    ) {
        addQuad(webId, Solid.PRIVATE_TYPE_INDEX, uri)
    }

    /**
     * Adds a `solid:publicTypeIndex` triple to this profile document.
     *
     * @param webId   The WebID subject IRI.
     * @param storage The pod storage root URI; used to derive the default [uri].
     * @param uri     The public type index URI; defaults to `{storage}/settings/publicTypeIndex`.
     */
    public fun setPublicTypeIndex(
        webId: String,
        storage: String,
        uri: String = "$storage/settings/publicTypeIndex",
    ) {
        addQuad(webId, Solid.PUBLIC_TYPE_INDEX, uri)
    }

    /**
     * Adds a `pim:preferencesFile` triple to this profile document.
     *
     * @param webId   The WebID subject IRI.
     * @param storage The pod storage root URI; used to derive the default [uri].
     * @param uri     The preferences file URI; defaults to `{storage}/settings/prefs.ttl`.
     */
    public fun setPreferencesFile(
        webId: String,
        storage: String,
        uri: String = "$storage/settings/prefs.ttl",
    ) {
        addQuad(webId, PIM.PREFERENCES_FILE, uri)
    }

    /**
     * Adds a `pim:storage` triple to this profile document.
     *
     * @param webId      The WebID subject IRI.
     * @param storageUri The pod storage root URI.
     */
    public fun setStorage(webId: String, storageUri: String) {
        addQuad(webId, PIM.STORAGE, storageUri, maxNumber = Int.MAX_VALUE)
    }

    /**
     * Adds an `ldp:inbox` triple to this profile document.
     *
     * @param webId   The WebID subject IRI.
     * @param storage The pod storage root URI; used to derive the default [uri].
     * @param uri     The inbox container URI; defaults to `{storage}/inbox/`.
     */
    public fun setInbox(
        webId: String,
        storage: String,
        uri: String = "$storage/inbox/",
    ) {
        addQuad(webId, LDP.INBOX, uri)
    }

    /**
     * Adds a `solid:oidcIssuer` triple to this profile document.
     *
     * @param webId     The WebID subject IRI.
     * @param issuerUri The OIDC issuer URI.
     */
    public fun setOidcIssuer(webId: String, issuerUri: String) {
        addQuad(webId, Solid.OIDC_ISSUER, issuerUri, maxNumber = Int.MAX_VALUE)
    }
}
