package com.erfangholami.androidsolidservices.api.resource.implementation

import com.erfangholami.androidsolidservices.api.access.InruptAcrJson
import com.erfangholami.androidsolidservices.api.access.NTriples
import com.erfangholami.androidsolidservices.api.transport.SolidRawResponse
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType
import com.erfangholami.androidsolidservices.shared.http.HTTPHeaderName
import com.erfangholami.androidsolidservices.shared.http.SolidHeaders
import com.erfangholami.androidsolidservices.shared.model.resource.RDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.RdfQuad
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import java.io.InputStream

internal class UnsupportedRdfContentTypeException(
    val contentType: String,
    val uri: String,
) : RuntimeException(
    "Cannot parse RDF resource $uri served as '$contentType' — only " +
            "${HTTPAcceptType.JSON_LD} and ${HTTPAcceptType.N_TRIPLES} are supported.",
)

internal object SolidResourceParser {

    fun <T> parse(response: SolidRawResponse, clazz: Class<T>): T {
        val contentType =
            response.headers[HTTPHeaderName.CONTENT_TYPE] ?: HTTPAcceptType.OCTET_STREAM
        return when {
            SolidContainer::class.java.isAssignableFrom(clazz) -> parseContainer(
                response,
                clazz,
                contentType
            )

            RDFResource::class.java.isAssignableFrom(clazz) -> parseRdf(
                response,
                clazz,
                contentType
            )

            else -> parseNonRdf(response, clazz, contentType)
        }
    }

    private fun <T> parseContainer(
        response: SolidRawResponse,
        clazz: Class<T>,
        contentType: String
    ): T {
        val quads = rdfQuads(response, contentType, i18nDirection = false)
        return clazz
            .getConstructor(
                String::class.java,
                String::class.java,
                List::class.java,
                SolidHeaders::class.java
            )
            .newInstance(
                response.uri.toString(),
                contentType,
                quads,
                SolidHeaders(response.headers.toMultimap()),
            )
    }

    private fun <T> parseRdf(response: SolidRawResponse, clazz: Class<T>, contentType: String): T {
        val quads = rdfQuads(response, contentType, i18nDirection = true)
        return clazz
            .getConstructor(
                String::class.java,
                String::class.java,
                List::class.java,
                SolidHeaders::class.java
            )
            .newInstance(
                response.uri.toString(),
                contentType,
                quads,
                SolidHeaders(response.headers.toMultimap()),
            )
    }

    private fun rdfQuads(
        response: SolidRawResponse,
        contentType: String,
        i18nDirection: Boolean,
    ): List<RdfQuad> {
        val ct = contentType.substringBefore(';').trim().lowercase()
        return when (ct) {
            HTTPAcceptType.N_TRIPLES, HTTPAcceptType.N_QUADS ->
                NTriples.parse(String(response.bodyBytes, Charsets.UTF_8), response.uri)

            HTTPAcceptType.TURTLE,
            HTTPAcceptType.N3,
            HTTPAcceptType.TRIG,
            HTTPAcceptType.RDF_XML,
            HTTPAcceptType.JSON_RDF ->
                throw UnsupportedRdfContentTypeException(ct, response.uri.toString())

            else -> {
                InruptAcrJson.parseOrNull(
                    String(response.bodyBytes, Charsets.UTF_8), response.uri,
                )?.let { return it }

                RDFResource.parseJsonLd(
                    String(response.bodyBytes, Charsets.UTF_8),
                    baseUri = response.uri.toString(),
                    i18nDirection = i18nDirection,
                )
            }
        }
    }

    private fun <T> parseNonRdf(
        response: SolidRawResponse,
        clazz: Class<T>,
        contentType: String
    ): T {
        val headers = SolidHeaders(response.headers.toMultimap())
        val body = response.bodyBytes.inputStream()
        return try {
            clazz
                .getConstructor(
                    String::class.java,
                    String::class.java,
                    InputStream::class.java,
                    SolidHeaders::class.java
                )
                .newInstance(response.uri.toString(), contentType, body, headers)
        } catch (_: NoSuchMethodException) {
            try {
                clazz
                    .getConstructor(
                        String::class.java,
                        String::class.java,
                        SolidHeaders::class.java,
                        InputStream::class.java
                    )
                    .newInstance(response.uri.toString(), contentType, headers, body)
            } catch (_: NoSuchMethodException) {
                clazz
                    .getConstructor(String::class.java, String::class.java, InputStream::class.java)
                    .newInstance(response.uri.toString(), contentType, body)
            }
        }
    }
}
